package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Everything only the Tenant Administrator may change (plan D-03), kept apart from descriptive information.
public record CommercialTerms(Cents baseRate, Cents baseOperationalCost, Integer configuredCapacity,
        boolean transportRequired, boolean highRisk) {

    public static CommercialTerms of(Long baseRateCents, Long baseOperationalCostCents, Integer configuredCapacity,
            boolean transportRequired, boolean highRisk) {
        if (configuredCapacity != null && configuredCapacity < 1) {
            throw new DomainException("configuredCapacity", "configuredCapacity must be at least 1");
        }
        return new CommercialTerms(Cents.of("baseRateCents", baseRateCents),
                Cents.of("baseOperationalCostCents", baseOperationalCostCents), configuredCapacity,
                transportRequired, highRisk);
    }
}
