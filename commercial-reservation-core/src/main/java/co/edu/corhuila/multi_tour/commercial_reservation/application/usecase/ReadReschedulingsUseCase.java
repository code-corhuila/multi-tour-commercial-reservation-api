package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReschedulingView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadReschedulings;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingReads;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// HU-COM-018 AC-18.15, AC-18.16, AC-18.18, plan D-07: the role comes before any load. A Final Customer is resolved from their account to their customer record and that customer becomes a
// condition of every query, so an unlinked account sees an empty list and a foreign episode looks missing. The other roles that read are the Administrator, the Collaborator, the Manager,
// the Accountant and the Analyst; any other role is refused.
public class ReadReschedulingsUseCase implements ReadReschedulings {
    private static final Set<Role> READERS = Set.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER);

    private final ReschedulingReads reads;
    private final CustomerByAccount accounts;

    public ReadReschedulingsUseCase(ReschedulingReads reads, CustomerByAccount accounts) {
        this.reads = reads;
        this.accounts = accounts;
    }

    @Override
    public RefundPage<ReschedulingView> page(PaymentActor actor, UUID reservationId, UUID departureId, EpisodeState state, int page, int limit) {
        requireReader(actor);
        if (actor.role() != Role.FINAL_CUSTOMER) return reads.page(actor.tenantId(), null, reservationId, departureId, state, page, limit);
        return own(actor).map(own -> reads.page(actor.tenantId(), own, reservationId, departureId, state, page, limit)).orElse(new RefundPage<>(List.of(), 0));
    }

    @Override
    public ReschedulingView get(PaymentActor actor, UUID episodeId) {
        requireReader(actor);
        UUID customer = actor.role() == Role.FINAL_CUSTOMER ? own(actor).orElseThrow(() -> new ReschedulingNotFoundException(episodeId)) : null;
        return reads.find(actor.tenantId(), customer, episodeId).orElseThrow(() -> new ReschedulingNotFoundException(episodeId));
    }

    private static void requireReader(PaymentActor actor) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null || !READERS.contains(actor.role())) throw new PaymentAccessDeniedException();
    }

    private Optional<UUID> own(PaymentActor actor) {
        return accounts.findByTenantAndAccount(actor.tenantId(), actor.actorId()).map(Customer::customerId);
    }
}
