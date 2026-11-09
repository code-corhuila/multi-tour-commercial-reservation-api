package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceDetail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadCreditBalances;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-COM-017 AC-17.13, AC-17.16, DP-05: a Final Customer is resolved from their account to their customer record, and that customer becomes a condition of every
// query (the customerId they send is ignored), so an unlinked account sees an empty list and a foreign credit looks missing. The summary is for staff only.
public class ReadCreditBalancesUseCase implements ReadCreditBalances {

    private final CreditBalanceQueries queries;
    private final CustomerByAccount accounts;

    public ReadCreditBalancesUseCase(CreditBalanceQueries queries, CustomerByAccount accounts) {
        this.queries = queries;
        this.accounts = accounts;
    }

    @Override
    public RefundPage<CreditBalanceView> page(PaymentActor actor, UUID customerId, boolean availableOnly, int page, int limit) {
        if (actor.role() != Role.FINAL_CUSTOMER) return queries.page(actor.tenantId(), customerId, availableOnly, page, limit);
        return own(actor).map(own -> queries.page(actor.tenantId(), own, availableOnly, page, limit)).orElse(new RefundPage<>(List.of(), 0));
    }

    @Override
    public CreditBalanceDetail detail(PaymentActor actor, UUID creditBalanceId) {
        UUID customer = actor.role() == Role.FINAL_CUSTOMER
                ? own(actor).orElseThrow(() -> new CreditBalanceNotFoundException(creditBalanceId)) : null;
        CreditBalanceView view = queries.find(actor.tenantId(), customer, creditBalanceId).orElseThrow(() -> new CreditBalanceNotFoundException(creditBalanceId));
        return new CreditBalanceDetail(view, queries.applications(actor.tenantId(), creditBalanceId), queries.refundRequests(actor.tenantId(), creditBalanceId));
    }

    @Override
    public CreditSummary summary(PaymentActor actor) {
        if (actor.role() == Role.FINAL_CUSTOMER) throw new PaymentAccessDeniedException();
        return queries.summary(actor.tenantId());
    }

    private Optional<UUID> own(PaymentActor actor) {
        return accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId()).map(Customer::customerId);
    }
}
