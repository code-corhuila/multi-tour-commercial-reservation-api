package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Ownership of the payment route for a Final Customer (audit M1; HU-COM-009 AC-09.5, HU-COM-008 own scope): PaymentController.register resolves the own reservation
// before anything is stored, so the reservation of another customer of the same tenant, or of another tenant, looks missing and nothing is written. Runs without a database.
class PaymentOwnershipHttpTest extends ReservationHttpBase {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final UUID OWNER_ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), SECOND_ACCOUNT = new UUID(0, 0x4A1), SECOND_CUSTOMER = new UUID(1, 0x4A2),
            FOREIGN_CUSTOMER = new UUID(1, 0x4A3);

    @BeforeEach void linkTheCustomers() {
        customers.save(customers.customers.get(customer).linkedTo(OWNER_ACCOUNT));
        customers.save(Customer.registerLead(SECOND_CUSTOMER, tenant, "CC2222", "Segunda Persona", BirthDateOrAge.ofAge(41),
                new ContactInfo("segunda@example.invalid", "3000000002"), true, NOW).linkedTo(SECOND_ACCOUNT));
        // The same account number linked in another tenant: the tenant of the token, not the account, decides what is visible.
        directory.tenants.put(otherTenant.toString(), new TenantRef(otherTenant, true));
        customers.save(Customer.registerLead(FOREIGN_CUSTOMER, otherTenant, "CC3333", "Persona de Otro Tenant", BirthDateOrAge.ofAge(35),
                new ContactInfo("ajena@example.invalid", "3000000003"), true, NOW).linkedTo(OWNER_ACCOUNT));
    }

    private String reservationOfTheOwner() throws Exception {
        return CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-" + UUID.randomUUID(), ADMIN), 201)).get("reservationId").asText();
    }

    private MockHttpServletRequestBuilder transferAs(UUID account, UUID tenantId, String reservation, String key) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("tenantId", tenantId.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
        return multipart(BASE + "/reservations/" + reservation + "/payments")
                .file(new MockMultipartFile("payment", "", "application/json",
                        TestJwtKeys.JSON.writeValueAsBytes(Map.of("paymentMethod", "TRANSFER", "amountCents", 90_000L))))
                .file(new MockMultipartFile("proof", "proof.png", "image/png", PNG))
                .header("Authorization", "Bearer " + token).header("X-Correlation-Id", TRACE).header("Idempotency-Key", key);
    }

    @Test void aFinalCustomerRegistersATransferOnTheirOwnReservation() throws Exception {
        String id = reservationOfTheOwner();

        var body = CatalogHttpTest.json(exchange(transferAs(OWNER_ACCOUNT, tenant, id, "own-payment-0001"), 201));

        assertThat(body.get("validationStatus").asText()).isEqualTo("IN_VALIDATION");
        assertThat(body.get("registeredBy").asText()).isEqualTo(OWNER_ACCOUNT.toString());
        assertThat(store.reservations.values().iterator().next().attempts()).hasSize(1);
        assertThat(payments.proofs).hasSize(1);
    }

    @Test void anotherCustomerOfTheSameTenantCannotPayTheReservationAndItLooksMissing() throws Exception {
        String id = reservationOfTheOwner();

        var refused = CatalogHttpTest.json(exchange(transferAs(SECOND_ACCOUNT, tenant, id, "second-payment-0001"), 404));

        assertThat(refused.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(refused.toString()).doesNotContain(id, customer.toString());
        assertThat(store.reservations.values().iterator().next().attempts()).isEmpty();
        assertThat(payments.proofs).isEmpty();
        assertThat(payments.events).isEmpty();
    }

    @Test void theRefusalForAnotherCustomerIsTheSameAsForAReservationThatDoesNotExist() throws Exception {
        String id = reservationOfTheOwner();

        var foreign = CatalogHttpTest.json(exchange(transferAs(SECOND_ACCOUNT, tenant, id, "second-payment-0002"), 404));
        var missing = CatalogHttpTest.json(exchange(transferAs(SECOND_ACCOUNT, tenant, new UUID(1, 0x4A9).toString(), "second-payment-0003"), 404));

        assertThat(foreign.get("error")).isEqualTo(missing.get("error"));
        assertThat(foreign.get("message").asText().replace(id, "<id>")).isEqualTo(missing.get("message").asText().replace(new UUID(1, 0x4A9).toString(), "<id>"));
    }

    @Test void aFinalCustomerOfAnotherTenantCannotPayTheReservationAndItLooksMissing() throws Exception {
        String id = reservationOfTheOwner();

        var refused = CatalogHttpTest.json(exchange(transferAs(OWNER_ACCOUNT, otherTenant, id, "foreign-payment-0001"), 404));

        assertThat(refused.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(store.reservations.values().iterator().next().attempts()).isEmpty();
        assertThat(payments.proofs).isEmpty();
        assertThat(payments.events).isEmpty();
    }
}
