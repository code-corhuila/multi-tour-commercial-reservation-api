package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountBreakdown;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.LocalDate;
import java.util.UUID;

// Quote of a tour on a date (AC-04.1 to AC-04.3, AC-04.5); the reference date is explicit (plan D-03, DP-04). A fixed reservation waits for HU-COM-007.
public interface QuoteDiscountBreakdown {

    DiscountBreakdown quote(UUID tenantId, UUID serviceId, LocalDate referenceDate, Cents baseValue);
}
