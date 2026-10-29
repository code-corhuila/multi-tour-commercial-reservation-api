package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.util.UUID;

// Risk data of a customer: only the audited route builds it, and it is never logged, cached or sent in events.
public record CustomerSensitiveDataResponse(UUID customerId, EmergencyInfo emergencyInfo, boolean informedConsentSigned) {
    public record EmergencyInfo(String bloodType, String emergencyContact, String mobilityRestrictions) {}

    public static CustomerSensitiveDataResponse from(UUID customerId, HighRiskData data) {
        return new CustomerSensitiveDataResponse(customerId, new EmergencyInfo(data.bloodType(),
                data.emergencyContact(), data.mobilityRestrictions()), data.informedConsentSigned());
    }
}
