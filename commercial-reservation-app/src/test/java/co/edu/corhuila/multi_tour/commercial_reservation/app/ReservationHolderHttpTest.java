package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-007 / HU-COM-005 inline holder over HTTP (contract CreateReservationRequest.holder): one request, one idempotency key,
// one transaction; exactly one of customerId and holder; the e-mail rules of CA-024 and AC-05.7; AC-08.13 for the Final Customer.
class ReservationHolderHttpTest extends ReservationHttpBase {
    private static final String NEW_EMAIL = "nueva.persona@example.invalid";

    private Map<String, Object> holder(String email) {
        Map<String, Object> holder = new LinkedHashMap<>();
        holder.put("fullName", "Titular Ficticio"); holder.put("documentNumber", "cc 998877"); holder.put("age", 35);
        holder.put("contact", Map.of("email", email, "phone", "3001234567")); holder.put("termsAccepted", true);
        return holder;
    }

    private Map<String, Object> bodyWith(Map<String, Object> holder) {
        var body = createBody(); body.remove("customerId"); body.put("holder", holder);
        return body;
    }

    @Test void aHolderIsRegisteredWithTheReservationAndTheAnswerCarriesNoSensitiveData() throws Exception {
        var answer = CatalogHttpTest.json(exchange(create(bodyWith(holder("  Nueva.Persona@Example.INVALID ")), "holder-key-001", ADMIN), 201));

        var stored = customers.customers.get(UUID.fromString(answer.get("customerId").asText()));
        assertThat(stored.tenantId()).isEqualTo(tenant);
        assertThat(stored.contact().email()).isEqualTo(NEW_EMAIL);
        assertThat(store.reservations.values()).singleElement().satisfies(found -> assertThat(found.customerId()).isEqualTo(stored.customerId()));
        assertThat(customers.registered).singleElement().satisfies(event -> assertThat(event.customerId()).isEqualTo(stored.customerId()));
        assertThat(answer.toString()).doesNotContain("Titular Ficticio").doesNotContain("998877").doesNotContain(NEW_EMAIL).doesNotContain("3001234567");
    }

    @Test void aCollaboratorCanSendTheHolderToo() throws Exception {
        exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-002", COLLABORATOR), 201);
        assertThat(customers.customers).hasSize(2);
    }

    @Test void theSameKeyAnswersTheOriginalAndRegistersNothingMore() throws Exception {
        var first = CatalogHttpTest.json(exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-003", ADMIN), 201));
        var repeat = CatalogHttpTest.json(exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-003", ADMIN), 200));

        assertThat(repeat).isEqualTo(first);
        assertThat(customers.customers).hasSize(2);
        assertThat(customers.registered).hasSize(1);
        assertThat(store.reservations).hasSize(1);
    }

    @Test void theSameKeyWithAnotherHolderIsARuleViolationNotAReplay() throws Exception {
        exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-004", ADMIN), 201);
        var other = holder("otra.persona@example.invalid"); other.put("documentNumber", "CC 111222");

        var error = CatalogHttpTest.json(exchange(create(bodyWith(other), "holder-key-004", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(store.reservations).hasSize(1);
    }

    @Test void bothACustomerAndAHolderAreRefusedNamingTheHolder() throws Exception {
        var body = bodyWith(holder(NEW_EMAIL)); body.put("customerId", customer.toString());
        CatalogHttpTest.assertField(exchange(create(body, "holder-key-005", ADMIN), 400), "holder");
        assertThat(store.reservations).isEmpty();
    }

    @Test void neitherACustomerNorAHolderIsRefusedNamingTheCustomer() throws Exception {
        var body = createBody(); body.remove("customerId");
        CatalogHttpTest.assertField(exchange(create(body, "holder-key-006", ADMIN), 400), "customerId");
    }

    @Test void aServiceInsideTheHolderIsRefusedAsTheHolderHasNoTour() throws Exception {
        var withTour = holder(NEW_EMAIL); withTour.put("serviceId", tour.toString());
        exchange(create(bodyWith(withTour), "holder-key-007", ADMIN), 400);
        assertThat(store.reservations).isEmpty();
        assertThat(customers.customers).hasSize(1);
    }

    @Test void aHolderWithoutAcceptedTermsOrWithAMissingDatumIsRefusedAndNothingIsStored() throws Exception {
        var noTerms = holder(NEW_EMAIL); noTerms.put("termsAccepted", false);
        CatalogHttpTest.assertField(exchange(create(bodyWith(noTerms), "holder-key-008", ADMIN), 400), "termsAccepted");
        var noName = holder(NEW_EMAIL); noName.remove("fullName");
        CatalogHttpTest.assertField(exchange(create(bodyWith(noName), "holder-key-009", ADMIN), 400), "fullName");
        assertThat(store.reservations).isEmpty();
        assertThat(customers.customers).hasSize(1);
        assertThat(customers.registered).isEmpty();
    }

    @Test void theEmailOfAnUnverifiedRecordIsRefusedNamingTheEmailAndNothingIsStored() throws Exception {
        var error = CatalogHttpTest.json(exchange(create(bodyWith(holder("persona@example.invalid")), "holder-key-010", ADMIN), 422));

        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("persona@example.invalid");
        assertThat(store.reservations).isEmpty();
        assertThat(customers.customers).hasSize(1);
    }

    @Test void theEmailOfAVerifiedAccountLinksTheExistingCustomerWithoutDuplicatingIt() throws Exception {
        customers.save(customers.customers.get(customer).linkedTo(new UUID(0, 0x999)));
        var answer = CatalogHttpTest.json(exchange(create(bodyWith(holder("Persona@Example.invalid")), "holder-key-011", ADMIN), 201));

        assertThat(answer.get("customerId").asText()).isEqualTo(customer.toString());
        assertThat(customers.customers).hasSize(1);
        assertThat(customers.registered).isEmpty();
    }

    @Test void aFinalCustomerWithoutARecordRegistersTheHolderLinkedToTheAccount() throws Exception {
        var answer = CatalogHttpTest.json(exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-012", FINAL_CUSTOMER), 201));

        var stored = customers.customers.get(UUID.fromString(answer.get("customerId").asText()));
        assertThat(stored.accountId()).isEqualTo(UUID.fromString(TestJwtKeys.ACCOUNT));
        assertThat(store.reservations.values()).singleElement().satisfies(found -> assertThat(found.createdBy()).isEqualTo(stored.accountId()));
    }

    @Test void aFinalCustomerWhoseAccountHasARecordSendsNoHolder() throws Exception {
        customers.save(customers.customers.get(customer).linkedTo(UUID.fromString(TestJwtKeys.ACCOUNT)));
        CatalogHttpTest.assertField(exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-013", FINAL_CUSTOMER), 400), "holder");
        assertThat(store.reservations).isEmpty();
    }

    @Test void theRolesThatCannotCreateStillAnswer403WithAHolder() throws Exception {
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST)) {
            exchange(create(bodyWith(holder(NEW_EMAIL)), "holder-key-014", role), 403);
        }
        assertThat(customers.customers).hasSize(1);
    }
}
