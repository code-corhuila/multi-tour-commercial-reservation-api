package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// HU-SEC-007 G-SEC9-1, ADR-004 D5 (areas and mapping approved by the Project Lead): the token of an exceptional or emergency access reads only, inside the areas of its scope. Deny by default:
// a route not listed here, any write and the internal routes have no area, so the token never reaches them. The whole table is this class.
public final class ExceptionalAccessPolicy {
    private static final String BASE = "/api/v1/commercial-reservation";
    private static final Pattern IDENTIFIER = Pattern.compile("(?<=/)[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");
    // Order matters: the first row that matches decides the area.
    private static final Map<Pattern, String> AREAS = new LinkedHashMap<>();

    static {
        area("SENSITIVE", "customers/[^/]+/sensitive-data");
        area("RESERVATIONS", "catalog/services/[^/]+/departures/[^/]+/reservations");
        area("CUSTOMERS", "customers(/[^/]+)?");
        area("CATALOG", "catalog/.*", "discount-rules(/.*)?", "associated-establishments(/.*)?", "additional-discount-reasons(/.*)?", "departures/.*");
        area("RESERVATIONS", "reservations(/.*)?", "payment-proofs(/.*)?", "pending-payments", "payment-parameters", "financial-decisions(/.*)?",
                "refund-authorizations(/.*)?", "credit-balances(/.*)?", "credit-balance-summary", "discount-quotes(/.*)?", "cancellation-policies");
    }

    private ExceptionalAccessPolicy() {
    }

    private static void area(String area, String... routes) {
        for (String route : routes) AREAS.put(Pattern.compile(Pattern.quote(BASE + "/") + route), area);
    }

    public static Optional<String> areaOf(String method, String path) {
        if (!"GET".equals(method) || path.contains("..")) return Optional.empty();
        return AREAS.entrySet().stream().filter(row -> row.getKey().matcher(path).matches()).map(Map.Entry::getValue).findFirst();
    }

    public static boolean allows(String method, String path, Set<String> scope) {
        return areaOf(method, path).filter(scope::contains).isPresent();
    }

    // The route with its identifiers replaced, safe to write in an audit line.
    public static String template(String path) {
        return IDENTIFIER.matcher(path).replaceAll("{id}");
    }
}
