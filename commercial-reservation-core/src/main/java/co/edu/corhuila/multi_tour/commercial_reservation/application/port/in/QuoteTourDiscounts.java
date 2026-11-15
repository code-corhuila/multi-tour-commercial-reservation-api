package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.LocalDate;
import java.util.UUID;

// Quote of a catalog tour of the actor's tenant on a date (AC-04.1, AC-04.2); no reservation is read or written.
public interface QuoteTourDiscounts {

    DiscountBreakdown quote(DiscountRuleActor actor, UUID serviceId, LocalDate referenceDate, Cents baseValue);
}
