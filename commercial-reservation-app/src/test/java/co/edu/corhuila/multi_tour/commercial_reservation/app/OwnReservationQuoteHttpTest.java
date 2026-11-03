package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-008 DP-12 (AC-08.18): the Final Customer quotes through their own route, with the same breakdown as the operator's quote.
class OwnReservationQuoteHttpTest extends ReservationHttpBase {

    private MockHttpServletRequestBuilder ownQuote(Map<String, Object> body, String role) throws Exception {
        return withToken(post(BASE + "/own/reservation-quotes"), role).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(body));
    }

    @Test void theFinalCustomerQuoteIsTheSameBreakdownAsTheOperatorsAndStoresNothing() throws Exception {
        var own = CatalogHttpTest.json(exchange(ownQuote(quoteBody(3), FINAL_CUSTOMER), 200));
        var staff = CatalogHttpTest.json(exchange(quote(quoteBody(3), ADMIN), 200));
        assertThat(own).isEqualTo(staff);
        assertThat(own.get("projectedValueCents").asLong()).isPositive();
    }

    @Test void onlyTheFinalCustomerUsesTheOwnRouteAndTheyAreStillRefusedOnTheOperatorsRoute() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) exchange(ownQuote(quoteBody(2), role), 403);
        exchange(quote(quoteBody(2), FINAL_CUSTOMER), 403);
    }

    @Test void theOwnQuoteNamesAMissingFieldAndRefusesAValueSentInTheBody() throws Exception {
        var body = quoteBody(2); body.remove("people");
        CatalogHttpTest.assertField(exchange(ownQuote(body, FINAL_CUSTOMER), 400), "people");
        var withValue = quoteBody(2); withValue.put("projectedValueCents", 1);
        exchange(ownQuote(withValue, FINAL_CUSTOMER), 400);
    }

    @Test void theOwnQuoteWithoutATokenIsRefusedWith401() throws Exception {
        exchange(post(BASE + "/own/reservation-quotes").header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(quoteBody(2))), 401);
    }
}
