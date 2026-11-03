package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// Ordinary data only: never the risk data, and tenantId is never exposed. Declared limits: the contract field
// highRisk is answered only by the registration (decision 2026-10-29); a later read omits it. termsAccepted is the stored acceptance;
// its moment (terms_accepted_at) is persisted but not exposed: the contract has no acceptedAt (open decision).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerResponse(UUID customerId, String fullName, String documentNumber, String birthDate, Integer age,
        Contact contact, boolean termsAccepted, String createdAt, Boolean highRisk) {
    public record Contact(String email, String phone) {}

    public static CustomerResponse from(Customer customer) {
        var birth = customer.birthDateOrAge();
        return new CustomerResponse(customer.customerId(), customer.fullName(), customer.documentNumber().value(),
                birth.birthDate() == null ? null : birth.birthDate().toString(), birth.age(),
                new Contact(customer.contact().email(), customer.contact().phone()), customer.termsAccepted(),
                customer.createdAt().toString(), customer.highRisk());
    }
}
