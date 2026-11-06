package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-009 and HU-COM-010 over the real schema (api tasks T-03, T-04): the attempts, the decision, the seat and the events of a
// reservation are stored and read back by the same repository that created it. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentReservationPersistenceTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcPaymentEventOutbox events;
    private JdbcPaymentTransaction transaction;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                new JdbcReservationCreatedOutbox(fixture.jdbc));
        events = new JdbcPaymentEventOutbox(fixture.jdbc);
        transaction = new JdbcPaymentTransaction(fixture.transactions);
    }

    private Reservation stored() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        return repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
    }

    private void persist(Reservation reservation) {
        transaction.inTransaction(() -> {
            repository.save(reservation);
            reservation.pullEvents().forEach(events::append);
            return null;
        });
    }

    @Test
    void aStoredReservationIsReadBackByIdWithItsCreationValuesAndNoAttempt() {
        Reservation read = stored();

        assertThat(read.finalValue()).isEqualTo(new Cents(324_000));
        assertThat(read.pendingBalance()).isEqualTo(new Cents(324_000));
        assertThat(read.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(read.attempts()).isEmpty();
        assertThat(read.seatHeld()).isFalse();
        assertThat(repository.findByTenantAndId(UUID.randomUUID(), read.reservationId())).isEmpty();
    }

    @Test
    void aTransferInValidationAndItsSeatAreStoredAndFoundByTheAttempt() {
        Reservation reservation = stored();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation);

        Reservation found = repository.findByTenantAndAttempt(fixture.tenant, attempt).orElseThrow();

        assertThat(found.reservationId()).isEqualTo(reservation.reservationId());
        assertThat(found.attempts()).singleElement().satisfies(row -> {
            assertThat(row.attemptId()).isEqualTo(attempt);
            assertThat(row.validationStatus()).isEqualTo(ValidationStatus.IN_VALIDATION);
            assertThat(row.amount()).isEqualTo(new Cents(100_000));
            assertThat(row.registeredBy()).isEqualTo(fixture.actor);
        });
        assertThat(found.seatHeld()).isTrue();
        assertThat(fixture.count("capacity_allocation", reservation.reservationId())).isEqualTo(1);
        assertThat(repository.findByTenantAndAttempt(UUID.randomUUID(), attempt)).isEmpty();
        persist(found);
        assertThat(fixture.count("capacity_allocation", reservation.reservationId())).isEqualTo(1);
    }

    @Test
    void anApprovalIsStoredWithTheSituationTheDecisionAndItsEvents() {
        Reservation reservation = stored();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();

        loaded.approve(attempt, new Cents(100_000), null, MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);

        Reservation read = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(read.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(read.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(read.pendingBalance()).isEqualTo(new Cents(224_000));
        assertThat(read.attempts().get(0).validationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(read.attempts().get(0).appliedAmount()).isEqualTo(new Cents(100_000));
        assertThat(read.attempts().get(0).decidedBy()).isEqualTo(fixture.actor);
        assertThat(types(reservation)).contains("ReservationCreated", "PaymentEvidenceRegistered", "CapacityAllocated", "PaymentValidated",
                "ReservationConfirmed");
        persist(read);
    }

    @Test
    void cashIsStoredWithoutValidationStatusAndARejectionKeepsItsReason() {
        Reservation reservation = stored();
        reservation.registerCash(UUID.randomUUID(), new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        UUID proof = UUID.randomUUID();
        reservation.registerTransfer(proof, new Cents(5_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();

        loaded.reject(proof, "The proof is illegible", fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);

        Reservation read = repository.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow();
        assertThat(read.attempts()).hasSize(2);
        assertThat(read.attempts().stream().filter(row -> row.validationStatus() == null)).hasSize(1);
        var rejected = read.attempts().stream().filter(row -> row.attemptId().equals(proof)).findFirst().orElseThrow();
        assertThat(rejected.validationStatus()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("The proof is illegible");
        assertThat(types(reservation)).contains("PaymentRejected", "PaymentValidated");
    }

    @Test
    void theReadsOfTheReservationRoutesCarryTheAttemptsOfThePayments() {
        Reservation reservation = stored();
        reservation.registerTransfer(UUID.randomUUID(), new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(reservation);
        var queries = new JdbcReservationQueries(fixture.jdbc, new JdbcReservationReader(fixture.jdbc, fixture.cipher));

        assertThat(queries.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow().attempts()).hasSize(1);
        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, fixture.customer, reservation.reservationId()).orElseThrow().attempts()).hasSize(1);
    }

    @Test
    void aFailureInsideTheTransactionLeavesNothingBehind() {
        Reservation reservation = stored();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);

        assertThatThrownBy(() -> transaction.inTransaction(() -> {
            repository.save(reservation);
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.findByTenantAndAttempt(fixture.tenant, attempt)).isEmpty();
    }

    private List<String> types(Reservation reservation) {
        return fixture.jdbc.queryForList("SELECT event_type FROM commercial_reservation.outbox_event WHERE reservation_id = ?",
                String.class, reservation.reservationId());
    }

    // PDR CA-016: the attempts of tenant A and of tenant B share the table, and a tenant never reaches the other's reservation or attempt.
    @Test
    void theAttemptsOfTenantBNeverAnswerTenantA() {
        var other = new ReservationPersistenceFixture();
        var otherRepository = new JdbcReservationRepository(other.jdbc, other.cipher, other.transactions, new JdbcReservationCreatedOutbox(other.jdbc));
        Reservation mine = stored();
        UUID mineAttempt = UUID.randomUUID(), theirAttempt = UUID.randomUUID();
        mine.registerTransfer(mineAttempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(mine);
        UUID theirId = UUID.randomUUID();
        otherRepository.save(other.reservation(theirId, true));
        Reservation theirs = otherRepository.findByTenantAndId(other.tenant, theirId).orElseThrow();
        theirs.registerTransfer(theirAttempt, new Cents(100_000), MINIMUM, other.actor, ReservationPersistenceFixture.NOW);
        otherRepository.save(theirs);

        assertThat(repository.findByTenantAndAttempt(fixture.tenant, mineAttempt)).isPresent();
        assertThat(otherRepository.findByTenantAndAttempt(other.tenant, theirAttempt)).isPresent();
        assertThat(repository.findByTenantAndAttempt(fixture.tenant, theirAttempt)).isEmpty();
        assertThat(otherRepository.findByTenantAndAttempt(other.tenant, mineAttempt)).isEmpty();
        assertThat(repository.findByTenantAndId(fixture.tenant, theirId)).isEmpty();
        assertThat(repository.findByTenantAndId(fixture.tenant, mine.reservationId()).orElseThrow().attempts()).singleElement()
                .satisfies(row -> assertThat(row.attemptId()).isEqualTo(mineAttempt));
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.payment_attempt WHERE tenant_id = ? AND reservation_id = ?",
                Integer.class, fixture.tenant, theirId)).isZero();
    }

    // Finding of the deep audit of HU-COM-009: the upsert of an attempt was keyed on the attempt id alone, so a tenant that saved an
    // attempt with the id of another tenant rewrote the row of that tenant. The conflict update now needs the same tenant.
    @Test
    void anotherTenantCannotOverwriteTheAttemptOfThisOneByUsingItsId() {
        Reservation mine = stored();
        UUID attempt = UUID.randomUUID();
        mine.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        mine.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(mine);
        var otherFixture = new ReservationPersistenceFixture();
        var otherRepository = new JdbcReservationRepository(otherFixture.jdbc, otherFixture.cipher, otherFixture.transactions,
                new JdbcReservationCreatedOutbox(otherFixture.jdbc));
        UUID otherId = UUID.randomUUID();
        otherRepository.save(otherFixture.reservation(otherId, true));
        Reservation theirs = otherRepository.findByTenantAndId(otherFixture.tenant, otherId).orElseThrow();
        theirs.registerTransfer(attempt, new Cents(100_000), MINIMUM, otherFixture.actor, ReservationPersistenceFixture.NOW);
        theirs.approve(attempt, new Cents(100_000), null, MINIMUM, otherFixture.actor, ReservationPersistenceFixture.NOW);

        otherRepository.save(theirs);

        var row = fixture.jdbc.queryForMap("SELECT tenant_id, reservation_id, validation_status, decided_by, applied_amount_cents "
                + "FROM commercial_reservation.payment_attempt WHERE payment_attempt_id = ?", attempt);
        assertThat(row.get("tenant_id")).isEqualTo(fixture.tenant);
        assertThat(row.get("reservation_id")).isEqualTo(mine.reservationId());
        assertThat(row.get("validation_status")).isEqualTo(ValidationStatus.IN_VALIDATION.name());
        assertThat(row.get("decided_by")).isNull();
        assertThat(row.get("applied_amount_cents")).isNull();
    }
}
