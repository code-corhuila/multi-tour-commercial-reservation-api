package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountLinkOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.LinkAccountOnEmailVerified;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountEmailVerified;

// The link and the event id commit or roll back as one (norm 5.3.11). It never creates a record: with none to link, the link is
// made at the first reservation (ADR-004 D5). The id is recorded in every case, so the relay stops.
public final class LinkAccountOnEmailVerifiedUseCase implements LinkAccountOnEmailVerified {

    private final CustomerByAccount accounts;
    private final CustomerByEmail emails;
    private final CustomerAccountLink links;
    private final ProcessedEvents processed;
    private final TenantEventTransaction transaction;

    public LinkAccountOnEmailVerifiedUseCase(CustomerByAccount accounts, CustomerByEmail emails, CustomerAccountLink links,
            ProcessedEvents processed, TenantEventTransaction transaction) {
        this.accounts = accounts;
        this.emails = emails;
        this.links = links;
        this.processed = processed;
        this.transaction = transaction;
    }

    @Override
    public AccountLinkOutcome link(AccountEmailVerified event) {
        return transaction.inTransaction(() -> {
            if (processed.wasProcessed(event.eventId())) return AccountLinkOutcome.ALREADY_PROCESSED;
            boolean linked = accounts.findByTenantAndAccount(event.tenantId(), event.accountId()).isEmpty()
                    && emails.findByTenantAndEmail(event.tenantId(), event.email())
                            .filter(match -> !match.verified())
                            .map(match -> links.linkIfUnlinked(event.tenantId(), match.customerId(), event.accountId()))
                            .orElse(false);
            processed.record(event.eventId());
            return linked ? AccountLinkOutcome.LINKED : AccountLinkOutcome.NOTHING_TO_LINK;
        });
    }
}
