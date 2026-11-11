package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.LocalDate;

// JSON merge of a rule (AC-03.16): an absent field keeps its value; a present null clears it (only the cap may be cleared).
public record DiscountRulePatch(Field<String> name, Field<LocalDate> validFrom, Field<LocalDate> validTo,
        Field<Integer> priority, Field<DiscountBase> calculationBase, Field<Integer> percentageBasisPoints,
        Field<Long> amountCents, Field<ServiceCoverage> serviceCoverage, Field<Long> capCents, Field<Boolean> stackable) {

    public record Field<T>(boolean present, T value) {
        public static <T> Field<T> absent() { return new Field<>(false, null); }
        public static <T> Field<T> of(T value) { return new Field<>(true, value); }
        T or(T current) { return present ? value : current; }
    }

    public static DiscountRulePatch none() {
        return new DiscountRulePatch(Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent(), Field.absent());
    }

    public DiscountRulePatch withName(String v) { return new DiscountRulePatch(Field.of(v), validFrom, validTo, priority, calculationBase, percentageBasisPoints, amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withValidFrom(LocalDate v) { return new DiscountRulePatch(name, Field.of(v), validTo, priority, calculationBase, percentageBasisPoints, amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withValidTo(LocalDate v) { return new DiscountRulePatch(name, validFrom, Field.of(v), priority, calculationBase, percentageBasisPoints, amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withPriority(Integer v) { return new DiscountRulePatch(name, validFrom, validTo, Field.of(v), calculationBase, percentageBasisPoints, amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withCalculationBase(DiscountBase v) { return new DiscountRulePatch(name, validFrom, validTo, priority, Field.of(v), percentageBasisPoints, amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withPercentageBasisPoints(Integer v) { return new DiscountRulePatch(name, validFrom, validTo, priority, calculationBase, Field.of(v), amountCents, serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withAmountCents(Long v) { return new DiscountRulePatch(name, validFrom, validTo, priority, calculationBase, percentageBasisPoints, Field.of(v), serviceCoverage, capCents, stackable); }
    public DiscountRulePatch withServiceCoverage(ServiceCoverage v) { return new DiscountRulePatch(name, validFrom, validTo, priority, calculationBase, percentageBasisPoints, amountCents, Field.of(v), capCents, stackable); }
    public DiscountRulePatch withCapCents(Long v) { return new DiscountRulePatch(name, validFrom, validTo, priority, calculationBase, percentageBasisPoints, amountCents, serviceCoverage, Field.of(v), stackable); }
    public DiscountRulePatch withStackable(Boolean v) { return new DiscountRulePatch(name, validFrom, validTo, priority, calculationBase, percentageBasisPoints, amountCents, serviceCoverage, capCents, Field.of(v)); }
}
