package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// HU-COM-006 T-06 (tours half): GET /public/tenants/{identifier}/tours and /tours/{serviceId} over the use cases that
// already exist. The clock is 2026-10-09T12:00:00Z; the tenant is planted under a kebab-case identifier.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DiscountRuleHttpFixture.class, ReservationHttpFixture.class})
class PublicToursHttpTest {
    static final String BASE = "/api/v1/commercial-reservation/public/tenants/";
    static final String IDENTIFIER = "agencia-ficticia", OTHER = "agencia-ajena", INACTIVE = "agencia-inactiva";
    static final String TRACE = CatalogHttpTest.TRACE;
    static final Instant NOW = TestJwtKeys.NOW;
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT), foreign = new UUID(0, 999);
    final UUID tourA = new UUID(2, 1), tourB = new UUID(2, 2), inactiveTour = new UUID(2, 3), transportOnly = new UUID(2, 4),
            foreignTour = new UUID(2, 5);

    @Autowired MockMvc mvc;
    @Autowired TenantDirectoryHttpFixture.MemoryDirectory directory;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    @Autowired DiscountRuleHttpFixture.MemoryDiscountRules rules;
    @Autowired ReservationHttpFixture.MemoryReservations store;

    @BeforeEach void seed() {
        directory.tenants.clear(); catalog.items.clear(); rules.rules.clear(); store.clear();
        directory.tenants.put(IDENTIFIER, new TenantRef(tenant, true));
        directory.tenants.put(OTHER, new TenantRef(foreign, true));
        directory.tenants.put(INACTIVE, new TenantRef(new UUID(0, 777), false));
        plant(tourA, tenant, CatalogServiceType.TOUR, "Caminata al Mirador", 100_000L, 12);
        plant(tourB, tenant, CatalogServiceType.TOUR, "Rafting Ficticio", 200_000L, 8);
        plant(transportOnly, tenant, CatalogServiceType.TRANSPORTATION, "Traslado suelto", 50_000L, 40);
        plant(foreignTour, foreign, CatalogServiceType.TOUR, "Tour Ajeno", 90_000L, 10);
        plant(inactiveTour, tenant, CatalogServiceType.TOUR, "Tour Retirado", 90_000L, 10);
        catalog.items.get(inactiveTour).inactivate(UUID.randomUUID(), NOW);
    }

    void plant(UUID id, UUID owner, CatalogServiceType type, String name, long rate, int capacity) {
        catalog.save(CatalogService.register(id, owner, type, name, CommercialTerms.of(rate, rate / 2, capacity, false, false),
                NOW.plusSeconds(id.getLeastSignificantBits())));
    }

    MvcResult call(String path, int code) throws Exception {
        var result = mvc.perform(get(BASE + path).header("X-Correlation-Id", TRACE)).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(code);
        return result;
    }

    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test void thePublicRoutesAnswerWithoutAToken() throws Exception {
        call(IDENTIFIER + "/tours", 200);
        call(IDENTIFIER + "/tours/" + tourA, 200);
    }

    @Test void aTokenSentToAPublicRouteIsIgnored() throws Exception {
        for (String bearer : new String[] {"Bearer not-a-token", "Bearer " + CatalogHttpTest.token(TestJwtKeys.TENANT, "FINAL_CUSTOMER") + "x"}) {
            var result = mvc.perform(get(BASE + IDENTIFIER + "/tours").header("Authorization", bearer)).andReturn();
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test void thePublicAnswersCarryOnlyTheAllowedFields() throws Exception {
        store.departures.put(new UUID(3, 1), new DepartureState(new UUID(3, 1), tenant, tourA, NOW.plusSeconds(86_400),
                DepartureStatus.SCHEDULED, 10, 2));
        var list = CatalogHttpTest.json(call(IDENTIFIER + "/tours", 200));
        assertThat(names(list)).containsExactlyInAnyOrder("data", "meta");
        assertThat(names(list.get("meta"))).containsExactlyInAnyOrder("page", "limit", "total", "totalPages");
        for (JsonNode tour : list.get("data")) {
            assertThat(names(tour)).isSubsetOf("serviceId", "commercialName", "transportIncluded", "baseValueCents",
                    "finalValueCents", "discountPercentageBp", "maxTravelers");
            assertThat(names(tour)).contains("serviceId", "commercialName", "transportIncluded", "baseValueCents",
                    "finalValueCents", "maxTravelers");
            assertThat(tour.get("baseValueCents").isIntegralNumber()).isTrue();
            assertThat(tour.get("finalValueCents").isIntegralNumber()).isTrue();
            assertThat(tour.get("maxTravelers").isIntegralNumber()).isTrue();
        }
        var detail = CatalogHttpTest.json(call(IDENTIFIER + "/tours/" + tourA, 200));
        assertThat(names(detail)).isSubsetOf("serviceId", "commercialName", "transportIncluded", "baseValueCents",
                "finalValueCents", "discountPercentageBp", "maxTravelers", "departures");
        assertThat(names(detail.get("departures").get(0))).containsExactlyInAnyOrder("departureId", "startsAt", "availableSeats");
        assertThat(detail.get("departures").get(0).get("startsAt").asText()).isEqualTo(NOW.plusSeconds(86_400).toString());
        String raw = call(IDENTIFIER + "/tours", 200).getResponse().getContentAsString().toLowerCase();
        for (String forbidden : new String[] {"cost", "status", "highrisk", "tenantid", "capacity", "reason", "configured"}) {
            assertThat(raw).as(forbidden).doesNotContain(forbidden);
        }
    }

    @Test void onlyActiveToursOfTheTenantAreListedNewestFirst() throws Exception {
        var data = CatalogHttpTest.json(call(IDENTIFIER + "/tours", 200)).get("data");
        assertThat(data).hasSize(2);
        assertThat(data.get(0).get("serviceId").asText()).isEqualTo(tourB.toString());
        assertThat(data.get(1).get("serviceId").asText()).isEqualTo(tourA.toString());
        assertThat(data.get(1).get("maxTravelers").asInt()).isEqualTo(12);
        assertThat(data.get(1).get("baseValueCents").asLong()).isEqualTo(100_000L);
    }

    @Test void theValueCarriesTheDiscountInForceAndNoRuleLeavesOnlyTheBase() throws Exception {
        var data = CatalogHttpTest.json(call(IDENTIFIER + "/tours", 200)).get("data");
        assertThat(data.get(0).get("finalValueCents").asLong()).isEqualTo(200_000L);
        assertThat(data.get(0).path("discountPercentageBp").isNull() || data.get(0).path("discountPercentageBp").isMissingNode()).isTrue();
        UUID rule = UUID.randomUUID();
        rules.rules.put(rule, DiscountRule.restore(rule, tenant, "Octubre", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"), 1, DiscountBase.ORIGINAL_VALUE, 1500, null, ServiceCoverage.all(), null,
                DiscountStatus.ACTIVE, NOW, NOW));
        data = CatalogHttpTest.json(call(IDENTIFIER + "/tours", 200)).get("data");
        assertThat(data.get(1).get("finalValueCents").asLong()).isEqualTo(85_000L);
        assertThat(data.get(1).get("discountPercentageBp").asInt()).isEqualTo(1500);
        assertThat(data.get(1).get("baseValueCents").asLong()).isEqualTo(100_000L);
    }

    @Test void aTenantWithNothingGetsAnEmptyPage() throws Exception {
        directory.tenants.put("agencia-vacia", new TenantRef(new UUID(0, 555), true));
        var page = CatalogHttpTest.json(call("agencia-vacia/tours", 200));
        assertThat(page.get("data")).isEmpty();
        assertThat(page.get("meta").get("total").asInt()).isZero();
    }

    @Test void listsArePaginatedAndSearchNarrowsTheTours() throws Exception {
        var first = CatalogHttpTest.json(call(IDENTIFIER + "/tours?page=1&limit=1", 200));
        assertThat(first.get("data")).hasSize(1);
        assertThat(first.get("meta").get("total").asInt()).isEqualTo(2);
        assertThat(first.get("meta").get("totalPages").asInt()).isEqualTo(2);
        var second = CatalogHttpTest.json(call(IDENTIFIER + "/tours?page=2&limit=1", 200));
        assertThat(second.get("data").get(0).get("serviceId").asText()).isEqualTo(tourA.toString());
        var found = CatalogHttpTest.json(call(IDENTIFIER + "/tours?search=MIRADOR", 200));
        assertThat(found.get("data")).hasSize(1);
        assertThat(found.get("meta").get("total").asInt()).isEqualTo(1);
    }

    @Test void aLimitOutsideOneToOneHundredOrAnUnknownParameterIsAValidationError() throws Exception {
        for (String query : new String[] {"?limit=0", "?limit=101", "?page=0", "?limit=abc", "?unknown=1"}) {
            var result = call(IDENTIFIER + "/tours" + query, 400);
            assertThat(CatalogHttpTest.json(result).get("error").asText()).as(query).isEqualTo("VALIDATION_ERROR");
        }
        call("Not_Valid/tours", 400);
        call(IDENTIFIER + "/tours/not-a-uuid", 400);
    }

    @Test void theAnswersCarryAShortPublicCachePolicyAndErrorsNone() throws Exception {
        assertThat(call(IDENTIFIER + "/tours", 200).getResponse().getHeader("Cache-Control")).isEqualTo("public, max-age=60");
        assertThat(call(IDENTIFIER + "/tours/" + tourA, 200).getResponse().getHeader("Cache-Control")).isEqualTo("public, max-age=30");
        assertThat(call(IDENTIFIER + "/tours/" + inactiveTour, 404).getResponse().getHeader("Cache-Control")).isNull();
        assertThat(call(IDENTIFIER + "/tours?limit=0", 400).getResponse().getHeader("Cache-Control")).isNull();
    }

    @Test void anotherTenantAnInactiveTourATransportAndAnUnknownIdentifierAnswerTheSameNotFound() throws Exception {
        String expected = call(IDENTIFIER + "/tours/" + inactiveTour, 404).getResponse().getContentAsString();
        assertThat(CatalogHttpTest.json(call(IDENTIFIER + "/tours/" + inactiveTour, 404)).get("error").asText()).isEqualTo("NOT_FOUND");
        for (String path : new String[] {IDENTIFIER + "/tours/" + foreignTour, IDENTIFIER + "/tours/" + transportOnly,
                IDENTIFIER + "/tours/" + UUID.randomUUID(), INACTIVE + "/tours/" + tourA, "agencia-desconocida/tours/" + tourA,
                INACTIVE + "/tours", "agencia-desconocida/tours"}) {
            assertThat(call(path, 404).getResponse().getContentAsString()).as(path).isEqualTo(expected);
        }
    }

    @Test void onlyScheduledFutureDeparturesWithSeatsAreOffered() throws Exception {
        UUID open = new UUID(3, 1), later = new UUID(3, 2);
        put(open, tourA, NOW.plusSeconds(3_600), DepartureStatus.SCHEDULED, 10, 4);
        put(later, tourA, NOW.plusSeconds(7_200), DepartureStatus.SCHEDULED, 10, 0);
        put(new UUID(3, 3), tourA, NOW.plusSeconds(3_600), DepartureStatus.SCHEDULED, 10, 10);
        put(new UUID(3, 4), tourA, NOW.plusSeconds(3_600), DepartureStatus.CANCELLED, 10, 0);
        put(new UUID(3, 5), tourA, NOW.plusSeconds(3_600), DepartureStatus.IN_EXECUTION, 10, 0);
        put(new UUID(3, 6), tourA, NOW.plusSeconds(3_600), DepartureStatus.FINALIZED, 10, 0);
        put(new UUID(3, 7), tourA, NOW.minusSeconds(3_600), DepartureStatus.SCHEDULED, 10, 0);
        put(new UUID(3, 8), tourB, NOW.plusSeconds(3_600), DepartureStatus.SCHEDULED, 10, 0);
        var departures = CatalogHttpTest.json(call(IDENTIFIER + "/tours/" + tourA, 200)).get("departures");
        assertThat(departures).hasSize(2);
        assertThat(departures.get(0).get("departureId").asText()).isEqualTo(open.toString());
        assertThat(departures.get(0).get("availableSeats").asInt()).isEqualTo(6);
        assertThat(departures.get(1).get("departureId").asText()).isEqualTo(later.toString());
        assertThat(departures.get(1).get("availableSeats").asInt()).isEqualTo(10);
    }

    @Test void aTourWithNoOpenDepartureAnswersAnEmptyListAndTheMaximumTravellers() throws Exception {
        var detail = CatalogHttpTest.json(call(IDENTIFIER + "/tours/" + tourA, 200));
        assertThat(detail.get("departures")).isEmpty();
        assertThat(detail.get("maxTravelers").asInt()).isEqualTo(12);
    }

    void put(UUID id, UUID service, Instant startsAt, DepartureStatus status, int capacity, int held) {
        store.departures.put(id, new DepartureState(id, tenant, service, startsAt, status, capacity, held));
    }
}
