package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * ADR-004 D7: the final test compares the operations of the contract with the ones the HTTP tests exercised and fails
 * if any is missing. It runs after every other class (see junit-platform.properties) and reads what
 * {@link ContractConformance} collected. An operation can be left out only in one of the two explicit lists below, each
 * with its reason; both lists are checked so that they cannot go stale.
 */
class ContractCoverageTest {

    /** Operations of the published contract that the service does not implement yet (they are marked x-proposed). */
    static final Map<String, String> NOT_IMPLEMENTED = Map.of(
            "POST /internal/tenants/{tenantId}/reschedulings/{reschedulingId}/freeze", "x-proposed and deprecated in the contract: HU-COM-020 freezes inside the transaction of the tenant event (plan D-03), so this route was never built",
            "POST /internal/tenants/{tenantId}/reschedulings/{reschedulingId}/resume", "x-proposed and deprecated in the contract: HU-COM-020 resumes inside the transaction of the tenant event (plan D-03), so this route was never built",
            "GET /internal/tenants/{tenantId}/reschedulings", "deprecated in the contract; replaced by GET /internal/reschedulings (DP-WRK5-1), which is exercised");

    /**
     * Implemented operations whose tests need PostgreSQL (the *JdbcHttpTest classes). The ci.yml of the service runs with
     * no database (ADR-004 D7), so these are exercised in the database-backed run and are listed here for the run without it.
     */
    static final Map<String, String> DATABASE_ONLY = Map.ofEntries(
            Map.entry("GET /cancellation-policies", "CancellationPolicyJdbcHttpTest"),
            Map.entry("PUT /cancellation-policies", "CancellationPolicyJdbcHttpTest"),
            Map.entry("PUT /internal/tenants/{tenantId}/departures/{departureId}/capacity-ceiling", "DepartureCeilingJdbcHttpTest"),
            Map.entry("GET /credit-balance-summary", "CreditBalanceReadJdbcHttpTest"),
            Map.entry("POST /financial-decisions/{financialDecisionId}/credit-decision", "CreditBalanceWriteJdbcHttpTest"),
            Map.entry("GET /internal/tenants/{tenantId}/sensitive-data-retention/due", "SensitiveDataRetentionJdbcHttpTest"),
            Map.entry("POST /internal/tenants/{tenantId}/reservations/{reservationId}/sensitive-data-deletion", "SensitiveDataRetentionJdbcHttpTest"),
            Map.entry("GET /additional-discount-reasons", "AuthorizableReasonHttpTest (TEST_DATABASE_URL)"),
            Map.entry("POST /additional-discount-reasons", "AuthorizableReasonHttpTest (TEST_DATABASE_URL)"),
            Map.entry("POST /additional-discount-reasons/{reasonId}/disable", "AuthorizableReasonHttpTest (TEST_DATABASE_URL)"));

    @Test
    void everyDeclaredOperationIsExercisedOrExplicitlyExcluded() {
        assumeFalse(ContractConformance.EXERCISED.isEmpty(), "run the whole module: this test reads what the other classes exercised");
        Set<String> declared = ContractConformance.declared();
        Set<String> missing = new TreeSet<>(declared);
        missing.removeAll(ContractConformance.EXERCISED);
        missing.removeAll(NOT_IMPLEMENTED.keySet());
        missing.removeAll(DATABASE_ONLY.keySet());
        assertThat(missing).as("operations of the contract that no HTTP test exercised").isEmpty();
        assertThat(declared).as("the explicit lists name operations of the contract").containsAll(NOT_IMPLEMENTED.keySet())
                .containsAll(DATABASE_ONLY.keySet());
        assertThat(ContractConformance.EXERCISED).as("an operation listed as not implemented must not be exercised")
                .doesNotContainAnyElementsOf(NOT_IMPLEMENTED.keySet());
        assertThat(ContractConformance.RECONCILING).as("every response must match the contract").isEmpty();
    }
}
