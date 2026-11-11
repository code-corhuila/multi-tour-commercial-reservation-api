package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadReschedulings;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// The reads of HU-COM-018 (PROPOSED in the contract, AC-18.15, AC-18.16, AC-18.18): the list and the detail of the rescheduling episodes. The tenant and the person are the verified token; the use
// case refuses a role that does not read and makes the customer of a Final Customer a condition of the query. A query parameter the contract does not name is a 400.
@RestController
@RequestMapping("/api/v1/commercial-reservation")
public final class ReschedulingReadController {
    private final ReadReschedulings read;

    public ReschedulingReadController(ReadReschedulings read) {
        this.read = read;
    }

    @GetMapping("/reschedulings")
    public ReservationListResponses<ReschedulingEpisodeResponse> list(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestParam Map<String, String> query) {
        var paging = new PageQuery(query, Set.of("page", "limit", "reservationId", "departureId", "state"));
        var page = read.page(PaymentActors.of(principal), uuid(query, "reservationId"), uuid(query, "departureId"), state(query), paging.page, paging.limit);
        return new ReservationListResponses<>(page.items().stream().map(ReschedulingEpisodeResponse::of).toList(), paging.meta(page.total()));
    }

    @GetMapping("/reschedulings/{reschedulingId}")
    public ReschedulingEpisodeResponse detail(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("reschedulingId") UUID id) {
        return ReschedulingEpisodeResponse.of(read.get(PaymentActors.of(principal), id));
    }

    private static UUID uuid(Map<String, String> query, String name) {
        if (!query.containsKey(name)) return null;
        try {
            return UUID.fromString(query.get(name));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException(name, name + " must be a UUID");
        }
    }

    private static EpisodeState state(Map<String, String> query) {
        if (!query.containsKey("state")) return null;
        try {
            return EpisodeState.valueOf(query.get("state"));
        } catch (IllegalArgumentException invalid) {
            throw new DomainException("state", "state is not a valid value");
        }
    }
}
