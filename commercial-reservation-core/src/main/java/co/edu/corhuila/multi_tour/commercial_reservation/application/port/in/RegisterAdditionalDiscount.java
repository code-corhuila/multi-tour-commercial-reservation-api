package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import java.util.UUID;

public interface RegisterAdditionalDiscount {
    DiscountLine preview(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints);

    // The same line as preview plus what was already paid above the new value (AC-04.13); it opens nothing.
    Preview previewWithExcess(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints);

    AdditionalDiscount register(DiscountRuleActor actor, Command command);

    record Preview(DiscountLine line, Cents excess) {
    }

    record Command(UUID reservationId, UUID reasonId, Integer percentageBasisPoints) {
    }
}
