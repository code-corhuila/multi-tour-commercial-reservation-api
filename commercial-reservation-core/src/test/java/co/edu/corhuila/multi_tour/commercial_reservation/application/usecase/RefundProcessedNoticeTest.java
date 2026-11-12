package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.OutflowMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-015 api T-04, AC-15.4, RN-06: a refund that is executed writes one REFUND_PROCESSED notice, in the transaction of the step, with references only.
// The other steps and a repeated completion write none.
class RefundProcessedNoticeTest {

    private record Written(UUID tenantId, UUID reservationId, NoticeType type, String triggeringEventType, UUID triggeringEventId, Instant occurredAt,
            boolean insideTransaction) {
    }

    private final RefundFakes fakes = new RefundFakes();
    private final List<Written> notices = new ArrayList<>();
    private boolean inTransaction;
    private final ClientNoticeOutbox outbox = (tenant, reservation, type, eventType, eventId, at) ->
            notices.add(new Written(tenant, reservation, type, eventType, eventId, at, inTransaction));
    private final ExecuteRefundUseCase execute = new ExecuteRefundUseCase(fakes.refundRepository, new co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction() {
        @Override public <T> T inTransaction(java.util.function.Supplier<T> work) {
            inTransaction = true;
            try {
                return fakes.transaction.inTransaction(work);
            } finally {
                inTransaction = false;
            }
        }
    }, RefundFakes.CLOCK, outbox);
    private final UUID collaborator = UUID.randomUUID();
    private final UUID movement = UUID.randomUUID();

    private RefundAuthorization authorized() {
        RefundAuthorization refund = RefundAuthorization.determine(fakes.ids.newId(), RefundFakes.TENANT, UUID.randomUUID(),
                RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER, new Cents(100_000), RefundFakes.NOW, RefundFakes.NOW.plusSeconds(3_600));
        fakes.refunds.put(refund.id(), refund);
        return refund;
    }

    @Test
    void aCompletedRefundWritesOneRefundProcessedNoticeInTheSameTransaction() {
        RefundAuthorization refund = authorized();
        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.TRANSFER);

        execute.complete(RefundFakes.TENANT, refund.id(), movement);

        assertThat(notices).containsExactly(new Written(RefundFakes.TENANT, refund.reservationId(), NoticeType.REFUND_PROCESSED, "RefundExecuted",
                refund.id(), RefundFakes.NOW, true));
    }

    @Test
    void aRedeliveredCompletionLeavesOneNotice() {
        RefundAuthorization refund = authorized();
        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.TRANSFER);
        execute.complete(RefundFakes.TENANT, refund.id(), movement);

        execute.complete(RefundFakes.TENANT, refund.id(), movement);

        assertThat(notices).hasSize(1);
    }

    @Test
    void startingAndUndoingARefundNotifyNothing() {
        RefundAuthorization refund = authorized();

        execute.start(RefundFakes.TENANT, refund.id(), collaborator, OutflowMethod.CASH);
        execute.undo(RefundFakes.TENANT, refund.id());

        assertThat(notices).isEmpty();
    }
}
