package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuoteQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

// D-10: the service computes, the portal shows. A type is offered when any method is enabled for it and a method when any
// type is enabled for it; the amount to pay is the minimum deposit on the first deposit and the pending balance otherwise.
public class GetPaymentQuoteUseCase implements GetPaymentQuote {

    private final ReservationRepository reservations;
    private final EnabledPaymentOptions options;
    private final PaymentConditionReader conditions;
    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;

    public GetPaymentQuoteUseCase(ReservationRepository reservations, EnabledPaymentOptions options, PaymentConditionReader conditions,
            PaymentDeadlineReader deadlines, DepartureReader departures) {
        this.reservations = reservations;
        this.options = options;
        this.conditions = conditions;
        this.deadlines = deadlines;
        this.departures = departures;
    }

    @Override
    public PaymentQuote quote(PaymentQuoteQuery query) {
        Reservation reservation = reservations.findByTenantAndId(query.tenantId(), query.reservationId())
                .filter(found -> found.tenantId().equals(query.tenantId()))
                .orElseThrow(() -> new ReservationNotFoundException(query.reservationId()));
        var service = reservation.service().serviceId();
        List<PaymentType> types = Arrays.stream(PaymentType.values()).filter(type -> Arrays.stream(PaymentMethod.values())
                .anyMatch(method -> options.isEnabled(query.tenantId(), service, type, method))).toList();
        List<PaymentMethod> methods = Arrays.stream(PaymentMethod.values()).filter(method -> Arrays.stream(PaymentType.values())
                .anyMatch(type -> options.isEnabled(query.tenantId(), service, type, method))).toList();
        PaymentType chosen = query.type() != null ? query.type() : reservation.expectedPaymentType();
        Cents minimum = chosen == PaymentType.DEPOSIT && types.contains(chosen)
                ? conditions.minimumDepositFor(query.tenantId(), reservation).orElse(null) : null;
        Cents amount = null;
        if (types.contains(chosen)) {
            boolean firstPayment = reservation.pendingBalance().equals(reservation.finalValue());
            amount = minimum != null && firstPayment ? minimum : reservation.pendingBalance();
        }
        // The deadline in force (extension, capped at the departure start, none while frozen), the instant the service judges (RN-02, RN-17).
        Instant start = departures.find(query.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
        Instant deadline = deadlines.openDeadline(query.tenantId(), reservation.reservationId()).map(found -> found.inForce(start)).orElse(null);
        return new PaymentQuote(types, methods, chosen, amount, minimum, reservation.pendingBalance(), deadline);
    }
}
