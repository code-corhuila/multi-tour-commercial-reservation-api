package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// HU-COM-020 RN-11; AC-20.14, DP-01 (PROPOSED default), plan D-06: the token access=restricted is the Administrator of an inactive tenant and reaches only the regularization work of PDR section 9
// (affected reservations, reschedules, refunds, proofs, voluntary cancellation requests) and reads. Deny by default: a new route is refused until a story adds its row here, so a suspended tenant
// cannot sell. The whole table is this class. Reads are allowed except the sensitive data of a customer and the internal routes; the writes are listed one by one.
public final class RestrictedAccessPolicy {

    private static final String BASE = "/api/v1/commercial-reservation";
    private static final String ID = "[^/]+";
    private static final Pattern READ_DENIED = Pattern.compile(Pattern.quote(BASE) + "/(customers/" + ID + "/sensitive-data|internal/.*)");
    private static final List<Pattern> WRITES = List.of(
            // HU-COM-013: the voluntary cancellation request of a customer (AC-20.7, AC-20.8)
            route("/reservations/" + ID + "/cancellation"),
            // HU-COM-010: the decision on a proof presented before the inactivation (AC-20.10, AC-20.12)
            route("/payment-proofs/" + ID + "/(approve|reject)"),
            // HU-COM-016 DP-16 (2026-11-12): the refund decisions are closed to an inactive tenant; reading refunds and executing the ones already determined stay open.
            // HU-COM-018: the reschedule routes of the operator
            route("/reschedulings/" + ID + "/(proposals|no-alternative)"),
            route("/reschedulings/" + ID + "/proposals/" + ID + "/external-answer"));

    private RestrictedAccessPolicy() {
    }

    private static Pattern route(String template) {
        return Pattern.compile(Pattern.quote(BASE) + template);
    }

    public static boolean allows(String method, String path, Set<String> roles) {
        if (!roles.contains("TENANT_ADMINISTRATOR") || !path.startsWith(BASE + "/")) return false;
        if ("GET".equals(method)) return !READ_DENIED.matcher(path).matches();
        return "POST".equals(method) && WRITES.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }
}
