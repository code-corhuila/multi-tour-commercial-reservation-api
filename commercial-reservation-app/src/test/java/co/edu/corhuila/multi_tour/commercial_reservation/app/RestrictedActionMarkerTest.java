package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthenticatedPrincipal;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RestrictedActionMarker;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// HU-COM-020 AC-20.13, RN-12, DP-05 (PROPOSED default): the marker of the audit is read from the authenticated principal of the request in course, so each outbox port needs no flag of its own. The marker is
// the reason RESTRICTED_ACCESS and nothing else; an ordinary token, and any work done outside a request (a worker job, a hook), leave the reason empty.
class RestrictedActionMarkerTest {
    private final RestrictedActionMarker marker = new RestrictedActionMarker();

    @AfterEach
    void leaveNoRequestBehind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void requestOf(AuthenticatedPrincipal principal) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE, principal);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void aRestrictedPrincipalMarksTheActionWithRestrictedAccess() {
        requestOf(new AuthenticatedPrincipal(UUID.randomUUID().toString(), UUID.randomUUID(), Set.of("TENANT_ADMINISTRATOR"), Set.of(), true));

        assertThat(marker.reason()).contains("RESTRICTED_ACCESS");
    }

    @Test
    void anOrdinaryPrincipalLeavesTheActionUnmarked() {
        requestOf(new AuthenticatedPrincipal(UUID.randomUUID().toString(), UUID.randomUUID(), Set.of("TENANT_ADMINISTRATOR")));

        assertThat(marker.reason()).isEmpty();
    }

    @Test
    void anActionOutsideARequestIsUnmarked() {
        assertThat(marker.reason()).isEmpty();
    }
}
