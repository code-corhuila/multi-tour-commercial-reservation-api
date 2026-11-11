package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// T-06 of HU-COM-014 (AC-14.3, AC-14.4, AC-14.5, AC-14.11, AC-14.12; RN-01, RN-02, RN-09, RN-10): the Final Customer reads only their own,
// writes nothing in this area, every list is paginated and the logs carry no personal datum. The routes already exist; these are the
// tests that fix the rules for the consultation.
@ExtendWith(OutputCaptureExtension.class)
class ConsultationRefusalsHttpTest extends ReservationHttpBase {

    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), STRANGER_ACCOUNT = new UUID(0, 0x14a),
            STRANGER = new UUID(14, 50), ATTEMPT = new UUID(14, 60);

    @BeforeEach void linkTheCustomers() {
        customers.save(customers.customers.get(customer).linkedTo(ACCOUNT));
        customers.save(Customer.registerLead(STRANGER, tenant, "CC99999", "Otra Persona Ficticia", BirthDateOrAge.ofAge(40),
                new ContactInfo("otra@example.invalid", "3000000001"), true, NOW).linkedTo(STRANGER_ACCOUNT));
    }

    private String tokenOf(UUID account, String tenantId) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("tenantId", tenantId); claims.put("roles", java.util.List.of(FINAL_CUSTOMER));
        return TestJwtKeys.token(java.util.Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token).header("X-Correlation-Id", TRACE);
    }

    private String createOwn(String key) throws Exception {
        var body = createBody(); body.remove("customerId");
        return CatalogHttpTest.json(exchange(create(body, key, FINAL_CUSTOMER), 201)).get("reservationId").asText();
    }

    @Test void theFinalCustomerCannotWriteOnTheirOwnReservationAndNothingChanges() throws Exception {
        String id = createOwn("reservation-key-146");
        int saves = store.saves;
        String[] writes = {"/reservations/" + id + "/cancellation", "/reservations/" + id + "/payment-deadline/extension",
                "/reservations/" + id + "/payment-followups", "/reservations/" + id + "/additional-discount",
                "/payment-proofs/" + ATTEMPT + "/approve", "/payment-proofs/" + ATTEMPT + "/reject"};
        for (String route : writes) {
            exchange(withToken(post(BASE + route), FINAL_CUSTOMER).contentType(MediaType.APPLICATION_JSON).content("{}")
                    .header("Idempotency-Key", "refused-key-0001"), 403);
        }
        assertThat(store.saves).isEqualTo(saves);
        assertThat(store.reservations.get(UUID.fromString(id)).reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test void anotherCustomerOfTheSameTenantAndAnotherTenantReadNothingOfTheReservation() throws Exception {
        String id = createOwn("reservation-key-147");
        store.reservations.get(UUID.fromString(id)).registerTransfer(ATTEMPT, new Cents(100_000), new Cents(100_000), STRANGER, NOW);
        for (String stranger : new String[] {tokenOf(STRANGER_ACCOUNT, TestJwtKeys.TENANT), tokenOf(ACCOUNT, otherTenant.toString())}) {
            exchange(as(stranger, get(BASE + "/reservations/" + id)), 404);
            exchange(as(stranger, get(BASE + "/reservations/" + id + "/payments")), 404);
            exchange(as(stranger, get(BASE + "/reservations/" + id + "/payments/" + ATTEMPT)), 404);
            assertThat(CatalogHttpTest.json(exchange(as(stranger, get(BASE + "/reservations?scope=all")), 200)).at("/meta/total").asInt()).isZero();
        }
    }

    @Test void everyListIsPaginatedWithTheSharedEnvelopeAndRefusesAnInvalidPage() throws Exception {
        for (int index = 0; index < 3; index++) createOwn("reservation-key-15" + index);
        var second = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations?page=2&limit=2"), FINAL_CUSTOMER), 200));
        assertThat(second.get("data")).hasSize(1);
        assertThat(second.at("/meta/page").asInt()).isEqualTo(2);
        assertThat(second.at("/meta/limit").asInt()).isEqualTo(2);
        assertThat(second.at("/meta/total").asInt()).isEqualTo(3);
        assertThat(second.at("/meta/totalPages").asInt()).isEqualTo(2);
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations?limit=101"), FINAL_CUSTOMER), 400), "limit");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations?page=0"), FINAL_CUSTOMER), 400), "page");
        String id = createOwn("reservation-key-156");
        store.reservations.get(UUID.fromString(id)).registerTransfer(ATTEMPT, new Cents(100_000), new Cents(100_000), STRANGER, NOW);
        var payments = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id + "/payments?limit=1"), FINAL_CUSTOMER), 200));
        assertThat(payments.at("/meta/total").asInt()).isEqualTo(1);
        assertThat(payments.at("/meta/limit").asInt()).isEqualTo(1);
    }

    @Test void aReadEmitsNoEventAndTheLogsCarryNoPersonalData(CapturedOutput output) throws Exception {
        String id = createOwn("reservation-key-157");
        int saves = store.saves;
        exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200);
        exchange(withToken(get(BASE + "/reservations/" + id), FINAL_CUSTOMER), 200);
        exchange(withToken(get(BASE + "/reservations/" + id + "/payments"), FINAL_CUSTOMER), 200);
        exchange(as(tokenOf(STRANGER_ACCOUNT, TestJwtKeys.TENANT), get(BASE + "/reservations/" + id)), 404);
        assertThat(store.saves).isEqualTo(saves);
        assertThat(payments.events).isEmpty();
        assertThat(output.getAll()).doesNotContain("Persona Ficticia", "persona@example.invalid", "CC12345", "3000000000", "Otra Persona");
    }
}
