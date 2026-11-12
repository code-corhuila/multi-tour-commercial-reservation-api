package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SetDepartureCeiling;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// HU-COM-012 amendment 2026-11-10 (PROPOSED in the contract): the route of the saga change-departure-vehicles. The SERVICE role and nothing else, the tenant from
// the path because a service token has none, and the departure is the salidaId of the departure events.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class DepartureCeilingController {
    public record CeilingRequest(Integer ceiling) {}
    public record CeilingResponse(int previousCeiling, int ceiling, int seatsHeld) {}

    private final SetDepartureCeiling ceilings;

    public DepartureCeilingController(SetDepartureCeiling ceilings) {
        this.ceilings = ceilings;
    }

    @PutMapping("/departures/{departureId}/capacity-ceiling")
    public CeilingResponse set(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("departureId") UUID departureId, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody CeilingRequest body) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
        if (body.ceiling() == null) throw new DomainException("ceiling", "ceiling is required");
        var done = ceilings.set(tenantId, departureId, body.ceiling(), key);
        return new CeilingResponse(done.previousCeiling(), done.ceiling(), done.seatsHeld());
    }
}
