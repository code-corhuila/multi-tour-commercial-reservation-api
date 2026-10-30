package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Closed request: the body carries no value, tenant, state or creator (RN-13), and the strict mapper refuses them.
// Boxed values keep absence, so the core names every missing field at once (AC-07.2). The holder of a Final Customer waits for HU-COM-008.
public record CreateReservationRequest(UUID customerId, UUID serviceId, UUID salidaId, Integer people, String paymentType,
        String paymentMethod, List<CompanionRequest> companions) {

    public record CompanionRequest(String fullName, String documentNumber, LocalDate birthDate, Integer age,
            Boolean informedConsentSigned, RegisterCustomerRequest.EmergencyInfo emergencyInfo) {

        Companion toCompanion(LocalDate today) {
            boolean signed = Boolean.TRUE.equals(informedConsentSigned);
            HighRiskData risk = emergencyInfo == null && !signed ? null : new HighRiskData(
                    emergencyInfo == null ? null : emergencyInfo.bloodType(),
                    emergencyInfo == null ? null : emergencyInfo.emergencyContact(),
                    emergencyInfo == null ? null : emergencyInfo.mobilityRestrictions(), signed);
            BirthDateOrAge born = birthDate != null ? BirthDateOrAge.ofBirthDate(birthDate, today)
                    : age != null ? BirthDateOrAge.ofAge(age) : null;
            if (born == null) throw new DomainException("companions.birthDateOrAge", "birthDate or age is required");
            return new Companion(fullName, DocumentNumber.of(documentNumber), born, risk);
        }
    }

    public CreateReservationCommand toCommand(UUID tenantId, UUID actorId, LocalDate today) {
        List<Companion> group = companions == null ? List.of() : companions.stream().map(item -> item.toCompanion(today)).toList();
        return new CreateReservationCommand(tenantId, actorId, customerId, serviceId, salidaId, people == null ? 0 : people,
                paymentType, paymentMethod, group);
    }
}
