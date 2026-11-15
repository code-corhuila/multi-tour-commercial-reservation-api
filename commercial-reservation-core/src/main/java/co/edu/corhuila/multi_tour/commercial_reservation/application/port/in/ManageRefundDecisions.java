package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import java.util.UUID;

// HU-COM-016: the three acts of the Tenant Administrator. No amount travels in any of them (RN-01).
public interface ManageRefundDecisions {

    // DP-01: the decision of the 100 % refund is also its authorization; the reason is optional.
    FinancialDecision recordRefundDecision(PaymentActor actor, UUID financialDecisionId, String reason);

    default FinancialDecision recordRefundDecision(PaymentActor actor, UUID financialDecisionId) {
        return recordRefundDecision(actor, financialDecisionId, null);
    }

    RefundAuthorization authorize(PaymentActor actor, UUID financialDecisionId, String reason);

    FinancialDecision reject(PaymentActor actor, UUID financialDecisionId, String justification);
}
