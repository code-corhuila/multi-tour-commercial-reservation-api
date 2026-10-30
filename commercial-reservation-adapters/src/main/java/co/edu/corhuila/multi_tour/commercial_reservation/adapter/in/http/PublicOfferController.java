package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PublicTourDetail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPublicEstablishment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetPublicTour;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPublicEstablishments;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListPublicTours;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EstablishmentType;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The public offer of a tenant (HU-COM-006, plan D-09): GET only, no token, the tenant from the identifier of the path.
// It answers the allow-list records of the core and nothing else. The cache policy is PROPOSED (gap G-I): the gateway
// may override it. An error never carries one.
@RestController
public final class PublicOfferController {
    private static final String BASE = "/api/v1/commercial-reservation/public/tenants/{identifier}";
    private static final String LIST_CACHE = "public, max-age=60", DETAIL_CACHE = "public, max-age=30";

    private final ListPublicTours tours;
    private final GetPublicTour tour;
    private final ListPublicEstablishments establishments;
    private final GetPublicEstablishment establishment;

    public PublicOfferController(ListPublicTours tours, GetPublicTour tour, ListPublicEstablishments establishments,
            GetPublicEstablishment establishment) {
        this.tours = tours;
        this.tour = tour;
        this.establishments = establishments;
        this.establishment = establishment;
    }

    @GetMapping(BASE + "/tours")
    public ResponseEntity<PublicPageResponse<PublicTour>> listTours(@PathVariable("identifier") String identifier,
            @RequestParam Map<String, String> query) {
        allow(query, "page", "limit", "search");
        int page = integer(query, "page", 1, Integer.MAX_VALUE), limit = integer(query, "limit", 20, 100);
        var result = tours.list(identifier, Optional.ofNullable(query.get("search")), page, limit);
        return cached(LIST_CACHE, PublicPageResponse.of(result.items(), result.total(), page, limit));
    }

    @GetMapping(BASE + "/tours/{serviceId}")
    public ResponseEntity<PublicTourDetail> getTour(@PathVariable("identifier") String identifier,
            @PathVariable("serviceId") UUID serviceId) {
        return cached(DETAIL_CACHE, tour.get(identifier, serviceId));
    }

    @GetMapping(BASE + "/associated-establishments")
    public ResponseEntity<PublicPageResponse<PublicEstablishment>> listEstablishments(
            @PathVariable("identifier") String identifier, @RequestParam Map<String, String> query) {
        allow(query, "page", "limit", "establishmentType");
        int page = integer(query, "page", 1, Integer.MAX_VALUE), limit = integer(query, "limit", 20, 100);
        var result = establishments.list(identifier, type(query.get("establishmentType")), page, limit);
        return cached(LIST_CACHE, PublicPageResponse.of(result.items(), result.total(), page, limit));
    }

    @GetMapping(BASE + "/associated-establishments/{associationId}")
    public ResponseEntity<PublicEstablishment> getEstablishment(@PathVariable("identifier") String identifier,
            @PathVariable("associationId") UUID associationId) {
        return cached(LIST_CACHE, establishment.get(identifier, associationId));
    }

    private static Optional<EstablishmentType> type(String value) {
        try {
            return Optional.ofNullable(value).map(EstablishmentType::valueOf);
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("establishmentType", "must be one of the establishment types");
        }
    }

    static <T> ResponseEntity<T> cached(String policy, T body) {
        return ResponseEntity.ok().header("Cache-Control", policy).body(body);
    }

    static void allow(Map<String, String> query, String... names) {
        for (String field : query.keySet()) {
            if (!Set.of(names).contains(field)) throw new DomainException(field, "unknown query parameter");
        }
    }

    static int integer(Map<String, String> query, String field, int fallback, int maximum) {
        try {
            int value = query.containsKey(field) ? Integer.parseInt(query.get(field)) : fallback;
            if (value < 1 || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new DomainException(field, "must be an integer between 1 and " + maximum);
        }
    }
}
