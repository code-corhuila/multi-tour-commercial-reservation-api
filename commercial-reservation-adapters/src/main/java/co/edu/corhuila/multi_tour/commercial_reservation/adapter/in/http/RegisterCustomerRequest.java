package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.time.LocalDate;
import java.util.UUID;

// Boxed values keep absence, so the domain names the missing field. Field names are PROPOSED (contract x-proposed).
public record RegisterCustomerRequest(String fullName, String documentNumber, LocalDate birthDate, Integer age,
        Contact contact, Boolean termsAccepted, UUID serviceId, Boolean informedConsentSigned,
        EmergencyInfo emergencyInfo) {
    public record Contact(String email, String phone) {}
    public record EmergencyInfo(String bloodType, String emergencyContact, String mobilityRestrictions) {}

    public RegisterLeadCustomer.Command toCommand() {
        boolean signed = Boolean.TRUE.equals(informedConsentSigned);
        // Whether the activity is high risk is the catalog decision; the use case checks the data against it.
        HighRiskData risk = emergencyInfo == null && !signed ? null : new HighRiskData(
                emergencyInfo == null ? null : emergencyInfo.bloodType(),
                emergencyInfo == null ? null : emergencyInfo.emergencyContact(),
                emergencyInfo == null ? null : emergencyInfo.mobilityRestrictions(), signed);
        return new RegisterLeadCustomer.Command(serviceId, documentNumber, fullName, birthDate, age,
                contact == null ? null : contact.email(), contact == null ? null : contact.phone(),
                Boolean.TRUE.equals(termsAccepted), risk);
    }
}
