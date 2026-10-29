package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import java.util.UUID;

public interface RegisterAdditionalDiscount {
    DiscountLine preview(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints);

    AdditionalDiscount register(DiscountRuleActor actor, Command command);

    record Command(UUID reservationId, UUID reasonId, Integer percentageBasisPoints) {
    }
}
