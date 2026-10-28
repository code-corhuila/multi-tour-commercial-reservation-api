package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import java.time.Clock;
import java.util.UUID;

public class RegisterAdditionalDiscountUseCase implements RegisterAdditionalDiscount {

    public RegisterAdditionalDiscountUseCase(ReservationReader reservations, AdditionalDiscountRepository discounts,
            AuthorizableReasonRepository reasons, ReservationModifiedOutbox outbox, ExcessTreatment excess,
            DiscountRuleTransaction transaction, Clock clock) {
    }

    @Override
    public DiscountLine preview(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints) {
        return null;
    }

    @Override
    public AdditionalDiscount register(DiscountRuleActor actor, Command command) {
        return null;
    }
}
