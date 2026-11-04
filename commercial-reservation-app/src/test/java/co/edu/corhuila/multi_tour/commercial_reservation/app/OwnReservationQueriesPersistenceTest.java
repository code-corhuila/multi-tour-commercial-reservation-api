package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

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
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CANCELLED', "
                + "cancellation_cause = 'CUSTOMER_REQUEST', cancelled_at = now() WHERE reservation_id = ?",
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
