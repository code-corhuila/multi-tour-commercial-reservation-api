package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.util.List;

// What a quote and a creation share (plan D-03): the tour line, the single transport line (null when the tour has none),
// the projected value, one line per rule in force and the final value. Every figure is in cents.
public record ReservationQuote(ReservedService service, int people, Cents tourLine, Cents transportLine,
        Cents projectedValue, List<FixedDiscount> discounts, Cents finalValue) {

    public ReservationQuote {
        discounts = List.copyOf(discounts);
    }
}
