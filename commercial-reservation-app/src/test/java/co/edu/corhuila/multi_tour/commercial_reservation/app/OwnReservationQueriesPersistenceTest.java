package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-008 AC-08.7 and AC-08.15 on the real schema: the Final Customer reads only the reservations of their own customer
// record. The customer is a condition of each statement next to the tenant, so a reservation of another customer is the same
// empty answer as a missing one.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OwnReservationQueriesPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcReservationQueries queries;
    private UUID other;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                new JdbcReservationCreatedOutbox(fixture.jdbc));
        queries = new JdbcReservationQueries(fixture.jdbc, new JdbcReservationReader(fixture.jdbc, fixture.cipher));
        new JdbcDepartureStateRepository(fixture.jdbc).save(new DepartureState(fixture.salida, fixture.tenant, fixture.service,
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(3, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        other = UUID.randomUUID();
        new JdbcCustomerRepository(fixture.jdbc, fixture.cipher).save(Customer.restore(other, fixture.tenant,
                DocumentNumber.of("DOC-FICT-0099"), "Otra Persona Ficticia", BirthDateOrAge.ofAge(33),
                new ContactInfo("otra@example.invalid", "+000-555-0111"), ReservationPersistenceFixture.NOW,
                ReservationPersistenceFixture.NOW, null));
    }

    private UUID savedFor(UUID customer) {
        UUID id = UUID.randomUUID();
        Reservation base = fixture.reservation(id, true);
        repository.save(Reservation.create(id, base.tenantId(), customer, base.departureId(), base.createdBy(), base.people(),
                base.service(), base.projectedValue(), base.finalValue(), base.fixedDiscounts(), base.expectedPaymentType(),
                base.expectedPaymentMethod(), base.createdAt()));
        return id;
    }

    @Test
    void theListHoldsOnlyTheReservationsOfTheCustomerAndCountsOnlyThose() {
        UUID mine = savedFor(fixture.customer);
        savedFor(other);
        savedFor(other);

        ReservationPage page = queries.page(fixture.tenant, fixture.customer, false, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(ReservationListItem::reservationId).containsExactly(mine);
        assertThat(page.items().get(0).customerName()).isEqualTo("Lead Ficticio");
        assertThat(queries.page(fixture.tenant, other, false, 1, 1).items()).hasSize(1);
        assertThat(queries.page(fixture.tenant, other, false, 1, 1).total()).isEqualTo(2);
    }

    @Test
    void theArchivedStatesAreLeftOutOfTheOwnListUnlessAskedFor() {
        UUID open = savedFor(fixture.customer);
        UUID cancelled = savedFor(fixture.customer);
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CANCELLED' WHERE reservation_id = ?",
                cancelled);

        assertThat(queries.page(fixture.tenant, fixture.customer, false, 1, 20).items())
                .extracting(ReservationListItem::reservationId).containsExactly(open);
        assertThat(queries.page(fixture.tenant, fixture.customer, true, 1, 20).total()).isEqualTo(2);
    }

    @Test
    void theDetailOfAnotherCustomerOrAnotherTenantIsEmptyLikeAMissingOne() {
        UUID mine = savedFor(fixture.customer);
        UUID theirs = savedFor(other);

        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, fixture.customer, mine)).isPresent();
        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, fixture.customer, theirs)).isEmpty();
        assertThat(queries.findByTenantCustomerAndId(UUID.randomUUID(), fixture.customer, mine)).isEmpty();
        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, fixture.customer, UUID.randomUUID())).isEmpty();
    }

    // A second REAL tenant (its own customer, tour and departure) with the same account linked to its customer: the own routes of the
    // first tenant never see its reservations, with the page, with the detail, or with the customer or the id of the other tenant.
    @Test
    void aSecondRealTenantWithTheSameAccountIsNeverSeenByTheOwnRoutesOfTheFirst() {
        var foreign = new ReservationPersistenceFixture();
        var foreignRepository = new JdbcReservationRepository(foreign.jdbc, foreign.cipher, foreign.transactions,
                new JdbcReservationCreatedOutbox(foreign.jdbc));
        new JdbcDepartureStateRepository(foreign.jdbc).save(new DepartureState(foreign.salida, foreign.tenant, foreign.service,
                Instant.now().truncatedTo(ChronoUnit.MICROS).plus(3, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 10, 0));
        UUID foreignId = UUID.randomUUID();
        foreignRepository.save(foreign.reservation(foreignId, true));
        UUID mine = savedFor(fixture.customer);
        UUID account = UUID.randomUUID();
        var link = new JdbcCustomerAccountLink(fixture.jdbc);
        assertThat(link.linkIfUnlinked(fixture.tenant, fixture.customer, account)).isTrue();
        assertThat(link.linkIfUnlinked(foreign.tenant, foreign.customer, account)).isTrue();
        // Each fixture seals with its own key, so a tenant is read back only with the queries built on its own cipher.
        var customers = new JdbcCustomerRepository(fixture.jdbc, fixture.cipher);
        var foreignCustomers = new JdbcCustomerRepository(foreign.jdbc, foreign.cipher);
        var foreignQueries = new JdbcReservationQueries(foreign.jdbc, new JdbcReservationReader(foreign.jdbc, foreign.cipher));
        assertThat(customers.findByTenantAndAccount(fixture.tenant, account).orElseThrow().customerId()).isEqualTo(fixture.customer);
        assertThat(foreignCustomers.findByTenantAndAccount(foreign.tenant, account).orElseThrow().customerId()).isEqualTo(foreign.customer);

        assertThat(queries.page(fixture.tenant, fixture.customer, true, 1, 20).items())
                .extracting(ReservationListItem::reservationId).containsExactly(mine);
        assertThat(queries.page(fixture.tenant, fixture.customer, true, 1, 20).total()).isEqualTo(1);
        assertThat(foreignQueries.page(foreign.tenant, foreign.customer, true, 1, 20).items())
                .extracting(ReservationListItem::reservationId).containsExactly(foreignId);
        assertThat(queries.page(fixture.tenant, foreign.customer, true, 1, 20).items()).isEmpty();
        assertThat(queries.page(foreign.tenant, fixture.customer, true, 1, 20).items()).isEmpty();
        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, fixture.customer, foreignId)).isEmpty();
        assertThat(queries.findByTenantCustomerAndId(fixture.tenant, foreign.customer, foreignId)).isEmpty();
        assertThat(queries.findByTenantCustomerAndId(foreign.tenant, foreign.customer, mine)).isEmpty();
        assertThat(foreignQueries.findByTenantCustomerAndId(foreign.tenant, foreign.customer, foreignId)).isPresent();
    }

    @Test
    void aCustomerWithNoReservationsHasAnEmptyPageAndTotalZero() {
        savedFor(other);

        ReservationPage page = queries.page(fixture.tenant, fixture.customer, true, 1, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
