package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.regex.Pattern;

// The public routes of the offer, listed one by one (HU-COM-006 plan D-04). Only GET is open; any other method, or a
// path under the public area that is not listed, is refused with the shared not found before the token check.
final class PublicPaths {
    private static final String AREA = "/api/v1/commercial-reservation/public";
    private static final String TENANT = AREA + "/tenants/[^/]+";
    private static final Pattern OPEN = Pattern.compile(TENANT + "/(tours(/[^/]+)?|associated-establishments(/[^/]+(/image)?)?)");

    private PublicPaths() {
    }

    static boolean allows(String method, String path) {
        return "GET".equals(method) && OPEN.matcher(path).matches() && !path.contains("..");
    }

    static boolean isPublicArea(String path) {
        return path.equals(AREA) || path.startsWith(AREA + "/");
    }
}
