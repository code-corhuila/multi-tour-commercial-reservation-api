package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Everything only the Tenant Administrator may change (plan D-03), kept apart from descriptive information.
// transportRatePerPerson is the optional per-person transport rate (RN-10, RN-TRA-002): null means not parametrized.
public record CommercialTerms(Cents baseRate, Cents baseOperationalCost, Integer configuredCapacity,
        boolean transportRequired, boolean highRisk, Cents transportRatePerPerson) {

    public CommercialTerms(Cents baseRate, Cents baseOperationalCost, Integer configuredCapacity,
            boolean transportRequired, boolean highRisk) {
        this(baseRate, baseOperationalCost, configuredCapacity, transportRequired, highRisk, null);
    }

    public static CommercialTerms of(Long baseRateCents, Long baseOperationalCostCents, Integer configuredCapacity,
            boolean transportRequired, boolean highRisk) {
        return of(baseRateCents, baseOperationalCostCents, configuredCapacity, transportRequired, highRisk, null);
    }

    public static CommercialTerms of(Long baseRateCents, Long baseOperationalCostCents, Integer configuredCapacity,
            boolean transportRequired, boolean highRisk, Long transportRatePerPersonCents) {
        if (configuredCapacity != null && configuredCapacity < 1) {
            throw new DomainException("configuredCapacity", "configuredCapacity must be at least 1");
        }
        return new CommercialTerms(Cents.of("baseRateCents", baseRateCents),
                Cents.of("baseOperationalCostCents", baseOperationalCostCents), configuredCapacity,
                transportRequired, highRisk,
                transportRatePerPersonCents == null ? null
                        : Cents.of("transportRatePerPersonCents", transportRatePerPersonCents));
    }
}
