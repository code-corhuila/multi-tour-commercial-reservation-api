package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// HU-COM-007 reads and roles (api spec T-06, T-09; AC-07.7, AC-07.13, AC-07.16, AC-07.17): the three reads of the
// HTTP layer, with in-memory fakes. The queries port is implemented by the JDBC adapter, not here.
class ReservationReadsTest {

    private static final UUID TENANT = new UUID(8, 1), OTHER = new UUID(8, 2), ACTOR = new UUID(8, 3), TOUR = new UUID(8, 4),
            OTHER_TOUR = new UUID(8, 5), DEPARTURE = new UUID(8, 6), RESERVATION = new UUID(8, 7);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final DepartureFakes departures = new DepartureFakes();
    private final List<Reservation> stored = new ArrayList<>();
    private boolean lastIncludeArchived;
    private int lastPage, lastLimit;

    private final ReservationQueries queries = new ReservationQueries() {
        @Override public ReservationPage page(UUID tenantId, boolean includeArchived, int page, int limit) {
            lastIncludeArchived = includeArchived; lastPage = page; lastLimit = limit;
            var items = stored.stream().filter(r -> r.tenantId().equals(tenantId))
                    .map(r -> new ReservationListItem(r.reservationId(), "Lead Person", "Cocora", NOW.plusSeconds(86_400),
                            r.reservationStatus(), r.paymentStatus(), r.pendingBalance())).toList();
            return new ReservationPage(items, items.size());
        }
        @Override public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID id) {
            return stored.stream().filter(r -> r.reservationId().equals(id) && r.tenantId().equals(tenantId)).findFirst();
        }
    };

    ReservationReadsTest() {
        catalog.save(CatalogService.register(TOUR, TENANT, CatalogServiceType.TOUR, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        catalog.save(CatalogService.register(OTHER_TOUR, OTHER, CatalogServiceType.TOUR, "Nevado",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        stored.add(Reservation.create(RESERVATION, TENANT, new UUID(8, 8), DEPARTURE, ACTOR, 2,
                new ReservedService(TOUR, new Cents(100_000), null), new Cents(200_000), new Cents(200_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.CASH, NOW));
    }

    private static DepartureState departure(long id, Instant startsAt, DepartureStatus status, int capacity, int held) {
        return new DepartureState(new UUID(9, id), TENANT, TOUR, startsAt, status, capacity, held);
    }

    @Test
    void theDeparturesOfATourKeepTheFullOnesAndDropWhatIsNotOpen() {
        departures.save(departure(1, NOW.plusSeconds(200), DepartureStatus.SCHEDULED, 5, 5));
        departures.save(departure(2, NOW.plusSeconds(100), DepartureStatus.SCHEDULED, 5, 2));
        departures.save(departure(3, NOW.minusSeconds(10), DepartureStatus.SCHEDULED, 5, 0));
        departures.save(departure(4, NOW.plusSeconds(300), DepartureStatus.CANCELLED, 5, 0));

        var page = new ListReservationDeparturesUseCase(catalog, departures, clock).list(TENANT, TOUR, 1, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(d -> d.seats().available()).containsExactly(3, 0);
    }

    @Test
    void theDeparturesAreCutByPageAndLimit() {
        for (long i = 0; i < 3; i++) departures.save(departure(i, NOW.plusSeconds(100 + i), DepartureStatus.SCHEDULED, 5, 0));

        var page = new ListReservationDeparturesUseCase(catalog, departures, clock).list(TENANT, TOUR, 2, 2);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void aTourOfAnotherTenantOrUnknownLooksMissing() {
        var list = new ListReservationDeparturesUseCase(catalog, departures, clock);

        assertThatThrownBy(() -> list.list(TENANT, OTHER_TOUR, 1, 20)).isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> list.list(TENANT, new UUID(8, 99), 1, 20)).isInstanceOf(CatalogServiceNotFoundException.class);
    }

    @Test
    void theListHidesTheArchivedStatesUnlessAllIsAsked() {
        var list = new ListReservationsUseCase(queries);

        assertThat(list.list(TENANT, false, 1, 20).items()).hasSize(1);
        assertThat(lastIncludeArchived).isFalse();
        list.list(TENANT, true, 2, 5);
        assertThat(lastIncludeArchived).isTrue();
        assertThat(lastPage).isEqualTo(2);
        assertThat(lastLimit).isEqualTo(5);
        assertThat(list.list(OTHER, false, 1, 20).items()).isEmpty();
    }

    @Test
    void aReservationIsReadWithTheValuesFixedAtCreationAndOneOfAnotherTenantLooksMissing() {
        var get = new GetReservationUseCase(queries);

        Reservation found = get.get(TENANT, RESERVATION);

        assertThat(found.finalValue()).isEqualTo(new Cents(200_000));
        assertThat(found.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(found.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThatThrownBy(() -> get.get(OTHER, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> get.get(TENANT, new UUID(8, 98))).isInstanceOf(ReservationNotFoundException.class);
    }

    private static CustomerActor actor(Role role) {
        return new CustomerActor(TENANT, ACTOR, role);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MANAGER", "ACCOUNTANT", "ANALYST"})
    void managerAccountantAndAnalystCannotQuoteOrCreate(Role role) {
        assertThatThrownBy(() -> ReservationAuthorization.requireWrite(actor(role)))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"TENANT_ADMINISTRATOR", "OPERATIONAL_COLLABORATOR"})
    void administratorAndCollaboratorQuoteCreateAndRead(Role role) {
        ReservationAuthorization.requireWrite(actor(role));
        ReservationAuthorization.requireRead(actor(role));
        ReservationAuthorization.requireDepartures(actor(role));
    }

    @Test
    void theFinalCustomerQuotesCreatesAndReadsThroughTheSameDoor() {
        ReservationAuthorization.requireWrite(actor(Role.FINAL_CUSTOMER));
        ReservationAuthorization.requireRead(actor(Role.FINAL_CUSTOMER));
        ReservationAuthorization.requireDepartures(actor(Role.FINAL_CUSTOMER));
    }

    @Test
    void theReservationsAreReadByEveryRoleThatMayRead() {
        for (Role role : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) ReservationAuthorization.requireRead(actor(role));
        assertThatThrownBy(() -> ReservationAuthorization.requireRead(null)).isInstanceOf(ReservationAccessDeniedException.class);
    }

    @Test
    void theDeparturesAreReadByEveryRoleExceptTheAccountant() {
        for (Role role : List.of(Role.MANAGER, Role.ANALYST, Role.FINAL_CUSTOMER)) ReservationAuthorization.requireDepartures(actor(role));
        assertThatThrownBy(() -> ReservationAuthorization.requireDepartures(actor(Role.ACCOUNTANT)))
                .isInstanceOf(ReservationAccessDeniedException.class);
    }
}
