package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExecuteRefund;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

// HU-COM-016 RN-03, RN-04, RN-14; plan D-06, DP-08: the aggregate decides, this class loads, applies one step and stores it only when it changed, so a
// repeated step writes nothing (AC-16.19). The workflow checked the role of the person and sends executedBy; the clock gives the instant.
public class ExecuteRefundUseCase implements ExecuteRefund {

    private final RefundAuthorizationRepository refunds;
    private final PaymentTransaction transaction;
    private final Clock clock;
    private final ClientNoticeOutbox notices;

    public ExecuteRefundUseCase(RefundAuthorizationRepository refunds, PaymentTransaction transaction, Clock clock) {
        this(refunds, transaction, clock, (tenantId, reservationId, type, eventType, eventId, occurredAt) -> { });
    }

    // HU-COM-015 AC-15.4, RN-06: the notice of an executed refund is written in the transaction of the completion step (plan D-03); RefundExecuted is
    // the trigger code the -db admits, and the refund authorization id is the key that makes a redelivery a no-op.
    public ExecuteRefundUseCase(RefundAuthorizationRepository refunds, PaymentTransaction transaction, Clock clock, ClientNoticeOutbox notices) {
        this.refunds = refunds;
        this.transaction = transaction;
        this.clock = clock;
        this.notices = notices;
    }

    @Override
    public RefundAuthorization start(UUID tenantId, UUID refundAuthorizationId, UUID executedBy, OutflowMethod method) {
        Instant now = Instant.now(clock);
        return step(tenantId, refundAuthorizationId, refund -> refund.startExecution(executedBy, method, now), false);
    }

    @Override
    public RefundAuthorization undo(UUID tenantId, UUID refundAuthorizationId) {
        return step(tenantId, refundAuthorizationId, RefundAuthorization::undoStart, false);
    }

    @Override
    public RefundAuthorization complete(UUID tenantId, UUID refundAuthorizationId, UUID cashMovementId) {
        Instant now = Instant.now(clock);
        return step(tenantId, refundAuthorizationId, refund -> refund.complete(cashMovementId, now), true);
    }

    @Override
    public RefundAuthorization find(UUID tenantId, UUID refundAuthorizationId) {
        return refunds.find(tenantId, refundAuthorizationId).orElseThrow(() -> new RefundNotFoundException("refund authorization", refundAuthorizationId));
    }

    private RefundAuthorization step(UUID tenantId, UUID id, UnaryOperator<RefundAuthorization> change, boolean notifies) {
        RefundAuthorization before = find(tenantId, id);
        RefundAuthorization after = change.apply(before);
        if (after.equals(before)) return before;
        return transaction.inTransaction(() -> {
            refunds.save(after);
            if (notifies) notices.append(tenantId, after.reservationId(), NoticeType.REFUND_PROCESSED, "RefundExecuted", after.id(), after.executedAt());
            return after;
        });
    }
}
