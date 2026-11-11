package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (ADR-004 D5): the token of an exceptional or emergency access is read only, reaches a route only inside its scope, never the internal routes, and anything not listed is denied.
class ExceptionalAccessPolicyTest {
    private static final String BASE = "/api/v1/commercial-reservation";
    private static final String ID = UUID.randomUUID().toString();

    private static Optional<String> area(String method, String path) {
        return ExceptionalAccessPolicy.areaOf(method, BASE + path);
    }

    @Test
    void eachReadRouteBelongsToOneArea() {
        assertThat(area("GET", "/reservations")).contains("RESERVATIONS");
        assertThat(area("GET", "/reservations/" + ID + "/payments")).contains("RESERVATIONS");
        assertThat(area("GET", "/payment-proofs/" + ID + "/file")).contains("RESERVATIONS");
        assertThat(area("GET", "/refund-authorizations")).contains("RESERVATIONS");
        assertThat(area("GET", "/credit-balances")).contains("RESERVATIONS");
        assertThat(area("GET", "/catalog/services/" + ID + "/departures/" + ID + "/reservations")).contains("RESERVATIONS");
        assertThat(area("GET", "/catalog/services")).contains("CATALOG");
        assertThat(area("GET", "/discount-rules/" + ID)).contains("CATALOG");
        assertThat(area("GET", "/associated-establishments")).contains("CATALOG");
        assertThat(area("GET", "/customers/" + ID)).contains("CUSTOMERS");
        assertThat(area("GET", "/customers/" + ID + "/sensitive-data")).contains("SENSITIVE");
        assertThat(area("GET", "/payment-proofs/" + ID + "/file")).contains("RESERVATIONS");
        // Not routes of this service at the root: the retention and the client notices live under /internal, the deadlines under /reservations/{id}.
        assertThat(area("GET", "/sensitive-data-retention/due")).isEmpty();
        assertThat(area("GET", "/client-notices/" + ID)).isEmpty();
        assertThat(area("GET", "/payment-deadlines")).isEmpty();
    }

    @Test
    void aWriteAndTheInternalRoutesHaveNoArea() {
        assertThat(area("POST", "/reservations")).isEmpty();
        assertThat(area("PUT", "/payment-parameters")).isEmpty();
        assertThat(area("PATCH", "/catalog/services/" + ID)).isEmpty();
        assertThat(area("DELETE", "/reservations/" + ID)).isEmpty();
        assertThat(area("GET", "/internal/outbox/events")).isEmpty();
        assertThat(area("GET", "/internal/tenants/" + ID + "/payment-proofs")).isEmpty();
    }

    @Test
    void whatIsNotListedAndOtherServicesAreDenied() {
        assertThat(area("GET", "/something-new")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", "/api/v1/cash-reporting/cash-days")).isEmpty();
        assertThat(ExceptionalAccessPolicy.areaOf("GET", BASE)).isEmpty();
        assertThat(area("GET", "/reservations/../internal/outbox")).isEmpty();
    }

    @Test
    void aRouteIsAllowedOnlyInsideTheScope() {
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/reservations", Set.of("RESERVATIONS"))).isTrue();
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/reservations", Set.of("CATALOG", "CASH"))).isFalse();
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/customers/" + ID + "/sensitive-data", Set.of("CUSTOMERS"))).isFalse();
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/customers/" + ID + "/sensitive-data", Set.of("CUSTOMERS", "SENSITIVE"))).isTrue();
        assertThat(ExceptionalAccessPolicy.allows("POST", BASE + "/reservations", Set.of("RESERVATIONS"))).isFalse();
        assertThat(ExceptionalAccessPolicy.allows("GET", BASE + "/reservations", Set.of())).isFalse();
    }

    @Test
    void theRouteTemplateHidesTheIdentifiers() {
        assertThat(ExceptionalAccessPolicy.template(BASE + "/reservations/" + ID + "/payments")).isEqualTo(BASE + "/reservations/{id}/payments");
        assertThat(ExceptionalAccessPolicy.template(BASE + "/catalog/services")).isEqualTo(BASE + "/catalog/services");
    }
}
