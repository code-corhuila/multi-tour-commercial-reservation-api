package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantDirectory;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The core of the creation (plan D-06): every check runs before the only write, so a refusal stores nothing.
// The tenant must be active (RN-16) and the group is checked as one (RN-08). Still out: the transaction and the outbox (T-08).
public class CreateReservationUseCase implements CreateReservation {

    private final TenantDirectory tenants;
    private final CustomerRepository customers;
    private final CustomerByAccount accounts;
    private final CatalogServiceRepository catalog;
    private final ReservableDeparture departures;
    private final ReservationPricing pricing;
    private final EnabledPaymentOptions options;
    private final ReservationRepository reservations;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateReservationUseCase(TenantDirectory tenants, CustomerRepository customers, CatalogServiceRepository catalog, ReservableDeparture departures,
            ReservationPricing pricing, EnabledPaymentOptions options, ReservationRepository reservations, IdGenerator ids,
            Clock clock) {
        this(tenants, customers, (tenant, account) -> Optional.empty(), catalog, departures, pricing, options, reservations, ids, clock);
    }

    public CreateReservationUseCase(TenantDirectory tenants, CustomerRepository customers, CustomerByAccount accounts,
            CatalogServiceRepository catalog, ReservableDeparture departures,
            ReservationPricing pricing, EnabledPaymentOptions options, ReservationRepository reservations, IdGenerator ids,
            Clock clock) {
        this.tenants = tenants;
        this.customers = customers;
        this.accounts = accounts;
        this.catalog = catalog;
        this.departures = departures;
        this.pricing = pricing;
        this.options = options;
        this.reservations = reservations;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Reservation create(CreateReservationCommand command) {
        requireMandatoryData(command);
        requireActiveTenant(command.tenantId());
        PaymentType type = PaymentType.fromCode(command.paymentType());
        PaymentMethod method = PaymentMethod.fromCode(command.paymentMethod());
        Customer lead = resolveLead(command);
        CatalogService tour = CatalogLookup.require(catalog, command.tenantId(), command.serviceId());
        requireDataTheTourNeeds(lead, tour);
        requireValidGroup(lead, tour, command.companions());
        departures.require(command.tenantId(), command.departureId(), command.serviceId(), command.people());
        if (!options.isEnabled(command.tenantId(), command.serviceId(), type, method)) {
            throw new BusinessRuleViolation("the payment type or medium is not enabled");
        }
        ReservationQuote quote = pricing.quote(command.tenantId(), tour, command.departureId(), command.people(),
                LocalDate.now(clock));
        Reservation reservation = Reservation.create(ids.newId(), command.tenantId(), lead.customerId(), command.departureId(),
                command.actorId(), command.people(), quote.service(), quote.projectedValue(), quote.finalValue(),
                quote.discounts(), type, method, Instant.now(clock), command.companions());
        reservations.save(reservation);
        return reservation;
    }

    // RN-01 (AC-08.1, AC-08.9): a Final Customer reserves for the customer linked to their account, never for one they name.
    // An account with no record still needs the holder block (AC-08.13); until that block exists the field is named.
    private Customer resolveLead(CreateReservationCommand command) {
        if (command.ownerAccountId() == null) {
            return customers.findByTenantAndId(command.tenantId(), command.customerId())
                    .filter(found -> found.tenantId().equals(command.tenantId()))
                    .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));
        }
        if (command.customerId() != null) throw new DomainException("customerId", "a customer cannot be named; it is the one of the account");
        return accounts.findByTenantAndAccount(command.tenantId(), command.ownerAccountId())
                .filter(found -> found.tenantId().equals(command.tenantId()))
                .orElseThrow(() -> new MissingReservationDataException(List.of("holder")));
    }

    // AC-07.2: each missing field is named at once.
    private static void requireMandatoryData(CreateReservationCommand command) {
        List<String> missing = new ArrayList<>();
        if (command.customerId() == null && command.ownerAccountId() == null) missing.add("customerId");
        if (command.departureId() == null) missing.add("salidaId");
        if (command.serviceId() == null) missing.add("serviceId");
        if (command.people() < 1) missing.add("people");
        if (command.paymentType() == null) missing.add("paymentType");
        if (command.paymentMethod() == null) missing.add("paymentMethod");
        if (!missing.isEmpty()) throw new MissingReservationDataException(missing);
    }

    // AC-07.21 (RN-17): a high-risk tour needs the extra data of the lead; the names are returned, never the values.
    private static void requireDataTheTourNeeds(Customer lead, CatalogService tour) {
        if (!tour.terms().highRisk()) return;
        List<String> missing = lead.highRiskData() == null ? new HighRiskData(null, null, null, false).missingFields()
                : lead.highRiskData().missingFields();
        if (!missing.isEmpty()) throw new IncompleteLeadCustomerException(missing);
    }

    // AC-07.20 (RN-16): an inactive or unknown tenant creates nothing; an unknown one is refused too, never assumed active.
    private void requireActiveTenant(UUID tenantId) {
        boolean active = tenantId != null && tenants.resolve(tenantId.toString())
                .filter(found -> tenantId.equals(found.tenantId())).map(TenantRef::active).orElse(false);
        if (!active) throw new BusinessRuleViolation("the tenant is not active");
    }

    // AC-07.11 (RN-08): no document twice, the lead's included and compared normalized; the extra data of a high-risk tour.
    private static void requireValidGroup(Customer lead, CatalogService tour, List<Companion> companions) {
        for (int i = 0; i < companions.size(); i++) {
            Companion companion = companions.get(i);
            if (companion.documentNumber().equals(lead.documentNumber())) {
                throw new BusinessRuleViolation("a document is already in the group");
            }
            if (tour.terms().highRisk()) {
                List<String> missing = companion.highRiskData() == null
                        ? new HighRiskData(null, null, null, false).missingFields() : companion.highRiskData().missingFields();
                if (!missing.isEmpty()) throw new IncompleteCompanionException(i + 1, missing);
            }
        }
    }
}
