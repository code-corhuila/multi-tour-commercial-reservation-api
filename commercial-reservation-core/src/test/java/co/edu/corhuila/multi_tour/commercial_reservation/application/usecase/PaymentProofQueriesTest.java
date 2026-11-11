package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ListProofsQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSource;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04, T-05 and T-07 of HU-COM-010, the core part: RN-06, RN-07, RN-16; AC-10.6, AC-10.8, AC-10.14, AC-10.17, AC-10.19 and
// AC-09.16 of HU-COM-009. The mark is derived from the clock at read time and is never stored. The internal route for job 2 and
// the roles of the route table are adapter work.
class PaymentProofQueriesTest {

    private static final UUID TENANT = new UUID(10, 0xc1), OTHER_TENANT = new UUID(10, 0xc2), ADMIN = new UUID(10, 0xc3),
            CUSTOMER = new UUID(10, 0xc4), OTHER_CUSTOMER = new UUID(10, 0xc5), SERVICE = new UUID(10, 0xc6), DEPARTURE = new UUID(10, 0xc7),
            RESERVATION = new UUID(10, 0xc8), OLD = new UUID(10, 0xd1), FRESH = new UUID(10, 0xd2), REJECTED = new UUID(10, 0xd3);
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Reservation reservation;
    private final PaymentProofReader reader = new PaymentProofReader() {
        @Override
        public ProofSourcePage find(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore, int page, int limit) {
            List<ProofSource> all = new ArrayList<>();
            for (Reservation each : fakes.reservations.values()) {
                if (!each.tenantId().equals(tenantId)) continue;
                each.attempts().stream().filter(a -> a.validationStatus() == status)
                        .filter(a -> registeredAtOrBefore == null || !a.registeredAt().isAfter(registeredAtOrBefore))
                        .forEach(a -> all.add(new ProofSource(each, a)));
            }
            all.sort(Comparator.comparing((ProofSource s) -> s.attempt().registeredAt()));
            int from = Math.min((page - 1) * limit, all.size());
            return new ProofSourcePage(all.subList(from, Math.min(from + limit, all.size())), all.size());
        }

        @Override
        public long countAtOrBefore(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore) {
            return find(tenantId, status, registeredAtOrBefore, 1, 1_000).total();
        }
    };
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ListPaymentProofsUseCase list = new ListPaymentProofsUseCase(reader, clock);
    private final PaymentProofQueriesUseCase queries = new PaymentProofQueriesUseCase(fakes, clock);
    private final ReadPaymentProofFileUseCase file = new ReadPaymentProofFileUseCase(fakes, fakes);

    PaymentProofQueriesTest() {
        // 300.000 pay in full: a proof 24 hours old, one 23 hours 59 minutes old and one rejected.
        reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(3)));
        reservation.registerTransfer(OLD, new Cents(50_000), null, ADMIN, NOW.minus(Duration.ofHours(24)));
        reservation.registerTransfer(FRESH, new Cents(60_000), null, ADMIN, NOW.minus(Duration.ofHours(23)).minusSeconds(3_540));
        reservation.registerTransfer(REJECTED, new Cents(70_000), null, ADMIN, NOW.minus(Duration.ofHours(30)));
        reservation.reject(REJECTED, "illegible", ADMIN, NOW.minus(Duration.ofHours(29)));
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        fakes.proofs.put(OLD, ProofFile.of(PNG));
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, ADMIN, role);
    }

    @Test
    void aProofOf24HoursIsMarkedWithoutChangingStateAndOneOf23HoursFiftyNineIsNot() {
        ProofPage page = list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 1, 20));

        assertThat(page.items()).extracting(ProofView::attemptId).containsExactly(OLD, FRESH);
        assertThat(page.items().get(0).reviewOverdue()).isTrue();
        assertThat(page.items().get(0).status()).isEqualTo(ValidationStatus.IN_VALIDATION);
        assertThat(page.items().get(1).reviewOverdue()).isFalse();
        assertThat(page.overdueCount()).isEqualTo(1);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void theOverdueFilterKeepsOnlyTheMarkedOnesAndTheStatusFilterShowsTheRejected() {
        assertThat(list.list(new ListProofsQuery(actor(Role.MANAGER), null, true, 1, 20)).items())
                .extracting(ProofView::attemptId).containsExactly(OLD);
        assertThat(list.list(new ListProofsQuery(actor(Role.MANAGER), ValidationStatus.REJECTED, false, 1, 20)).items())
                .extracting(ProofView::attemptId).containsExactly(REJECTED);
    }

    @Test
    void theListIsPaginatedWithinOneAndAHundred() {
        ProofPage second = list.list(new ListProofsQuery(actor(Role.ANALYST), null, false, 2, 1));

        assertThat(second.items()).extracting(ProofView::attemptId).containsExactly(FRESH);
        assertThat(second.total()).isEqualTo(2);
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.ANALYST), null, false, 1, 101))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("limit"));
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.ANALYST), null, false, 0, 10))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("page"));
    }

    @Test
    void theRolesOfTheMatrixAreTheOnlyOnesAllowedToReadAndAnotherTenantSeesNothing() {
        for (Role reader : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThat(list.list(new ListProofsQuery(actor(reader), null, false, 1, 20)).items()).hasSize(2);
        }
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.FINAL_CUSTOMER), null, false, 1, 20)))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(list.list(new ListProofsQuery(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), null, false, 1, 20)).items())
                .isEmpty();
    }

    @Test
    void theOverdueListOfJob2TakesTheTenantFromThePathAndKeepsOnlyTheMarkedOnes() {
        ProofPage page = list.overdueOf(TENANT, 1, 20);

        assertThat(page.items()).extracting(ProofView::attemptId).containsExactly(OLD);
        assertThat(list.overdueOf(OTHER_TENANT, 1, 20).items()).isEmpty();
    }

    @Test
    void theReservationHistoryListsEveryAttemptIncludingTheRejectedOnes() {
        List<ProofView> history = queries.history(actor(Role.ACCOUNTANT), RESERVATION);

        assertThat(history).extracting(ProofView::attemptId).containsExactly(OLD, FRESH, REJECTED);
        ProofView rejected = history.get(2);
        assertThat(rejected.status()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("illegible");
        assertThat(rejected.declaredAmount()).isEqualTo(new Cents(70_000));
        assertThat(rejected.verifiedAmount()).isNull();
    }

    @Test
    void aCustomerReadsOnlyTheirOwnReservationAndAnotherTenantOrCustomerAnswersNotFound() {
        assertThat(queries.history(new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION)).hasSize(3);

        assertThatThrownBy(() -> queries.history(new PaymentActor(TENANT, OTHER_CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> queries.history(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void oneProofIsReadWithItsAmountsAndAnotherTenantsAnswersNotFound() {
        assertThat(queries.get(actor(Role.MANAGER), OLD).declaredAmount()).isEqualTo(new Cents(50_000));
        assertThat(queries.get(actor(Role.MANAGER), OLD).reviewOverdue()).isTrue();

        assertThatThrownBy(() -> queries.get(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), OLD))
                .isInstanceOf(PaymentProofNotFoundException.class);
    }

    @Test
    void theProofFileIsReadOnlyByTheHolderOrTheOwner() {
        assertThat(file.read(actor(Role.TENANT_ADMINISTRATOR), OLD).contentType()).isEqualTo("image/png");
        assertThat(file.read(new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, Set.of("VALIDATE_PAYMENT_PROOFS")), OLD)).isNotNull();
        assertThat(file.read(new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER), OLD)).isNotNull();

        for (PaymentActor refused : List.of(actor(Role.OPERATIONAL_COLLABORATOR), actor(Role.MANAGER), actor(Role.ACCOUNTANT),
                actor(Role.ANALYST), new PaymentActor(TENANT, OTHER_CUSTOMER, Role.FINAL_CUSTOMER))) {
            assertThatThrownBy(() -> file.read(refused, OLD)).isInstanceOf(PaymentAccessDeniedException.class);
        }
    }

    @Test
    void theFileOfAnotherTenantOrWithoutAFileAnswersNotFound() {
        assertThatThrownBy(() -> file.read(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), OLD))
                .isInstanceOf(PaymentProofNotFoundException.class);
        assertThatThrownBy(() -> file.read(actor(Role.TENANT_ADMINISTRATOR), FRESH)).isInstanceOf(PaymentProofNotFoundException.class);
    }
}
