package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ManageAuthorizableReasonsUseCase implements ManageAuthorizableReasons {
    private final AuthorizableReasonRepository reasons;
    private final IdGenerator ids;

    public ManageAuthorizableReasonsUseCase(AuthorizableReasonRepository reasons, IdGenerator ids) {
        this.reasons = reasons;
        this.ids = ids;
    }

    @Override
    public List<AuthorizableReason> list(DiscountRuleActor actor) {
        DiscountRuleAuthorization.requireWrite(actor);
        return reasons.findByTenant(actor.tenantId());
    }

    @Override
    public AuthorizableReason add(DiscountRuleActor actor, String text) {
        DiscountRuleAuthorization.requireWrite(actor);
        AuthorizableReason reason = AuthorizableReason.create(actor.tenantId(), ids.newId(), text);
        if (reasons.findByTenant(actor.tenantId()).stream()
                .anyMatch(existing -> key(existing.text()).equals(key(reason.text())))) {
            throw new DomainException("reason", "the reason already exists");
        }
        reasons.save(reason);
        return reason;
    }

    @Override
    public AuthorizableReason disable(DiscountRuleActor actor, UUID reasonId) {
        DiscountRuleAuthorization.requireWrite(actor);
        AuthorizableReason reason = reasons.findByTenantAndId(actor.tenantId(), reasonId)
                .orElseThrow(() -> new AuthorizableReasonNotFoundException(reasonId));
        if (!reason.enabled()) {
            return reason;
        }
        // TODO: the adapter must lock the catalogue so two concurrent disables cannot empty it.
        if (reasons.findByTenant(actor.tenantId()).stream().filter(AuthorizableReason::enabled).count() <= 1) {
            throw new DomainException("reason", "the last enabled reason cannot be disabled");
        }
        AuthorizableReason disabled = reason.disabled();
        reasons.save(disabled);
        return disabled;
    }

    private static String key(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
