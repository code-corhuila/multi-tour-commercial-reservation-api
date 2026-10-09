package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

// Field binding leaves omissions untouched while rejecting explicit nulls before either intent runs.
public final class UpdateCatalogServiceRequest {
    @JsonSetter(nulls = Nulls.FAIL) public String commercialName;
    @JsonSetter(nulls = Nulls.FAIL) public Long baseRateCents;
    @JsonSetter(nulls = Nulls.FAIL) public Long baseOperationalCostCents;
    @JsonSetter(nulls = Nulls.FAIL) public Integer configuredCapacity;
    @JsonSetter(nulls = Nulls.FAIL) public Boolean transportRequired;
    @JsonSetter(nulls = Nulls.FAIL) public Boolean highRisk;

    boolean hasTerms() {
        return baseRateCents != null || baseOperationalCostCents != null || configuredCapacity != null
                || transportRequired != null || highRisk != null;
    }
}
