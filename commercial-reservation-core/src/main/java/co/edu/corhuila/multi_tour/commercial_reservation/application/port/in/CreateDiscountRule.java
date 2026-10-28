package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.LocalDate;

public interface CreateDiscountRule {
    DiscountRule create(DiscountRuleActor actor, Command command);

    record Command(String name, LocalDate validFrom, LocalDate validTo, Integer priority, DiscountBase calculationBase,
            Integer percentageBasisPoints, Long amountCents, ServiceCoverage serviceCoverage, Long capCents) {
    }
}
