package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExtendDeadlineCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ExtendPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RecordPaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentFollowupRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import java.util.UUID;

// Same mechanism as the payments (norm 5.3.8, AC-11.9): a known key answers the stored result and writes nothing; a key lost in a race
// discards the second write with its transaction. A key belongs to one request: used on another it is a rule violation, never a replay.
public final class IdempotentDeadlineWrites {
    public record Followup(PaymentFollowup followup, boolean created) {}

    public record Extension(DeadlineView view, boolean created) {}

    private static final String USED = "the Idempotency-Key was already used by another request";
    private final RecordPaymentFollowup followups;
    private final ExtendPaymentDeadline extensions;
    private final GetPaymentDeadline reads;
    private final PaymentFollowupRepository history;
    private final IdempotentWrites writes;

    public IdempotentDeadlineWrites(RecordPaymentFollowup followups, ExtendPaymentDeadline extensions, GetPaymentDeadline reads,
            PaymentFollowupRepository history, IdempotentWrites writes) {
        this.followups = followups;
        this.extensions = extensions;
        this.reads = reads;
        this.history = history;
        this.writes = writes;
    }

    public Followup followup(RecordFollowupCommand command, String key) {
        var done = writes.run(command.actor().tenantId(), key, "payment-followup", () -> followups.record(command), PaymentFollowup::followupId,
                stored -> replayFollowup(command, stored));
        return new Followup(done.value(), done.created());
    }

    public Extension extension(ExtendDeadlineCommand command, String key) {
        var done = writes.run(command.actor().tenantId(), key, "payment-deadline-extension", () -> extensions.extend(command),
                DeadlineView::reservationId, stored -> replayExtension(command, stored));
        return new Extension(done.value(), done.created());
    }

    private PaymentFollowup replayFollowup(RecordFollowupCommand command, UUID followupId) {
        return history.find(command.actor().tenantId(), followupId)
                .filter(found -> found.reservationId().equals(command.reservationId()) && command.note() != null
                        && found.note().equals(command.note().strip()))
                .orElseThrow(() -> new BusinessRuleViolation(USED));
    }

    private DeadlineView replayExtension(ExtendDeadlineCommand command, UUID reservationId) {
        if (!reservationId.equals(command.reservationId())) throw new BusinessRuleViolation(USED);
        DeadlineView view = reads.get(command.actor(), reservationId);
        if (command.hours() == null || !command.hours().equals(view.deadline().extensionHours())) throw new BusinessRuleViolation(USED);
        return view;
    }
}
