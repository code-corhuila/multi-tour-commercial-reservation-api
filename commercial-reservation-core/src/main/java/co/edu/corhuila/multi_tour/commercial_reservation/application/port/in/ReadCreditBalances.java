package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceDetail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import java.util.UUID;

// HU-COM-017: staff reads every credit of the tenant; a Final Customer reads only their own, and a foreign one looks missing.
public interface ReadCreditBalances {

    RefundPage<CreditBalanceView> page(PaymentActor actor, UUID customerId, boolean availableOnly, int page, int limit);

    CreditBalanceDetail detail(PaymentActor actor, UUID creditBalanceId);

    CreditSummary summary(PaymentActor actor);
}
