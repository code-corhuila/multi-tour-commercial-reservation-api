package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-008 (api spec T-01, T-02, T-06; AC-08.1, AC-08.7, AC-08.9, AC-08.15): the Final Customer enters through the same
// creation and the same reads, and the customer is the one linked to the account of the token (RN-01, RN-08).
class OwnReservationsTest {

    private static final UUID TENANT = new UUID(8, 0xa1), OTHER_TENANT = new UUID(8, 0xa2), ACCOUNT = new UUID(8, 0xa3),
            OTHER_ACCOUNT = new UUID(8, 0xa4), CUSTOMER = new UUID(8, 0xa5), OTHER_CUSTOMER = new UUID(8, 0xa6),
            TOUR = new UUID(8, 0xa7), DEPARTURE = new UUID(8, 0xa8), RESERVATION = new UUID(8, 0xa9),
            OTHERS_RESERVATION = new UUID(8, 0xaa), UNLINKED_ACCOUNT = new UUID(8, 0xab);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final Map<UUID, Customer> customers = new HashMap<>();
    private final List<Reservation> stored = new ArrayList<>();
    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final DiscountRuleFakes rules = new DiscountRuleFakes();
    private final DepartureFakes departures = new DepartureFakes();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CustomerRepository customerRepository = new CustomerRepository() {
        @Override public void save(Customer customer) { customers.put(customer.customerId(), customer); }
        @Override public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) {
            return Optional.ofNullable(customers.get(customerId)).filter(found -> found.tenantId().equals(tenantId));
        }
    };
    private final CustomerByAccount byAccount = (tenantId, accountId) -> customers.values().stream()
            .filter(found -> found.tenantId().equals(tenantId) && accountId.equals(found.accountId())).findFirst();
    private final OwnReservationQueries ownQueries = new OwnReservationQueries() {
        @Override public ReservationPage page(UUID tenantId, UUID customerId, boolean includeArchived, int page, int limit) {
            var items = stored.stream().filter(r -> r.tenantId().equals(tenantId) && r.customerId().equals(customerId))
                    .map(r -> new ReservationListItem(r.reservationId(), "Lead Person", "Cocora", NOW.plusSeconds(86_400),
                            r.reservationStatus(), r.paymentStatus(), r.pendingBalance())).toList();
            return new ReservationPage(items, items.size());
        }
        @Override public Optional<Reservation> findByTenantCustomerAndId(UUID tenantId, UUID customerId, UUID id) {
            return stored.stream().filter(r -> r.reservationId().equals(id) && r.tenantId().equals(tenantId)
                    && r.customerId().equals(customerId)).findFirst();
        }
    };
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(rules);
    private final CreateReservationUseCase create = new CreateReservationUseCase(
            identifier -> Optional.of(new TenantRef(TENANT, true)), customerRepository, byAccount, catalog,
            new ReservableDeparture(departures, clock),
            new ReservationPricing(new QuoteDiscountBreakdownUseCase(evaluation), evaluation, (t, s, d) -> Optional.empty()),
            (tenant, service, type, method) -> true, stored::add, () -> RESERVATION, clock);

    OwnReservationsTest() {
        customers.put(CUSTOMER, lead(CUSTOMER, "CC1001").linkedTo(ACCOUNT));
        customers.put(OTHER_CUSTOMER, lead(OTHER_CUSTOMER, "CC1002").linkedTo(OTHER_ACCOUNT));
        catalog.save(CatalogService.register(TOUR, TENANT, CatalogServiceType.TOUR, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, false, false), NOW));
        departures.plant(new DepartureState(DEPARTURE, TENANT, TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        stored.add(reservation(RESERVATION, CUSTOMER));
        stored.add(reservation(OTHERS_RESERVATION, OTHER_CUSTOMER));
    }

    private static Customer lead(UUID id, String document) {
        return Customer.registerLead(id, TENANT, document, "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo("lead@example.com", "3000000000"), true, NOW);
    }

    private static Reservation reservation(UUID id, UUID customer) {
        return Reservation.create(id, TENANT, customer, DEPARTURE, ACCOUNT, 2, new ReservedService(TOUR, new Cents(100_000), null),
                new Cents(200_000), new Cents(200_000), List.of(), PaymentType.DEPOSIT, PaymentMethod.CASH, NOW);
    }

    private static CreateReservationCommand own(UUID account, UUID customer, int people) {
        return new CreateReservationCommand(TENANT, account, customer, TOUR, DEPARTURE, people, "DEPOSIT", "CASH", List.of(), account);
    }

    @Test
    void anAccountIsLinkedToACustomerWithoutChangingTheRest() {
        Customer plain = lead(new UUID(8, 1), "CC1003");
        Customer linked = plain.linkedTo(ACCOUNT);

        assertThat(plain.accountId()).isNull();
        assertThat(linked.accountId()).isEqualTo(ACCOUNT);
        assertThat(linked.customerId()).isEqualTo(plain.customerId());
        assertThat(linked.documentNumber()).isEqualTo(plain.documentNumber());
        assertThat(linked.createdAt()).isEqualTo(plain.createdAt());
    }

    @Test
    void aFinalCustomerCreatesAPendingPaymentReservationForTheirOwnCustomer() {
        stored.clear();

        Reservation created = create.create(own(ACCOUNT, null, 3));

        assertThat(created.customerId()).isEqualTo(CUSTOMER);
        assertThat(created.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(created.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(created.createdBy()).isEqualTo(ACCOUNT);
        assertThat(stored).containsExactly(created);
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isZero();
    }

    @Test
    void aCustomerInTheCommandOfAFinalCustomerIsRejectedNamingTheField() {
        stored.clear();

        assertThatThrownBy(() -> create.create(own(ACCOUNT, OTHER_CUSTOMER, 3)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("customerId"));
        assertThat(stored).isEmpty();
    }

    @Test
    void anAccountWithoutACustomerRecordNamesTheHolderAndStoresNothing() {
        stored.clear();

        assertThatThrownBy(() -> create.create(own(UNLINKED_ACCOUNT, null, 3)))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields()).containsExactly("holder"));
        assertThat(stored).isEmpty();
    }

    @Test
    void aFinalCustomerListsOnlyTheirOwnReservations() {
        var list = new ListOwnReservationsUseCase(byAccount, ownQueries);

        assertThat(list.list(TENANT, ACCOUNT, false, 1, 20).items()).extracting(ReservationListItem::reservationId)
                .containsExactly(RESERVATION);
        assertThat(list.list(TENANT, UNLINKED_ACCOUNT, false, 1, 20).items()).isEmpty();
        assertThat(list.list(OTHER_TENANT, ACCOUNT, false, 1, 20).items()).isEmpty();
    }

    @Test
    void aFinalCustomerReadsTheirOwnAndAnotherCustomersReservationLooksMissing() {
        var get = new GetOwnReservationUseCase(byAccount, ownQueries);

        assertThat(get.get(TENANT, ACCOUNT, RESERVATION).reservationId()).isEqualTo(RESERVATION);
        assertThatThrownBy(() -> get.get(TENANT, ACCOUNT, OTHERS_RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> get.get(TENANT, UNLINKED_ACCOUNT, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> get.get(OTHER_TENANT, ACCOUNT, RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
    }
}
