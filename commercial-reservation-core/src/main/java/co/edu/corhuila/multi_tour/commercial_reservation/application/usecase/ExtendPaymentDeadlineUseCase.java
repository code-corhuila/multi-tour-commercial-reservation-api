package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExtendPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.time.Clock;
import java.time.Instant;

// RN-03, AC-11.3: the rules live in PaymentDeadline; this class loads, checks the reservation, writes and answers. A reservation of
// another tenant looks missing.
public class ExtendPaymentDeadlineUseCase implements ExtendPaymentDeadline {

    private final ReservationRepository reservations;
    private final PaymentDeadlineReader deadlines;
    private final DepartureReader departures;
    private final PaymentExtensionPolicy policy;
    private final PaymentDeadlineWriter writer;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public ExtendPaymentDeadlineUseCase(ReservationRepository reservations, PaymentDeadlineReader deadlines, DepartureReader departures,
            PaymentExtensionPolicy policy, PaymentDeadlineWriter writer, PaymentTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.deadlines = deadlines;
        this.departures = departures;
        this.policy = policy;
        this.writer = writer;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DeadlineView extend(ExtendDeadlineCommand command) {
        PaymentAuthorization.requireExtend(command.actor());
        if (command.hours() == null || command.hours() < 1) throw new DomainException("hours", "hours must be an integer of at least 1");
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.lockByTenantAndId(command.actor().tenantId(), command.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));
            if (reservation.reservationStatus() != ReservationStatus.PENDING_PAYMENT)
                throw new BusinessRuleViolation("only a reservation pending payment can have its deadline extended");
            PaymentDeadline current = deadlines.openDeadline(reservation.tenantId(), reservation.reservationId())
                    .orElseThrow(() -> new BusinessRuleViolation("the reservation has no payment deadline"));
            int maximum = policy.maximumHours(reservation.tenantId(), reservation)
                    .orElseThrow(() -> new BusinessRuleViolation("the maximum extension is not configured"));
            Instant start = departures.find(reservation.tenantId(), reservation.departureId()).map(DepartureState::startsAt).orElse(null);
            Instant now = Instant.now(clock);
            PaymentDeadline extended = current.extended(command.hours(), maximum, command.actor().actorId(), now, start);
            writer.saveExtension(reservation.tenantId(), reservation.reservationId(), command.hours(), now, command.actor().actorId());
            return new DeadlineView(reservation.reservationId(), extended, start, extended.effective(start));
        });
    }
}
