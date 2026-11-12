package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthFilter;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.AuthenticatedPrincipal;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.CreditHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.ExceptionalAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.RefundHttpAccess;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.PaymentAccessDeniedException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11, option A): the role gates of the credit and refund routes let the mark of an exceptional or emergency access read, never write, and still refuse
// the Platform Administrator without the mark.
class PlatformSupportInterceptorsTest {
    private static final ExceptionalAccess MARK = new ExceptionalAccess("exceptional", Set.of("RESERVATIONS"), UUID.randomUUID());

    private static MockHttpServletRequest request(String method, AuthenticatedPrincipal principal) {
        var request = new MockHttpServletRequest(method, "/api/v1/commercial-reservation/credit-balances");
        request.setAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE, principal);
        return request;
    }

    private static AuthenticatedPrincipal marked() {
        return new AuthenticatedPrincipal(UUID.randomUUID().toString(), UUID.randomUUID(), Set.of("PLATFORM_ADMINISTRATOR"), Set.of(), false, MARK);
    }

    private static AuthenticatedPrincipal unmarked() {
        return new AuthenticatedPrincipal(UUID.randomUUID().toString(), UUID.randomUUID(), Set.of("PLATFORM_ADMINISTRATOR"));
    }

    @Test
    void theMarkReadsTheCreditAndRefundRoutes() {
        assertThat(new CreditHttpAccess().preHandle(request("GET", marked()), new MockHttpServletResponse(), null)).isTrue();
        assertThat(new RefundHttpAccess().preHandle(request("GET", marked()), new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void theMarkNeverWritesAndThePlatformAdministratorWithoutItNeverReads() {
        assertThatThrownBy(() -> new CreditHttpAccess().preHandle(request("POST", marked()), new MockHttpServletResponse(), null)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> new RefundHttpAccess().preHandle(request("POST", marked()), new MockHttpServletResponse(), null)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> new CreditHttpAccess().preHandle(request("GET", unmarked()), new MockHttpServletResponse(), null)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> new RefundHttpAccess().preHandle(request("GET", unmarked()), new MockHttpServletResponse(), null)).isInstanceOf(PaymentAccessDeniedException.class);
    }
}
