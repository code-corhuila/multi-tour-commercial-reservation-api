package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;

// Schema RegisterAdditionalDiscountRequest: only the percentage and the enabled reason. The tenant and the author are not
// accepted (unknown fields are refused) because they are the token's.
public record RegisterAdditionalDiscountRequest(Integer basisPoints, UUID reasonId) {

    RegisterAdditionalDiscount.Command toCommand(UUID reservationId) {
        if (basisPoints == null) throw new DomainException("basisPoints", "basisPoints is required");
        AdditionalDiscountController.requireRange(basisPoints);
        if (reasonId == null) throw new DomainException("reasonId", "reasonId is required");
        return new RegisterAdditionalDiscount.Command(reservationId, reasonId, basisPoints);
    }
}
