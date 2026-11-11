package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// A null component means "leave as is": the contract allows a partial update of the commercial terms.
public record CommercialTermsChange(Long baseRateCents, Long baseOperationalCostCents, Integer configuredCapacity,
        Boolean transportRequired, Boolean highRisk) {
}
