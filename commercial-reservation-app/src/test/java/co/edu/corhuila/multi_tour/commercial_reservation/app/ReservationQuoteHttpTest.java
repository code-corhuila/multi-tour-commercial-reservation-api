package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-007 over HTTP, the departures and the quote (api spec T-06, AC-07.3 to AC-07.7, AC-07.13). Every value is invented.
class ReservationQuoteHttpTest extends ReservationHttpBase {

    @Test void theDeparturesOfATourAreListedInOrderWithTheFullOneMarkedAndAPageMeta() throws Exception {
        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/catalog/services/" + tour + "/departures"), ADMIN), 200));
        assertThat(page.at("/data/0/departureId").asText()).isEqualTo(departure.toString());
        assertThat(page.at("/data/0/availableSeats").asInt()).isEqualTo(3);
        assertThat(page.at("/data/0/totalSeats").asInt()).isEqualTo(10);
        assertThat(page.at("/data/0/full").asBoolean()).isFalse();
        assertThat(page.at("/data/1/full").asBoolean()).isTrue();
        assertThat(page.at("/data/0").has("estimatedEndsAt")).isFalse();
        assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
        assertThat(page.at("/meta/limit").asInt()).isEqualTo(20);
    }

    @Test void theFinalCustomerSeesTheDeparturesAndTheAccountantDoesNot() throws Exception {
        exchange(withToken(get(BASE + "/catalog/services/" + tour + "/departures"), FINAL_CUSTOMER), 200);
        exchange(withToken(get(BASE + "/catalog/services/" + tour + "/departures"), ACCOUNTANT), 403);
    }

    @Test void aTourOfAnotherTenantAnswersNotFoundAndABadLimitIsRefused() throws Exception {
        exchange(withToken(get(BASE + "/catalog/services/" + otherTour + "/departures"), ADMIN), 404);
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/catalog/services/" + tour + "/departures")
                .param("limit", "0"), ADMIN), 400), "limit");
    }

    @Test void aQuoteReturnsTheValueInCentsAndStoresNothing() throws Exception {
        var quote = CatalogHttpTest.json(exchange(quote(quoteBody(3), COLLABORATOR), 200));
        assertThat(quote.at("/tourLine/ratePerPersonCents").asLong()).isEqualTo(100_000L);
        assertThat(quote.at("/tourLine/people").asInt()).isEqualTo(3);
        assertThat(quote.at("/tourLine/amountCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.has("transportLine")).isFalse();
        assertThat(quote.get("projectedValueCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.get("finalValueCents").asLong()).isEqualTo(300_000L);
        assertThat(quote.get("discounts").isArray()).isTrue();
        assertThat(store.saves).isZero();
    }

    @Test void theTransportIsOneLineChargedOnce() throws Exception {
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, true, false), NOW));
        store.transportRate = Optional.of(new Cents(20_000));
        var quote = CatalogHttpTest.json(exchange(quote(quoteBody(3), ADMIN), 200));
        assertThat(quote.at("/transportLine/amountCents").asLong()).isEqualTo(60_000L);
        assertThat(quote.get("projectedValueCents").asLong()).isEqualTo(360_000L);
    }

    @Test void aTourRequiringTransportWithNoRateIsRefusedWith422() throws Exception {
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, true, false), NOW));
        var error = CatalogHttpTest.json(exchange(quote(quoteBody(3), ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("transport");
    }

    @ParameterizedTest @ValueSource(strings = {"finalValueCents", "tenantId", "discountPercentage"})
    void aValueOrATenantSentInTheBodyIsRefused(String field) throws Exception {
        var body = quoteBody(2); body.put(field, 1);
        CatalogHttpTest.assertField(exchange(quote(body, ADMIN), 400), field);
    }

    @Test void aQuoteWithoutPeopleNamesTheFieldAndTheRolesThatOnlyReadAreRefused() throws Exception {
        var body = quoteBody(2); body.remove("people");
        CatalogHttpTest.assertField(exchange(quote(body, ADMIN), 400), "people");
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) exchange(quote(quoteBody(2), role), 403);
    }

    @Test void aTourOfAnotherTenantInAQuoteAnswersNotFound() throws Exception {
        var body = quoteBody(2); body.put("serviceId", otherTour.toString());
        exchange(quote(body, ADMIN), 404);
    }
}
