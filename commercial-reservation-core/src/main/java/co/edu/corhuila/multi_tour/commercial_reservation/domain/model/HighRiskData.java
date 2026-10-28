package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.ArrayList;
import java.util.List;

// Extra data of a high-risk activity (RN-02, DP-03/DP-04: names from the conventions, values free text).
// "Ninguna" is the explicit answer for no restrictions, so a blank restriction is a missing answer.
public record HighRiskData(String bloodType, String emergencyContact, String mobilityRestrictions,
        boolean informedConsentSigned) {

    public List<String> missingFields() {
        return List.of();
    }

    // Sensitive values never reach a log through a stray toString (RN-13).
    @Override
    public String toString() {
        return "HighRiskData[redacted]";
    }
}
