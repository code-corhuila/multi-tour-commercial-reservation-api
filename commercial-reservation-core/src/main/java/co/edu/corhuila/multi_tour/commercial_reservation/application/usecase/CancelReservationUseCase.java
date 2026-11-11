package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancelReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CancelReservation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationCause;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CancellationSettlement;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CapacityReleased;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.InvalidStatusTransitionException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// RN-01 to RN-05, RN-07, RN-10; AC-13.1, AC-13.2, AC-13.4, AC-13.6, AC-13.7, AC-13.13: reuses Reservation.cancel of HU-COM-011 with the
// cause CUSTOMER_REQUEST. The settlement, the record, the seat release, the events and the opening of the decision commit together.
// The check of the role precedes any load; a reservation of another tenant looks missing.
public class CancelReservationUseCase implements CancelReservation {

    static final int MAXIMUM_REASON = 500;

    private final ReservationRepository reservations;
    private final DepartureReader departures;
    private final SalidaCapacityHolder holder;
    private final CancellationPolicyReader policy;
    private final ReprogrammingMarkReader marks;
    private final FinancialDecisionOpener decisions;
    private final CancellationRecords records;
    private final PaymentEventOutbox outbox;
    private final PaymentTransaction transaction;
    private final Clock clock;

    public CancelReservationUseCase(ReservationRepository reservations, DepartureReader departures, SalidaCapacityHolder holder,
            CancellationPolicyReader policy, ReprogrammingMarkReader marks, FinancialDecisionOpener decisions, CancellationRecords records,
            PaymentEventOutbox outbox, PaymentTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.departures = departures;
        this.holder = holder;
        this.policy = policy;
        this.marks = marks;
        this.decisions = decisions;
        this.records = records;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public CancellationRecord cancel(CancelReservationCommand command) {
        CancellationAuthorization.requireCancel(command.actor());
        String reason = command.reason() == null ? "" : command.reason().strip();
        requireValidRequest(reason, command.origin());
        return transaction.inTransaction(() -> {
            Reservation reservation = reservations.lockByTenantAndId(command.actor().tenantId(), command.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));
            if (!QuoteCancellationUseCase.isCancellableStatus(reservation))
                throw new InvalidStatusTransitionException("the reservation can no longer be cancelled");
            if (marks.isMarked(reservation.tenantId(), reservation.reservationId()))
                throw new BusinessRuleViolation("the customer must reject the rescheduling proposal first");
            Instant now = Instant.now(clock);
            CancellationSettlement settlement = QuoteCancellationUseCase.settle(policy, departures, reservation, now);
            int released = holder.release(reservation.tenantId(), reservation.reservationId());
            if (released > 0) reservation.releaseSeat(CapacityReleased.CANCELLED_AT_CUSTOMER_REQUEST, now);
            reservation.cancel(CancellationCause.CUSTOMER_REQUEST, command.actor().actorId(), now, released);
            reservations.save(reservation);
            reservation.pullEvents().forEach(outbox::append);
            boolean opened = settlement.refundable().amount() > 0;
            CancellationRecord record = new CancellationRecord(reservation.reservationId(), reason, CancellationCause.CUSTOMER_REQUEST,
                    settlement, opened, command.actor().actorId(), now);
            records.save(reservation.tenantId(), record);
            if (opened) decisions.open(reservation.tenantId(), reservation.reservationId(), settlement.validated(), settlement.penalty(), now);
            return record;
        });
    }

    // AC-13.7: every invalid field is named at once; the reason is 1 to 500 characters after trimming and the origin is the one this route
    // registers (DP-04).
    private static void requireValidRequest(String reason, String origin) {
        List<String> invalid = new ArrayList<>();
        if (reason.isEmpty() || reason.length() > MAXIMUM_REASON) invalid.add("reason");
        if (!CancellationCause.CUSTOMER_REQUEST.name().equals(origin)) invalid.add("origin");
        if (!invalid.isEmpty()) throw new MissingReservationDataException(invalid);
    }
}
