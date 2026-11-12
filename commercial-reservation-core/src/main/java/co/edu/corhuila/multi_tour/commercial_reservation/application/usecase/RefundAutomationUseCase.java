package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HU-COM-016 plan D-06, DP-08, DP-15: the steps that the service token asks. The workflow and the job verified the caller; the service decides whether
// the 24 hours or the five business days have passed and never trusts an instant from outside. The default and its automatic authorization share one transaction.
public class RefundAutomationUseCase implements RefundAutomation {

    private final FinancialDecisionRepository decisions;
    private final RefundAuthorizationRepository refunds;
    private final BusinessCalendar calendar;
    private final RefundAuthorizedOutbox outbox;
    private final IdGenerator ids;
    private final PaymentTransaction transaction;
    private final Clock clock;
    private final SuspensionReader suspensions;

    public RefundAutomationUseCase(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, PaymentTransaction transaction, Clock clock) {
        this(decisions, refunds, calendar, outbox, ids, transaction, clock, tenantId -> Optional.empty());
    }

    // HU-COM-016 DP-16: the suspension reader tells whether the tenant is inactive (HU-COM-020).
    public RefundAutomationUseCase(FinancialDecisionRepository decisions, RefundAuthorizationRepository refunds, BusinessCalendar calendar,
            RefundAuthorizedOutbox outbox, IdGenerator ids, PaymentTransaction transaction, Clock clock, SuspensionReader suspensions) {
        this.suspensions = suspensions;
        this.decisions = decisions;
        this.refunds = refunds;
        this.calendar = calendar;
        this.outbox = outbox;
        this.ids = ids;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public RefundAuthorization applyDefault(UUID tenantId, UUID financialDecisionId) {
        // HU-COM-016 DP-16 (Project Lead, 2026-11-12): the default of 24 hours does not run while the tenant is inactive.
        if (suspensions.openSince(tenantId).isPresent())
            throw new BusinessRuleViolation("the tenant is inactive: refund decisions are closed until it is active again");
        FinancialDecision decision = decisions.find(tenantId, financialDecisionId)
                .orElseThrow(() -> new RefundNotFoundException("financial decision", financialDecisionId));
        Instant now = Instant.now(clock);
        return transaction.inTransaction(() -> {
            FinancialDecision decided = decision.applyDefault(now);
            decisions.save(decided);
            return authorized(RefundAuthorization.authorizeByDefault(ids.newId(), decided, now, due(decided.recognizedAt())));
        });
    }

    @Override
    public RefundAuthorization markOverdue(UUID tenantId, UUID refundAuthorizationId) {
        RefundAuthorization refund = refunds.find(tenantId, refundAuthorizationId)
                .orElseThrow(() -> new RefundNotFoundException("refund authorization", refundAuthorizationId));
        RefundAuthorization marked = refund.markOverdue(Instant.now(clock));
        if (!marked.equals(refund)) transaction.inTransaction(() -> {
            refunds.save(marked);
            return marked;
        });
        return marked;
    }

    @Override
    public RefundAuthorization determine(UUID tenantId, UUID reservationId, RefundOrigin origin, Cents amount) {
        Instant now = Instant.now(clock);
        return transaction.inTransaction(
                () -> authorized(RefundAuthorization.determine(ids.newId(), tenantId, reservationId, origin, amount, now, due(now))));
    }

    private Instant due(Instant from) {
        return calendar.addBusinessDays(from, RefundAuthorizationRules.BUSINESS_DAYS);
    }

    private RefundAuthorization authorized(RefundAuthorization refund) {
        refunds.save(refund);
        outbox.append(refund);
        return refund;
    }
}
