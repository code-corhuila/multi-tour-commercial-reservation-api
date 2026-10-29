package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
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

// The core of the creation (plan D-06): every check runs before the only write, so a refusal stores nothing.
// Still out of this class: the group (T-05 with HU-COM-005), the tenant status (DP-13), the transaction and the outbox (T-08).
public class CreateReservationUseCase implements CreateReservation {

    private final CustomerRepository customers;
    private final CatalogServiceRepository catalog;
    private final ReservableDeparture departures;
    private final ReservationPricing pricing;
    private final EnabledPaymentOptions options;
    private final ReservationRepository reservations;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateReservationUseCase(CustomerRepository customers, CatalogServiceRepository catalog, ReservableDeparture departures,
            ReservationPricing pricing, EnabledPaymentOptions options, ReservationRepository reservations, IdGenerator ids,
            Clock clock) {
        this.customers = customers;
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
        PaymentType type = PaymentType.fromCode(command.paymentType());
        PaymentMethod method = PaymentMethod.fromCode(command.paymentMethod());
        Customer lead = customers.findByTenantAndId(command.tenantId(), command.customerId())
                .filter(found -> found.tenantId().equals(command.tenantId()))
                .orElseThrow(() -> new CustomerNotFoundException(command.customerId()));
        CatalogService tour = CatalogLookup.require(catalog, command.tenantId(), command.serviceId());
        requireDataTheTourNeeds(lead, tour);
        departures.require(command.tenantId(), command.departureId(), command.serviceId(), command.people());
        if (!options.isEnabled(command.tenantId(), command.serviceId(), type, method)) {
            throw new BusinessRuleViolation("the payment type or medium is not enabled");
        }
        ReservationQuote quote = pricing.quote(command.tenantId(), tour, command.departureId(), command.people(),
                LocalDate.now(clock));
        Reservation reservation = Reservation.create(ids.newId(), command.tenantId(), lead.customerId(), command.departureId(),
                command.actorId(), command.people(), quote.service(), quote.projectedValue(), quote.finalValue(),
                quote.discounts(), type, method, Instant.now(clock));
        reservations.save(reservation);
        return reservation;
    }

    // AC-07.2: each missing field is named at once.
    private static void requireMandatoryData(CreateReservationCommand command) {
        List<String> missing = new ArrayList<>();
        if (command.customerId() == null) missing.add("customerId");
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
}
