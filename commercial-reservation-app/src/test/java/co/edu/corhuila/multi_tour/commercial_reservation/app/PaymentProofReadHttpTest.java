package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-COM-010 over HTTP, the reads (api spec T-02, T-05; AC-10.4, AC-10.6, AC-10.14, AC-10.17, AC-10.19): the list with the 24 hour mark,
// one proof, the file read through the API with no-store, and the overdue list of job 2 for a service token only.
class PaymentProofReadHttpTest extends ReservationHttpBase {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private final UUID recent = new UUID(20, 1), old = new UUID(20, 2), recentReservation = new UUID(20, 3), oldReservation = new UUID(20, 4);

    @BeforeEach void proofs() {
        plant(recentReservation, recent, Duration.ofHours(2));
        plant(oldReservation, old, Duration.ofHours(30));
    }

    private void plant(UUID reservationId, UUID attemptId, Duration age) {
        Reservation reservation = Reservation.create(reservationId, tenant, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(age));
        reservation.registerTransfer(attemptId, new Cents(90_000), new Cents(90_000), customer, NOW.minus(age));
        reservation.pullEvents();
        store.reservations.put(reservationId, reservation);
        payments.proofs.put(attemptId, co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile.of(PNG));
    }

    @Test void theListCarriesTheMarkAndTheCustomerNameAndTheOverdueCountForEveryReadingRole() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) {
            var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-proofs"), role), 200));
            assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
            assertThat(page.get("overdueCount").asInt()).isEqualTo(1);
            assertThat(page.at("/data/0/paymentId").asText()).isEqualTo(old.toString());
            assertThat(page.at("/data/0/reviewOverdue").asBoolean()).isTrue();
            assertThat(page.at("/data/1/reviewOverdue").asBoolean()).isFalse();
            assertThat(page.at("/data/0/customerName").asText()).isEqualTo("Persona Ficticia");
            assertThat(page.at("/data/0/declaredAmountCents").asLong()).isEqualTo(90_000L);
            assertThat(page.at("/data/0/validationStatus").asText()).isEqualTo("IN_VALIDATION");
        }
        var overdue = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-proofs").param("overdue", "true"), ADMIN), 200));
        assertThat(overdue.get("data")).hasSize(1);
        assertThat(CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-proofs").param("status", "APPROVED"), ADMIN), 200)).get("data")).isEmpty();
    }

    @Test void theListRefusesTheFinalCustomerAndWhatItDoesNotKnow() throws Exception {
        exchange(withToken(get(BASE + "/payment-proofs"), FINAL_CUSTOMER), 403);
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/payment-proofs").param("status", "LOST"), ADMIN), 400), "status");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/payment-proofs").param("overdue", "maybe"), ADMIN), 400), "overdue");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/payment-proofs").param("page", "0"), ADMIN), 400), "page");
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/payment-proofs").param("sort", "x"), ADMIN), 400), "sort");
    }

    @Test void oneProofIsReadWithoutTheFileAndAnotherTenantsLooksMissing() throws Exception {
        var proof = CatalogHttpTest.json(exchange(withToken(get(BASE + "/payment-proofs/" + recent), ANALYST), 200));

        assertThat(proof.get("paymentId").asText()).isEqualTo(recent.toString());
        assertThat(proof.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(proof.get("reviewOverdue").asBoolean()).isFalse();
        assertThat(proof.toString()).doesNotContain("content", "sha256");
        exchange(withToken(get(BASE + "/payment-proofs/" + new UUID(20, 99)), ADMIN), 404);
        exchange(withToken(get(BASE + "/payment-proofs/" + recent), FINAL_CUSTOMER), 403);
    }

    @Test void theFileIsServedThroughTheApiWithNoStoreToWhoMayDecide() throws Exception {
        var result = exchange(withToken(get(BASE + "/payment-proofs/" + recent + "/file"), ADMIN), 200);

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(result.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        exchange(withToken(get(BASE + "/payment-proofs/" + recent + "/file"), COLLABORATOR), 403);
        exchange(withToken(get(BASE + "/payment-proofs/" + recent + "/file"), ACCOUNTANT), 403);
        exchange(withToken(get(BASE + "/payment-proofs/" + new UUID(20, 99) + "/file"), ADMIN), 404);
        var granted = get(BASE + "/payment-proofs/" + recent + "/file")
                .header("Authorization", "Bearer " + CustomerHttpTest.token(tenant.toString(), COLLABORATOR, List.of("VALIDATE_PAYMENT_PROOFS")))
                .header("X-Correlation-Id", TRACE);
        exchange(granted, 200);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder internal(UUID forTenant, String bearer, String overdue) {
        var request = get(BASE + "/internal/tenants/" + forTenant + "/payment-proofs").header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", TRACE);
        return overdue == null ? request : request.param("overdue", overdue);
    }

    @Test void job2ReadsTheOverdueProofsOfATenantWithAServiceTokenOnly() throws Exception {
        String service = CustomerHttpTest.token(null, "SERVICE", List.of());

        var page = CatalogHttpTest.json(exchange(internal(tenant, service, "true"), 200));

        assertThat(page.get("data")).hasSize(1);
        assertThat(page.at("/data/0/paymentId").asText()).isEqualTo(old.toString());
        assertThat(CatalogHttpTest.json(exchange(internal(otherTenant, service, "true"), 200)).get("data")).isEmpty();
        exchange(internal(tenant, token(ADMIN), "true"), 403);
        CatalogHttpTest.assertField(exchange(internal(tenant, service, null), 400), "overdue");
        CatalogHttpTest.assertField(exchange(internal(tenant, service, "false"), 400), "overdue");
    }
}
