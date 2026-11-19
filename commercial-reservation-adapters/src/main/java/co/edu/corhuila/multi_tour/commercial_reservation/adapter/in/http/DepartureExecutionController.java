package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ExecutionChange;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ChangeDepartureExecution;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// startDepartureExecution and endDepartureExecution (PROPOSED in the contract, G-API4-1, RN-EJE-001): operations-cost tells the service that a departure started
// or ended. The SERVICE role and nothing else, the tenant from the path because a service token has none, and the Idempotency-Key is required. The answer is two
// counts: no reservation, customer or person is named.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}/departures/{departureId}")
public final class DepartureExecutionController {
    public record Change(UUID departureId, int changed, int unchanged) {}

    private final ChangeDepartureExecution execution;

    public DepartureExecutionController(ChangeDepartureExecution execution) {
        this.execution = execution;
    }

    @PostMapping("/execution-start")
    public Change start(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("departureId") UUID departureId, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        requireServiceAndKey(principal, key);
        return of(execution.start(tenantId, departureId, key));
    }

    @PostMapping("/execution-end")
    public Change end(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal, @PathVariable("tenantId") UUID tenantId,
            @PathVariable("departureId") UUID departureId, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        requireServiceAndKey(principal, key);
        return of(execution.end(tenantId, departureId, key));
    }

    private static void requireServiceAndKey(AuthenticatedPrincipal principal, String key) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (key == null || key.length() < 8 || key.length() > 128) throw new DomainException("Idempotency-Key", "header required, 8 to 128 characters");
    }

    private static Change of(ExecutionChange change) {
        return new Change(change.departureId(), change.changed(), change.unchanged());
    }
}
