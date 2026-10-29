package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import java.util.List;
import java.util.UUID;

public interface ManageAuthorizableReasons {
    List<AuthorizableReason> list(DiscountRuleActor actor);

    AuthorizableReason add(DiscountRuleActor actor, String text);

    AuthorizableReason disable(DiscountRuleActor actor, UUID reasonId);
}
