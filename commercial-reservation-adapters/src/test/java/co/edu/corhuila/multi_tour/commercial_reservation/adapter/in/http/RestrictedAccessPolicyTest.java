package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-07 of HU-COM-020 (-api): RN-11; AC-20.14, DP-01. A token with access=restricted is the Administrator of an inactive tenant: it reaches only the regularization work and reads, and only as
// Administrator; everything else is denied by default, so a suspended tenant cannot sell. The table of the allowed writes is one class.
class RestrictedAccessPolicyTest {

    private static final Set<String> ADMIN = Set.of("TENANT_ADMINISTRATOR");
    private static final String BASE = "/api/v1/commercial-reservation";
    private static final String ID = UUID.randomUUID().toString();

    private static boolean allows(String method, String path, Set<String> roles) {
        return RestrictedAccessPolicy.allows(method, BASE + path, roles);
    }

    @Test
    void aRestrictedTokenReachesOnlyTheAllowedRoutes() {
        assertThat(allows("GET", "/reservations", ADMIN)).isTrue();
        assertThat(allows("GET", "/reservations/" + ID, ADMIN)).isTrue();
        assertThat(allows("GET", "/payment-proofs", ADMIN)).isTrue();
        assertThat(allows("GET", "/refund-authorizations", ADMIN)).isTrue();
        assertThat(allows("GET", "/catalog/services", ADMIN)).isTrue();
        assertThat(allows("POST", "/reservations/" + ID + "/cancellation", ADMIN)).isTrue();
        assertThat(allows("POST", "/payment-proofs/" + ID + "/approve", ADMIN)).isTrue();
        assertThat(allows("POST", "/payment-proofs/" + ID + "/reject", ADMIN)).isTrue();
        assertThat(allows("POST", "/reschedulings/" + ID + "/proposals", ADMIN)).isTrue();
        assertThat(allows("POST", "/reschedulings/" + ID + "/proposals/" + ID + "/external-answer", ADMIN)).isTrue();
        assertThat(allows("POST", "/reschedulings/" + ID + "/no-alternative", ADMIN)).isTrue();
    }

    @Test
    void aRestrictedTokenCannotCreateAReservationOrAPayment() {
        assertThat(allows("POST", "/reservations", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservation-quotes", ADMIN)).isFalse();
        assertThat(allows("POST", "/own/reservation-quotes", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/payments", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/modifications", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/modification-quote", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/additional-discount", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/payment-followups", ADMIN)).isFalse();
        assertThat(allows("POST", "/reservations/" + ID + "/payment-deadline/extension", ADMIN)).isFalse();
        assertThat(allows("POST", "/customers", ADMIN)).isFalse();
    }

    @Test
    void aRestrictedTokenCannotChangeTheCatalogTheDiscountsOrTheSettings() {
        assertThat(allows("POST", "/catalog/services", ADMIN)).isFalse();
        assertThat(allows("PATCH", "/catalog/services/" + ID, ADMIN)).isFalse();
        assertThat(allows("POST", "/catalog/services/" + ID + "/inactivate", ADMIN)).isFalse();
        assertThat(allows("PUT", "/catalog/settings", ADMIN)).isFalse();
        assertThat(allows("POST", "/discount-rules", ADMIN)).isFalse();
        assertThat(allows("PATCH", "/discount-rules/" + ID, ADMIN)).isFalse();
        assertThat(allows("PUT", "/payment-parameters", ADMIN)).isFalse();
        assertThat(allows("POST", "/associated-establishments", ADMIN)).isFalse();
        assertThat(allows("POST", "/additional-discount-reasons", ADMIN)).isFalse();
    }

    @Test
    void theSensitiveDataOfACustomerAndAnUnknownRouteAreDeniedByDefault() {
        assertThat(allows("GET", "/customers/" + ID + "/sensitive-data", ADMIN)).isFalse();
        assertThat(allows("DELETE", "/reservations/" + ID, ADMIN)).isFalse();
        assertThat(allows("POST", "/a-route-that-does-not-exist", ADMIN)).isFalse();
        assertThat(RestrictedAccessPolicy.allows("GET", "/other-service/reservations", ADMIN)).isFalse();
    }

    // HU-COM-016 DP-16 (Project Lead, 2026-11-12): the decisions of 24 hours are closed to an inactive tenant; the reads stay open.
    @Test
    void theRefundDecisionRoutesAreClosedToTheRestrictedAdministratorAndTheReadsStayOpen() {
        for (String route : new String[] {"refund-decision", "refund-authorization", "rejection"})
            assertThat(allows("POST", "/financial-decisions/" + ID + "/" + route, ADMIN)).as(route).isFalse();
        assertThat(allows("GET", "/financial-decisions", ADMIN)).isTrue();
        assertThat(allows("GET", "/refund-authorizations", ADMIN)).isTrue();
    }

    @Test
    void aRestrictedTokenOfAnotherRoleIsRefusedEverywhere() {
        for (String role : new String[] {"OPERATIONAL_COLLABORATOR", "MANAGER", "ACCOUNTANT", "ANALYST", "FINAL_CUSTOMER", "SERVICE"}) {
            assertThat(allows("GET", "/reservations", Set.of(role))).as(role).isFalse();
            assertThat(allows("POST", "/reservations/" + ID + "/cancellation", Set.of(role))).as(role).isFalse();
        }
        assertThat(allows("GET", "/reservations", Set.of())).isFalse();
    }
}
