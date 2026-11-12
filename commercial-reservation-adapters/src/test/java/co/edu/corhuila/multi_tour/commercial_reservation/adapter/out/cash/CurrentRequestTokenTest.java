package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// The token of the person is read from the request being served, never stored (ADR-004 D5).
class CurrentRequestTokenTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void serving(String authorization) {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                CurrentRequestTokenTest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                (proxy, method, args) -> "getHeader".equals(method.getName()) && "Authorization".equals(args[0])
                        ? authorization
                        : null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void theBearerTokenOfTheRequestIsReturnedWithoutItsPrefix() {
        serving("Bearer abc.def.ghi");

        assertThat(CurrentRequestToken.bearer()).contains("abc.def.ghi");
    }

    @Test
    void noRequestNoHeaderOrAnotherSchemeGiveNoToken() {
        assertThat(CurrentRequestToken.bearer()).isEmpty();

        serving(null);
        assertThat(CurrentRequestToken.bearer()).isEmpty();

        serving("Basic abc");
        assertThat(CurrentRequestToken.bearer()).isEmpty();

        serving("Bearer ");
        assertThat(CurrentRequestToken.bearer()).isEmpty();
    }
}
