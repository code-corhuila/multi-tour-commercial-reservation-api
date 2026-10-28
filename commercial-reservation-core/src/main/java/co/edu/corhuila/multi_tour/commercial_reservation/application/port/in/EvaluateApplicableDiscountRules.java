package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ApplicableDiscountRules;
import java.time.LocalDate;
import java.util.UUID;

// Called in process by other contexts (plan D-03); never an HTTP route. The reference date is always explicit (plan D-04, DP-02).
public interface EvaluateApplicableDiscountRules {

    ApplicableDiscountRules evaluate(UUID tenantId, UUID serviceId, LocalDate referenceDate);
}
