package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// Ordinary data only: never the risk data, and tenantId is never exposed. Declared limits: the contract required
// highRisk is not answered because the registered serviceId is not stored, and termsAccepted reads true because a
// customer cannot be registered without accepting (the table has no column for it).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(UUID customerId, String fullName, String documentNumber, String birthDate, Integer age,
        Contact contact, boolean termsAccepted, String createdAt) {
    public record Contact(String email, String phone) {}

    public static CustomerResponse from(Customer customer) {
        var birth = customer.birthDateOrAge();
        return new CustomerResponse(customer.customerId(), customer.fullName(), customer.documentNumber().value(),
                birth.birthDate() == null ? null : birth.birthDate().toString(), birth.age(),
                new Contact(customer.contact().email(), customer.contact().phone()), customer.termsAccepted(),
                customer.createdAt().toString());
    }
}
