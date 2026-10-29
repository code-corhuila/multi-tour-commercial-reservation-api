package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-05 of HU-COM-007, the core part: RN-01, RN-05 to RN-07, RN-14, RN-17; AC-07.1, AC-07.2, AC-07.8, AC-07.9, AC-07.21.
// The group, the tenant status, the transaction and the outbox wait for their own tasks.
class CreateReservationTest {

    private static final UUID TENANT = new UUID(7, 0xe1), OTHER_TENANT = new UUID(7, 0xe2), ACTOR = new UUID(7, 0xe3),
            CUSTOMER = new UUID(7, 0xe4), TOUR = new UUID(7, 0xe5), RISKY_TOUR = new UUID(7, 0xe6), DEPARTURE = new UUID(7, 0xe7),
            RESERVATION = new UUID(7, 0xe8), RISKY_DEPARTURE = new UUID(7, 0xe9);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    private final Map<UUID, Customer> customers = new HashMap<>();
    private final List<Reservation> stored = new ArrayList<>();
    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final DiscountRuleFakes rules = new DiscountRuleFakes();
    private final DepartureFakes departures = new DepartureFakes();
    private boolean optionsEnabled = true;

    private final CustomerRepository customerRepository = new CustomerRepository() {
        @Override public void save(Customer customer) { customers.put(customer.customerId(), customer); }
        @Override public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) {
            return Optional.ofNullable(customers.get(customerId)).filter(found -> found.tenantId().equals(tenantId));
        }
    };
    private final ReservationRepository reservations = stored::add;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(rules);
    private final CreateReservationUseCase create = new CreateReservationUseCase(customerRepository, catalog,
            new ReservableDeparture(departures, clock),
            new ReservationPricing(new QuoteDiscountBreakdownUseCase(evaluation), evaluation, (t, s, d) -> Optional.of(new Cents(20_000))),
            (tenant, service, type, method) -> optionsEnabled, reservations, () -> RESERVATION, clock);

    CreateReservationTest() {
        customers.put(CUSTOMER, Customer.registerLead(CUSTOMER, TENANT, "CC12345", "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo("lead@example.com", "3000000000"), true, CREATED));
        catalog.save(CatalogService.register(TOUR, TENANT, CatalogServiceType.TOUR, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, true, false), CREATED));
        catalog.save(CatalogService.register(RISKY_TOUR, TENANT, CatalogServiceType.TOUR, "Nevado",
                CommercialTerms.of(150_000L, 40_000L, 20, false, true), CREATED));
        departures.plant(new DepartureState(DEPARTURE, TENANT, TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 7));
        departures.plant(new DepartureState(RISKY_DEPARTURE, TENANT, RISKY_TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        rules.save(DiscountRule.create(new UUID(7, 1), TENANT, "Early", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1,
                DiscountBase.ORIGINAL_VALUE, 1000, null, ServiceCoverage.all(), null, CREATED));
    }

    private static CreateReservationCommand command(UUID customer, UUID tour, UUID departure, int people, String type, String method) {
        return new CreateReservationCommand(TENANT, ACTOR, customer, tour, departure, people, type, method);
    }

    private CreateReservationCommand valid() {
        return command(CUSTOMER, TOUR, DEPARTURE, 3, "DEPOSIT", "TRANSFER");
    }

    @Test
    void aCompleteCreationStoresAPendingPaymentReservationWithItsValueAndNoSeatHeld() {
        Reservation reservation = create.create(valid());

        assertThat(stored).containsExactly(reservation);
        assertThat(reservation.reservationId()).isEqualTo(RESERVATION);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(reservation.paymentStatus()).isEqualTo(PaymentStatus.NO_PAYMENT);
        assertThat(reservation.projectedValue()).isEqualTo(new Cents(360_000));
        assertThat(reservation.finalValue()).isEqualTo(new Cents(324_000));
        assertThat(reservation.pendingBalance()).isEqualTo(reservation.finalValue());
        assertThat(reservation.fixedDiscounts()).hasSize(1);
        assertThat(reservation.expectedPaymentType()).isEqualTo(PaymentType.DEPOSIT);
        assertThat(reservation.expectedPaymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(reservation.createdBy()).isEqualTo(ACTOR);
        assertThat(reservation.createdAt()).isEqualTo(NOW);
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(7);
    }

    @Test
    void aMissingMandatoryDatumIsNamedAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(command(null, null, null, 0, null, null)))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields())
                        .containsExactly("customerId", "salidaId", "serviceId", "people", "paymentType", "paymentMethod"));
        assertThat(stored).isEmpty();
    }

    @Test
    void anUnknownPaymentTypeOrMediumIsRejectedAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(command(CUSTOMER, TOUR, DEPARTURE, 1, "CRYPTO", "CASH")))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> create.create(command(CUSTOMER, TOUR, DEPARTURE, 1, "DEPOSIT", "BARTER")))
                .isInstanceOf(DomainException.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void aPaymentOptionNotEnabledForTheTenantIsRefusedAndNothingIsStored() {
        optionsEnabled = false;

        assertThatThrownBy(() -> create.create(valid())).isInstanceOf(BusinessRuleViolation.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void aCustomerOrATourOfAnotherTenantLooksMissing() {
        assertThatThrownBy(() -> create.create(new CreateReservationCommand(OTHER_TENANT, ACTOR, CUSTOMER, TOUR, DEPARTURE, 1, "DEPOSIT", "CASH")))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> create.create(command(CUSTOMER, new UUID(7, 0xf2), DEPARTURE, 1, "DEPOSIT", "CASH")))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThat(stored).isEmpty();
    }

    @Test
    void aDepartureThatIsNotReservableRefusesTheCreationAndLeavesHeldSeatsUntouched() {
        assertThatThrownBy(() -> create.create(command(CUSTOMER, TOUR, new UUID(7, 0xf3), 1, "DEPOSIT", "CASH")))
                .isInstanceOf(DepartureNotFoundException.class);
        assertThatThrownBy(() -> create.create(command(CUSTOMER, TOUR, RISKY_DEPARTURE, 1, "DEPOSIT", "CASH")))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> create.create(command(CUSTOMER, TOUR, DEPARTURE, 4, "DEPOSIT", "CASH")))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("3 seats");
        assertThat(stored).isEmpty();
        assertThat(departures.rows.get(DEPARTURE).heldSeats()).isEqualTo(7);
        assertThat(departures.journal).isEmpty();
    }

    @Test
    void aLeadCustomerWithoutTheDataTheTourRequiresRefusesTheCreationNamingTheFields() {
        assertThatThrownBy(() -> create.create(command(CUSTOMER, RISKY_TOUR, RISKY_DEPARTURE, 1, "DEPOSIT", "CASH")))
                .isInstanceOfSatisfying(IncompleteLeadCustomerException.class, e -> assertThat(e.fields())
                        .containsExactly("bloodType", "emergencyContact", "mobilityRestrictions", "informedConsentSigned"));
        assertThat(stored).isEmpty();
    }

    @Test
    void aLeadCustomerWithTheDataTheTourRequiresIsAccepted() {
        HighRiskData data = new HighRiskData("O+", "Ana 3001112233", "Ninguna", true);
        customers.put(CUSTOMER, Customer.restore(CUSTOMER, TENANT, customers.get(CUSTOMER).documentNumber(), "Lead Person",
                BirthDateOrAge.ofAge(30), new ContactInfo("lead@example.com", "3000000000"), CREATED, CREATED, data));

        assertThat(create.create(command(CUSTOMER, RISKY_TOUR, RISKY_DEPARTURE, 1, "FULL_PAYMENT", "CASH")).finalValue())
                .isEqualTo(new Cents(135_000));
        assertThat(stored).hasSize(1);
    }
}
