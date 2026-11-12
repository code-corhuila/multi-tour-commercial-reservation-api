package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import java.util.UUID;

// HU-COM-017: the acts on a credit balance that the routes call. The tenant, the person and the role come from the token; a Final Customer is resolved to their own
// customer record here, so the route never decides who owns a credit.
public interface ManageCreditBalances {

    CreditBalanceView recordCreditDecision(PaymentActor actor, UUID financialDecisionId);

    CreditApplication apply(PaymentActor actor, UUID creditBalanceId, UUID targetReservationId, long amountCents);

    CreditBalanceView credit(PaymentActor actor, UUID creditBalanceId);

    CreditApplication application(PaymentActor actor, UUID creditBalanceId, UUID applicationId);
}
