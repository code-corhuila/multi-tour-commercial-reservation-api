package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageAuthorizableReasons;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import java.util.List;
import java.util.UUID;

public class ManageAuthorizableReasonsUseCase implements ManageAuthorizableReasons {

    public ManageAuthorizableReasonsUseCase(AuthorizableReasonRepository reasons, IdGenerator ids) {
    }

    @Override
    public List<AuthorizableReason> list(DiscountRuleActor actor) {
        return null;
    }

    @Override
    public AuthorizableReason add(DiscountRuleActor actor, String text) {
        return null;
    }

    @Override
    public AuthorizableReason disable(DiscountRuleActor actor, UUID reasonId) {
        return null;
    }
}
