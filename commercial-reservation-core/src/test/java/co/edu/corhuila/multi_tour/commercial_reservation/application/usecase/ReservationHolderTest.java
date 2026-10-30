package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
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

// HU-COM-007 / HU-COM-005 inline holder (contract CreateReservationRequest.holder): the lead is registered and the reservation
// is created by one use case call, so the adapter's transaction covers both. RN-CLI-001, AC-05.7, AC-08.13.
class ReservationHolderTest {

    private static final UUID TENANT = new UUID(9, 1), ACTOR = new UUID(9, 2), TOUR = new UUID(9, 3), RISKY_TOUR = new UUID(9, 4),
            DEPARTURE = new UUID(9, 5), RISKY_DEPARTURE = new UUID(9, 6), RESERVATION = new UUID(9, 7), NEW_CUSTOMER = new UUID(9, 8),
            VERIFIED = new UUID(9, 9), UNVERIFIED = new UUID(9, 10), ACCOUNT = new UUID(9, 11);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final Map<UUID, Customer> customers = new HashMap<>();
    private final List<Reservation> stored = new ArrayList<>();
    private final List<CustomerRegistered> events = new ArrayList<>();
    private final List<String> lookedUp = new ArrayList<>();
    private final Map<String, CustomerByEmail.EmailMatch> matches = new HashMap<>();
    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final DepartureFakes departures = new DepartureFakes();
    private final java.util.ArrayDeque<UUID> ids = new java.util.ArrayDeque<>(List.of(NEW_CUSTOMER, RESERVATION));
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final EvaluateApplicableDiscountRulesUseCase evaluation = new EvaluateApplicableDiscountRulesUseCase(new DiscountRuleFakes());
    private final CustomerRepository repository = new CustomerRepository() {
        @Override public void save(Customer customer) { customers.put(customer.customerId(), customer); }
        @Override public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) {
            return Optional.ofNullable(customers.get(customerId)).filter(found -> found.tenantId().equals(tenantId));
        }
    };
    private final CreateReservationUseCase create = new CreateReservationUseCase(
            identifier -> Optional.of(new TenantRef(TENANT, true)), repository,
            (tenant, account) -> customers.values().stream().filter(found -> account.equals(found.accountId())).findFirst(),
            catalog, new ReservableDeparture(departures, clock),
            new ReservationPricing(new QuoteDiscountBreakdownUseCase(evaluation), evaluation, (t, s, d) -> Optional.of(new Cents(20_000))),
            (tenant, service, type, method) -> true, stored::add, () -> ids.isEmpty() ? RESERVATION : ids.pop(), clock,
            events::add, (tenant, email) -> {
                lookedUp.add(email);
                return Optional.ofNullable(matches.get(email));
            });

    ReservationHolderTest() {
        catalog.save(CatalogService.register(TOUR, TENANT, CatalogServiceType.TOUR, "Cocora",
                CommercialTerms.of(100_000L, 40_000L, 20, true, false), NOW));
        catalog.save(CatalogService.register(RISKY_TOUR, TENANT, CatalogServiceType.TOUR, "Nevado",
                CommercialTerms.of(150_000L, 40_000L, 20, false, true), NOW));
        departures.plant(new DepartureState(DEPARTURE, TENANT, TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 7));
        departures.plant(new DepartureState(RISKY_DEPARTURE, TENANT, RISKY_TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 0));
        customers.put(VERIFIED, Customer.restore(VERIFIED, TENANT, DocumentNumber.of("CC1"), "Verified Person", BirthDateOrAge.ofAge(40), new ContactInfo("known@example.invalid", null),
                NOW, NOW, null, ACCOUNT));
        customers.put(UNVERIFIED, Customer.registerLead(UNVERIFIED, TENANT, "CC2", "Unverified Person", BirthDateOrAge.ofAge(41),
                new ContactInfo("pending@example.invalid", null), true, NOW));
        matches.put("known@example.invalid", new CustomerByEmail.EmailMatch(VERIFIED, true));
        matches.put("pending@example.invalid", new CustomerByEmail.EmailMatch(UNVERIFIED, false));
    }

    private static RegisterLeadCustomer.Command holder(String email, boolean terms, HighRiskData risk) {
        return new RegisterLeadCustomer.Command(null, "cc 900", "Holder Person", null, 33, email, "3000000000", terms, risk);
    }

    private static CreateReservationCommand with(UUID customerId, RegisterLeadCustomer.Command holder, UUID tour, UUID departure, UUID account) {
        return new CreateReservationCommand(TENANT, ACTOR, customerId, tour, departure, 2, "DEPOSIT", "TRANSFER", List.of(), account, holder);
    }

    private CreateReservationCommand operator(RegisterLeadCustomer.Command holder) {
        return with(null, holder, TOUR, DEPARTURE, null);
    }

    private void nothingWasWritten() {
        assertThat(stored).isEmpty();
        assertThat(events).isEmpty();
        assertThat(customers.keySet()).containsExactlyInAnyOrder(VERIFIED, UNVERIFIED);
    }

    @Test
    void aHolderIsRegisteredAndTheReservationIsCreatedForIt() {
        Reservation reservation = create.create(operator(holder("  Holder@Example.INVALID ", true, null)));

        assertThat(stored).containsExactly(reservation);
        Customer lead = customers.get(reservation.customerId());
        assertThat(lead.fullName()).isEqualTo("Holder Person");
        assertThat(lead.documentNumber().value()).isEqualTo("CC 900");
        assertThat(lead.contact().email()).isEqualTo("holder@example.invalid");
        assertThat(lead.tenantId()).isEqualTo(TENANT);
        assertThat(lead.accountId()).isNull();
        assertThat(reservation.createdBy()).isEqualTo(ACTOR);
        assertThat(lookedUp).containsExactly("holder@example.invalid");
    }

    @Test
    void theCustomerEventCarriesReferencesOnly() {
        Reservation reservation = create.create(operator(holder("holder@example.invalid", true, null)));

        assertThat(events).containsExactly(new CustomerRegistered(TENANT, reservation.customerId(), ACTOR, NOW));
    }

    @Test
    void bothACustomerAndAHolderAreRefusedNamingTheField() {
        assertThatThrownBy(() -> create.create(with(VERIFIED, holder("holder@example.invalid", true, null), TOUR, DEPARTURE, null)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("holder"));
        nothingWasWritten();
    }

    @Test
    void aHolderWithoutAcceptedTermsIsRefusedAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(operator(holder("holder@example.invalid", false, null))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("termsAccepted"));
        nothingWasWritten();
    }

    @Test
    void aHighRiskTourFromTheCatalogNeedsTheRiskDataOfTheHolder() {
        var command = with(null, holder("holder@example.invalid", true, null), RISKY_TOUR, RISKY_DEPARTURE, null);

        assertThatThrownBy(() -> create.create(command)).isInstanceOf(DomainException.class);
        nothingWasWritten();

        create.create(with(null, holder("holder@example.invalid", true, new HighRiskData("O+", "Contact 3001112233", "None", true)),
                RISKY_TOUR, RISKY_DEPARTURE, null));
        assertThat(stored).hasSize(1);
    }

    @Test
    void aFailedReservationLeavesNeitherTheCustomerNorTheEvent() {
        var full = with(null, holder("holder@example.invalid", true, null), TOUR, DEPARTURE, null);
        departures.plant(new DepartureState(DEPARTURE, TENANT, TOUR, NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 4, 4));

        assertThatThrownBy(() -> create.create(full)).isInstanceOf(RuntimeException.class);
        nothingWasWritten();
    }

    @Test
    void anEmailOfAVerifiedAccountLinksTheExistingCustomerInsteadOfDuplicatingIt() {
        Reservation reservation = create.create(operator(holder("Known@Example.invalid", true, null)));

        assertThat(reservation.customerId()).isEqualTo(VERIFIED);
        assertThat(customers).hasSize(2);
        assertThat(events).isEmpty();
        assertThat(customers.get(VERIFIED).fullName()).isEqualTo("Verified Person");
    }

    @Test
    void anEmailOfOnlyAnUnverifiedRecordIsRefusedNamingTheEmailAndNothingIsStored() {
        assertThatThrownBy(() -> create.create(operator(holder("pending@example.invalid", true, null))))
                .isInstanceOfSatisfying(BusinessRuleViolation.class, e -> assertThat(e.getMessage()).contains("pending@example.invalid"));
        nothingWasWritten();
    }

    @Test
    void aFinalCustomerWithoutARecordRegistersTheHolderLinkedToTheAccount() {
        Reservation reservation = create.create(with(null, holder("holder@example.invalid", true, null), TOUR, DEPARTURE, new UUID(9, 99)));

        assertThat(customers.get(reservation.customerId()).accountId()).isEqualTo(new UUID(9, 99));
        assertThat(events).hasSize(1);
    }

    @Test
    void aFinalCustomerWhoseAccountHasARecordSendsNoHolder() {
        assertThatThrownBy(() -> create.create(with(null, holder("holder@example.invalid", true, null), TOUR, DEPARTURE, ACCOUNT)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.field()).isEqualTo("holder"));
        nothingWasWritten();
    }

    @Test
    void aFinalCustomerCannotLinkTheRecordOfAnotherEmailOwner() {
        assertThatThrownBy(() -> create.create(with(null, holder("known@example.invalid", true, null), TOUR, DEPARTURE, new UUID(9, 99))))
                .isInstanceOf(BusinessRuleViolation.class);
        nothingWasWritten();
    }
}
