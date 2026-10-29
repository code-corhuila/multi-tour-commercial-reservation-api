package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.CreateDiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

// Boxed values keep absence, so the domain names the missing field (AC-03.3, AC-03.4). Field names are PROPOSED.
public record CreateDiscountRuleRequest(String name, LocalDate validFrom, LocalDate validTo, Integer priority,
        DiscountBase calculationBase, Integer percentageBasisPoints, Long amountCents, Coverage serviceCoverage,
        List<UUID> serviceIds, Long capCents) {
    public enum Coverage { ALL, SELECTED }

    public CreateDiscountRule.Command toCommand() {
        ServiceCoverage coverage;
        if (serviceCoverage == Coverage.SELECTED) {
            coverage = ServiceCoverage.of(serviceIds == null ? null : new LinkedHashSet<>(serviceIds));
        } else if (serviceIds != null && !serviceIds.isEmpty()) {
            throw new DomainException("serviceIds", "serviceIds are accepted only with serviceCoverage SELECTED");
        } else {
            coverage = ServiceCoverage.all();
        }
        return new CreateDiscountRule.Command(name, validFrom, validTo, priority, calculationBase,
                percentageBasisPoints, amountCents, coverage, capCents);
    }
}
