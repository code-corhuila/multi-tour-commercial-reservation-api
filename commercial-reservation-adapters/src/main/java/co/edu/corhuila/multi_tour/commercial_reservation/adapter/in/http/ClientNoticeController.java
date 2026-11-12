package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadClientNoticeContent;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The content route of a client notice (getClientNoticeContent, PROPOSED in the contract, DP-15-02): job 4 asks, at send time, for the recipient and the
// variables of the template. The SERVICE role and nothing else; the tenant is the path because a service token has none. The answer is never logged.
@RestController
@RequestMapping("/api/v1/commercial-reservation/internal/tenants/{tenantId}")
public final class ClientNoticeController {
    private final ReadClientNoticeContent read;

    public ClientNoticeController(ReadClientNoticeContent read) {
        this.read = read;
    }

    @GetMapping("/client-notices/{eventId}")
    public ClientNoticeContentResponse content(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @PathVariable("tenantId") UUID tenantId, @PathVariable("eventId") UUID eventId) {
        if (!principal.roles().contains("SERVICE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ClientNoticeContentResponse.of(read.read(tenantId, eventId));
    }
}
