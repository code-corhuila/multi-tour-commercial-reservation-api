package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-007 AC-07.16 and AC-07.17 on the real schema: the paged list and the detail of the reservations of a tenant. The tenant
// is a condition of every query; names and the start of the departure come from the joined rows. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationQueriesPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcReservationQueries queries;
    private Instant departureStart;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                new JdbcReservationCreatedOutbox(fixture.jdbc));
        queries = new JdbcReservationQueries(fixture.jdbc, new JdbcReservationReader(fixture.jdbc, fixture.cipher));
        departureStart = Instant.now().truncatedTo(ChronoUnit.MICROS).plus(3, ChronoUnit.DAYS);
        new JdbcDepartureStateRepository(fixture.jdbc).save(new DepartureState(fixture.salida, fixture.tenant, fixture.service,
                departureStart, DepartureStatus.SCHEDULED, 10, 0));
    }

    private UUID saved(long minutesAgo, String reservationStatus) {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET created_at = ?, reservation_status = ? "
                + "WHERE reservation_id = ?", OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutesAgo), reservationStatus, id);
        return id;
    }

    @Test
    void theListIsNewestFirstAndEachRowCarriesTheNamesTheDepartureStartAndTheBalance() {
        UUID older = saved(30, "PENDING_PAYMENT");
        UUID newer = saved(10, "CONFIRMED");

        ReservationPage page = queries.page(fixture.tenant, false, 1, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(ReservationListItem::reservationId).containsExactly(newer, older);
        ReservationListItem first = page.items().get(0);
        assertThat(first.customerName()).isEqualTo("Lead Ficticio");
        assertThat(first.tourName()).isEqualTo("Servicio Ficticio");
        assertThat(first.departureStartsAt()).isEqualTo(departureStart);
        assertThat(first.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(first.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(first.pendingBalance()).isEqualTo(new Cents(324_000));
    }

    @Test
    void thePagesKeepTheTotalAndDoNotRepeatRows() {
        for (int i = 1; i <= 5; i++) saved(i * 10L, "PENDING_PAYMENT");

        ReservationPage first = queries.page(fixture.tenant, false, 1, 2);
        ReservationPage second = queries.page(fixture.tenant, false, 2, 2);
        ReservationPage third = queries.page(fixture.tenant, false, 3, 2);
        ReservationPage beyond = queries.page(fixture.tenant, false, 4, 2);

        assertThat(first.total()).isEqualTo(5);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.total()).isEqualTo(5);
        List<UUID> all = new java.util.ArrayList<>();
        for (ReservationPage page : List.of(first, second, third)) page.items().forEach(row -> all.add(row.reservationId()));
        assertThat(all).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void theArchivedStatesAreLeftOutUnlessAskedFor() {
        UUID open = saved(30, "PENDING_PAYMENT");
        UUID completed = saved(20, "COMPLETED");
        UUID cancelled = saved(10, "CANCELLED");

        assertThat(queries.page(fixture.tenant, false, 1, 20).items()).extracting(ReservationListItem::reservationId)
                .containsExactly(open);
        ReservationPage all = queries.page(fixture.tenant, true, 1, 20);
        assertThat(all.total()).isEqualTo(3);
        assertThat(all.items()).extracting(ReservationListItem::reservationId).containsExactly(cancelled, completed, open);
    }

    @Test
    void anotherTenantSeesNothingOfTheTenantAndNeitherTheListNorTheDetailLeaks() {
        UUID id = saved(10, "PENDING_PAYMENT");
        UUID stranger = UUID.randomUUID();

        assertThat(queries.page(stranger, true, 1, 20).items()).isEmpty();
        assertThat(queries.page(stranger, true, 1, 20).total()).isZero();
        assertThat(queries.findByTenantAndId(stranger, id)).isEmpty();
        assertThat(queries.findByTenantAndId(fixture.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theDetailIsTheReservationAsCreatedPlusItsStoredStateAndSeat() {
        UUID id = saved(10, "PENDING_PAYMENT");
        Reservation created = fixture.reservation(id, true);
        fixture.jdbc.update("UPDATE commercial_reservation.reservation SET reservation_status = 'CONFIRMED', "
                + "payment_status = 'DEPOSIT_RECEIVED', pending_balance_cents = 200000 WHERE reservation_id = ?", id);
        fixture.jdbc.update("INSERT INTO commercial_reservation.capacity_allocation (capacity_allocation_id, tenant_id, "
                + "salida_id, reservation_id, party_size, allocation_kind, allocated_at) VALUES (?, ?, ?, ?, 3, 'RESERVATION', now())",
                UUID.randomUUID(), fixture.tenant, fixture.salida, id);

        Reservation found = queries.findByTenantAndId(fixture.tenant, id).orElseThrow();

        assertThat(found.reservationId()).isEqualTo(id);
        assertThat(found.finalValue()).isEqualTo(created.finalValue());
        assertThat(found.fixedDiscounts()).isEqualTo(created.fixedDiscounts());
        assertThat(found.reservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(found.paymentStatus()).isEqualTo(PaymentStatus.DEPOSIT_RECEIVED);
        assertThat(found.pendingBalance()).isEqualTo(new Cents(200_000));
        assertThat(found.seatHeld()).isTrue();
    }

    @Test
    void aReleasedAllocationIsNotAHeldSeatAndANewReservationHoldsNone() {
        UUID id = saved(10, "PENDING_PAYMENT");
        assertThat(queries.findByTenantAndId(fixture.tenant, id).orElseThrow().seatHeld()).isFalse();
        fixture.jdbc.update("INSERT INTO commercial_reservation.capacity_allocation (capacity_allocation_id, tenant_id, "
                + "salida_id, reservation_id, party_size, allocation_kind, allocated_at, released_at) "
                + "VALUES (?, ?, ?, ?, 3, 'RESERVATION', now(), now())", UUID.randomUUID(), fixture.tenant, fixture.salida, id);

        assertThat(queries.findByTenantAndId(fixture.tenant, id).orElseThrow().seatHeld()).isFalse();
    }

    private static boolean present(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }
}
