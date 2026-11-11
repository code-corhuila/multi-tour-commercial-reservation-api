package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingReservationDataException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Closed request: the body carries no value, tenant, state or creator (RN-13), and the strict mapper refuses them.
// Boxed values keep absence, so the core names every missing field at once (AC-07.2). The holder is the new lead customer sent
// inline, the alternative to customerId; for a Final Customer whose account has no record it is required (AC-08.13).
public record CreateReservationRequest(UUID customerId, UUID serviceId, UUID salidaId, Integer people, String paymentType,
        String paymentMethod, List<CompanionRequest> companions, HolderRequest holder) {

    // Same fields and validations as RegisterCustomerRequest without serviceId: the risk mark is the tour's, never typed.
    public record HolderRequest(String fullName, String documentNumber, LocalDate birthDate, Integer age,
            RegisterCustomerRequest.Contact contact, Boolean termsAccepted, Boolean informedConsentSigned,
            RegisterCustomerRequest.EmergencyInfo emergencyInfo) {

        RegisterLeadCustomer.Command toCommand() {
            return new RegisterCustomerRequest(fullName, documentNumber, birthDate, age, contact, termsAccepted, null,
                    informedConsentSigned, emergencyInfo).toCommand();
        }
    }

    public record CompanionRequest(String fullName, String documentNumber, LocalDate birthDate, Integer age,
            Boolean informedConsentSigned, RegisterCustomerRequest.EmergencyInfo emergencyInfo) {

        // Every missing or invalid datum is named at once with the position of the companion (AC-07.2, AC-05.4, AC-05.13).
        Companion toCompanion(int position, LocalDate today) {
            String prefix = "companions[" + position + "].";
            var missing = new ArrayList<String>();
            if (fullName == null || fullName.isBlank()) missing.add(prefix + "fullName");
            if (documentNumber == null || documentNumber.isBlank()) missing.add(prefix + "documentNumber");
            if (birthDate != null && birthDate.isAfter(today))
                throw new DomainException(prefix + "birthDate", "birthDate must not be in the future");
            if (birthDate == null && age == null) missing.add(prefix + "birthDateOrAge");
            if (!missing.isEmpty()) throw new MissingReservationDataException(missing);
            boolean signed = Boolean.TRUE.equals(informedConsentSigned);
            HighRiskData risk = emergencyInfo == null && !signed ? null : new HighRiskData(
                    emergencyInfo == null ? null : emergencyInfo.bloodType(),
                    emergencyInfo == null ? null : emergencyInfo.emergencyContact(),
                    emergencyInfo == null ? null : emergencyInfo.mobilityRestrictions(), signed);
            BirthDateOrAge born = birthDate != null ? BirthDateOrAge.ofBirthDate(birthDate, today) : ageOf(prefix);
            return new Companion(fullName, DocumentNumber.of(documentNumber), born, risk);
        }

        private BirthDateOrAge ageOf(String prefix) {
            if (age <= 0) throw new DomainException(prefix + "age", "age must be positive");
            return BirthDateOrAge.ofAge(age);
        }
    }

    public CreateReservationCommand toCommand(UUID tenantId, UUID actorId, LocalDate today) {
        List<Companion> group = new ArrayList<>();
        // An empty element is a companion with every datum missing, so it is named with its position (AC-05.4), never a 500.
        if (companions != null) for (int i = 0; i < companions.size(); i++) {
            var item = companions.get(i) == null ? new CompanionRequest(null, null, null, null, null, null) : companions.get(i);
            group.add(item.toCompanion(i, today));
        }
        return new CreateReservationCommand(tenantId, actorId, customerId, serviceId, salidaId, people == null ? 0 : people,
                paymentType, paymentMethod, group, null, holder == null ? null : holder.toCommand());
    }

    // The door of a Final Customer: the customer is the one of the account, so a customerId in the body is refused (AC-08.9).
    public CreateReservationCommand toOwnCommand(UUID tenantId, UUID accountId, LocalDate today) {
        var command = toCommand(tenantId, accountId, today);
        return new CreateReservationCommand(command.tenantId(), command.actorId(), command.customerId(), command.serviceId(),
                command.departureId(), command.people(), command.paymentType(), command.paymentMethod(), command.companions(), accountId,
                command.holder());
    }
}
