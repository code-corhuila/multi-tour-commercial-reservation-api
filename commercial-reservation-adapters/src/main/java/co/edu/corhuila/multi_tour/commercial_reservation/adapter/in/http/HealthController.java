package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Public on purpose: the gateway and the orchestrator probe it before any token exists.
@RestController
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    HealthResponse health() {
        return new HealthResponse("ok", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
    }
}
