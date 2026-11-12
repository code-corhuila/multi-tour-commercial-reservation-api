package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-018 over HTTP, the internal routes the saga and job 5 call (AC-18.8, AC-18.10 to AC-18.12, AC-18.15, DP-06, DP-WRK5-1): SERVICE role only, the tenant is the path, a repeat of the
// opening answers 200, nothing due is a 422, the cross-tenant list names ids only. Every value is invented.
@Import(ReschedulingHttpFixture.class)
class ReschedulingInternalHttpTest extends ReservationHttpBase {
    private static final String KEY = "saga-key-0001";
    private final UUID reservationId = new UUID(61, 1), cancelledDeparture = new UUID(61, 2), proposedDeparture = new UUID(61, 3), person = new UUID(61, 4);
    @Autowired ReschedulingHttpFixture.MemoryEpisodes episodes;
    private String service;
    private final Instant cancelledAt = NOW.minus(Duration.ofHours(30));

    @BeforeEach void plant() throws Exception {
        episodes.clear();
        episodes.reservations.add(reservationId);
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
    }

    private MockHttpServletRequestBuilder open(UUID forTenant, Map<String, Object> body, String key, String bearer) throws Exception {
        var request = post(BASE + "/internal/tenants/" + forTenant + "/reschedulings").header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE)
                .contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private Map<String, Object> incident() {
        return Map.of("reservationId", reservationId.toString(), "departureId", cancelledDeparture.toString(), "cancelledAt", cancelledAt.toString());
    }

    private MockHttpServletRequestBuilder voiding(UUID forTenant, Map<String, Object> body, String key, String bearer) throws Exception {
        var request = post(BASE + "/internal/tenants/" + forTenant + "/reschedulings/proposed-departure-cancellations").header("Authorization", "Bearer " + bearer)
                .header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder expiry(UUID forTenant, UUID id, String bearer) throws Exception {
        return post(BASE + "/internal/tenants/" + forTenant + "/reschedulings/" + id + "/expiry").header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE);
    }

    private MockHttpServletRequestBuilder due(String query, String bearer) throws Exception {
        return get(BASE + "/internal/reschedulings" + query).header("Authorization", "Bearer " + bearer).header("X-Correlation-Id", TRACE);
    }

    @Test void theSagaOpensOneEpisodeCountingFromTheCancellationAndARepeatAnswers200WithTheSameEpisode() throws Exception {
        var opened = CatalogHttpTest.json(exchange(open(tenant, incident(), KEY, service), 201));
        var again = CatalogHttpTest.json(exchange(open(tenant, incident(), "saga-key-0002", service), 200));

        assertThat(opened.get("state").asText()).isEqualTo("OPEN");
        assertThat(opened.get("reservationId").asText()).isEqualTo(reservationId.toString());
        assertThat(opened.get("originalDepartureId").asText()).isEqualTo(cancelledDeparture.toString());
        assertThat(opened.get("cancelledAt").asText()).isEqualTo(cancelledAt.toString());
        assertThat(opened.get("proposalDeadlineAt").asText()).isEqualTo(cancelledAt.plus(Duration.ofHours(24)).toString());
        assertThat(opened.get("episodeLimitAt").asText()).isEqualTo(cancelledAt.plus(Duration.ofHours(96)).toString());
        assertThat(opened.get("frozen").asBoolean()).isFalse();
        assertThat(opened.get("proposals")).isEmpty();
        assertThat(opened.get("history").get(0).get("action").asText()).isEqualTo("EPISODE_OPENED");
        assertThat(again).isEqualTo(opened);
        assertThat(episodes.episodes).hasSize(1);
    }

    @Test void theOpeningIsForTheServiceTokenOnlyNamesTheFieldsAndAnUnknownReservationIs404() throws Exception {
        exchange(open(tenant, incident(), KEY, token(ADMIN)), 403);
        exchange(open(tenant, incident(), KEY, token(COLLABORATOR)), 403);
        CatalogHttpTest.assertField(exchange(open(tenant, Map.of("departureId", cancelledDeparture.toString(), "cancelledAt", cancelledAt.toString()), KEY, service), 400), "reservationId");
        CatalogHttpTest.assertField(exchange(open(tenant, Map.of("reservationId", reservationId.toString(), "departureId", cancelledDeparture.toString()), KEY, service), 400), "cancelledAt");
        CatalogHttpTest.assertField(exchange(open(tenant, incident(), null, service), 400), "Idempotency-Key");
        exchange(open(tenant, Map.of("reservationId", new UUID(61, 99).toString(), "departureId", cancelledDeparture.toString(), "cancelledAt", cancelledAt.toString()), KEY, service), 404);
        assertThat(episodes.episodes).isEmpty();
    }

    @Test void theCancellationOfAProposedDepartureVoidsItsPendingProposalsAndARepeatVoidsNothingMore() throws Exception {
        ReschedulingEpisode episode = ReschedulingEpisode.open(new UUID(61, 10), tenant, reservationId, cancelledDeparture, cancelledAt, cancelledAt)
                .propose(new UUID(61, 11), proposedDeparture, 2, person, NOW.minus(Duration.ofHours(10)), NOW.plus(Duration.ofDays(30)));
        episodes.save(episode);
        var body = Map.<String, Object>of("departureId", proposedDeparture.toString(), "cancelledAt", NOW.toString());

        var first = CatalogHttpTest.json(exchange(voiding(tenant, body, KEY, service), 200));
        var repeat = CatalogHttpTest.json(exchange(voiding(tenant, body, "saga-key-0002", service), 200));

        assertThat(first.get("voidedProposals").asInt()).isEqualTo(1);
        assertThat(first.get("reschedulingIds").get(0).asText()).isEqualTo(episode.id().toString());
        assertThat(repeat.get("voidedProposals").asInt()).isZero();
        assertThat(repeat.get("reschedulingIds")).isEmpty();
        assertThat(episodes.episodes.get(episode.id()).pendingProposal()).isEmpty();
    }

    @Test void theVoidingIsForTheServiceTokenOnlyAndNamesTheFields() throws Exception {
        var body = Map.<String, Object>of("departureId", proposedDeparture.toString(), "cancelledAt", NOW.toString());
        exchange(voiding(tenant, body, KEY, token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(voiding(tenant, Map.of("cancelledAt", NOW.toString()), KEY, service), 400), "departureId");
        CatalogHttpTest.assertField(exchange(voiding(tenant, body, null, service), 400), "Idempotency-Key");
    }

    @Test void theExpiryClosesAnEpisodeWhoseTwentyFourHoursPassedAndARepeatAnswers200AndNothingDueIs422() throws Exception {
        exchange(open(tenant, incident(), KEY, service), 201);
        UUID id = episodes.episodes.keySet().iterator().next();

        var closed = CatalogHttpTest.json(exchange(expiry(tenant, id, service), 200));
        var again = CatalogHttpTest.json(exchange(expiry(tenant, id, service), 200));

        assertThat(closed.get("state").asText()).isEqualTo("CANCELLED");
        assertThat(closed.get("closingReason").asText()).isEqualTo("PROPOSAL_PERIOD_EXPIRED");
        assertThat(again).isEqualTo(closed);

        ReschedulingEpisode fresh = ReschedulingEpisode.open(new UUID(61, 20), tenant, new UUID(61, 21), cancelledDeparture, NOW.minus(Duration.ofHours(1)), NOW);
        episodes.save(fresh);
        exchange(expiry(tenant, fresh.id(), service), 422);
        assertThat(episodes.episodes.get(fresh.id()).state().name()).isEqualTo("OPEN");
    }

    @Test void theExpiryIsForTheServiceTokenOnlyAndAnEpisodeOfAnotherTenantLooksMissing() throws Exception {
        exchange(open(tenant, incident(), KEY, service), 201);
        UUID id = episodes.episodes.keySet().iterator().next();
        exchange(expiry(tenant, id, token(ADMIN)), 403);
        exchange(expiry(otherTenant, id, service), 404);
        exchange(expiry(tenant, new UUID(61, 99), service), 404);
        assertThat(episodes.episodes.get(id).state().name()).isEqualTo("OPEN");
    }

    @Test void theWorkerListsTheDueEpisodesOfEveryTenantWithIdsOnlyAndABadQueryIs400() throws Exception {
        exchange(open(tenant, incident(), KEY, service), 201);
        UUID id = episodes.episodes.keySet().iterator().next();
        episodes.save(ReschedulingEpisode.open(new UUID(61, 30), otherTenant, new UUID(61, 31), cancelledDeparture, NOW.minus(Duration.ofHours(1)), NOW));

        var page = CatalogHttpTest.json(exchange(due("?due=true&limit=100", service), 200));

        assertThat(page.get("data")).hasSize(1);
        assertThat(page.get("data").get(0).get("tenantId").asText()).isEqualTo(tenant.toString());
        assertThat(page.get("data").get(0).get("reschedulingId").asText()).isEqualTo(id.toString());
        assertThat(page.get("data").get(0).get("reservationId").asText()).isEqualTo(reservationId.toString());
        assertThat(page.get("data").get(0).size()).isEqualTo(3);
        exchange(due("?due=true", token(ADMIN)), 403);
        CatalogHttpTest.assertField(exchange(due("", service), 400), "due");
        CatalogHttpTest.assertField(exchange(due("?due=false", service), 400), "due");
        CatalogHttpTest.assertField(exchange(due("?due=true&limit=101", service), 400), "limit");
    }
}
