package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.EpisodeState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-018 AC-18.6, AC-18.7, AC-18.9, AC-18.15, AC-18.17 over HTTP, without a database: the proposal, the answer of the customer, the outside answer of the staff and the declaration of no
// alternative. Two customers and two tenants (audit M1): a Final Customer answers only the proposal of their own reservation and a foreign one looks missing. The role gate comes before any
// load, every write needs its Idempotency-Key and a repeat answers the stored episode without writing again. The evidence never comes back.
@Import({ReschedulingWritesHttpFixture.class})
class ReschedulingWriteHttpTest extends ReservationHttpBase {

    private static final UUID OWNER_ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), SECOND_ACCOUNT = new UUID(0, 0x7A1), UNLINKED_ACCOUNT = new UUID(0, 0x7A9),
            SECOND_CUSTOMER = new UUID(1, 0x7A2), FOREIGN_CUSTOMER = new UUID(1, 0x7A3), OWNER_EPISODE = new UUID(1, 0x7B1), OWNER_PROPOSAL = new UUID(1, 0x7C1),
            FRESH_EPISODE = new UUID(1, 0x7B2), PROPOSED = new UUID(1, 0x7D1), STAFF = new UUID(0, 0x7E1), FOREIGN_EPISODE = new UUID(1, 0x7B3), UNKNOWN = new UUID(1, 0x7FF);
    private static final Instant CANCELLED = NOW.minusSeconds(3_600);
    @Autowired ReschedulingWritesHttpFixture.MemoryEpisodes episodes;
    private UUID ownerReservation, freshReservation;

    @BeforeEach void plant() throws Exception {
        episodes.clear();
        customers.save(customers.customers.get(customer).linkedTo(OWNER_ACCOUNT));
        customers.save(Customer.registerLead(SECOND_CUSTOMER, tenant, "CC2222", "Segunda Persona", BirthDateOrAge.ofAge(41),
                new ContactInfo("segunda@example.invalid", "3000000002"), true, NOW).linkedTo(SECOND_ACCOUNT));
        directory.tenants.put(otherTenant.toString(), new TenantRef(otherTenant, true));
        customers.save(Customer.registerLead(FOREIGN_CUSTOMER, otherTenant, "CC3333", "Persona de Otro Tenant", BirthDateOrAge.ofAge(35),
                new ContactInfo("ajena@example.invalid", "3000000003"), true, NOW).linkedTo(OWNER_ACCOUNT));
        ownerReservation = reservationOf(customer, "plant-key-0001");
        freshReservation = reservationOf(customer, "plant-key-0002");
        episodes.save(ReschedulingEpisode.open(OWNER_EPISODE, tenant, ownerReservation, departure, CANCELLED, NOW)
                .propose(OWNER_PROPOSAL, PROPOSED, 3, STAFF, NOW, NOW.plusSeconds(864_000)));
        episodes.save(ReschedulingEpisode.open(FRESH_EPISODE, tenant, freshReservation, departure, CANCELLED, NOW));
        episodes.save(ReschedulingEpisode.open(FOREIGN_EPISODE, otherTenant, new UUID(1, 0x7F3), departure, CANCELLED, NOW));
    }

    private UUID reservationOf(UUID holder, String key) throws Exception {
        var body = createBody(); body.put("customerId", holder.toString());
        return UUID.fromString(CatalogHttpTest.json(exchange(create(body, key, ADMIN), 201)).get("reservationId").asText());
    }

    private MockHttpServletRequestBuilder as(UUID account, UUID tenantId, MockHttpServletRequestBuilder request) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("tenantId", tenantId.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return request.header("Authorization", "Bearer " + TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED)).header("X-Correlation-Id", TRACE);
    }

    private MockHttpServletRequestBuilder write(String path, Map<String, Object> body, String key) throws Exception {
        var request = post(BASE + "/reschedulings/" + path).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body));
        return key == null ? request : request.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder answer(UUID episode, UUID proposal, String decision, String key) throws Exception {
        return write(episode + "/proposals/" + proposal + "/answer", Map.of("decision", decision), key);
    }

    private Map<String, Object> outside(String decision, String channel, String evidence) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision); body.put("channel", channel); body.put("evidence", evidence); body.put("answeredAt", NOW.toString());
        return body;
    }

    @Test void aFinalCustomerAnswersOnlyTheProposalOfTheirOwnReservationAndItTakesEffectAtOnce() throws Exception {
        var done = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0001")), 200));

        assertThat(done.get("state").asText()).isEqualTo("RESCHEDULED");
        assertThat(done.get("proposals").get(0).get("answerSource").asText()).isEqualTo("CUSTOMER");
        assertThat(episodes.episodes.get(OWNER_EPISODE).proposals().get(0).state()).isEqualTo(ProposalState.ACCEPTED);
    }

    @Test void aFinalCustomerRejectingClosesTheEpisodeAsCancelled() throws Exception {
        var done = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "REJECT", "answer-key-0002")), 200));

        assertThat(done.get("state").asText()).isEqualTo("CANCELLED");
        assertThat(done.get("closingReason").asText()).isEqualTo("REJECTED_BY_CUSTOMER");
    }

    @Test void theProposalOfAnotherCustomerOrTenantLooksMissingAndNothingIsWritten() throws Exception {
        var foreign = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0003")), 404));
        var unlinked = CatalogHttpTest.json(exchange(as(UNLINKED_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0004")), 404));
        var otherTenantToken = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, otherTenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0005")), 404));
        var otherTenantEpisode = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(FOREIGN_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0006")), 404));
        var missing = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(UNKNOWN, OWNER_PROPOSAL, "ACCEPT", "answer-key-0007")), 404));
        var strangerProposal = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, UNKNOWN, "ACCEPT", "answer-key-0008")), 404));

        for (var refused : List.of(foreign, unlinked, otherTenantToken, otherTenantEpisode, strangerProposal)) assertThat(refused.get("error")).isEqualTo(missing.get("error"));
        assertThat(foreign.toString()).doesNotContain(customer.toString(), ownerReservation.toString());
        assertThat(episodes.writes).isEmpty();
        assertThat(episodes.episodes.get(OWNER_EPISODE).state()).isEqualTo(EpisodeState.OPEN);
    }

    @Test void onlyTheFinalCustomerAnswersAsTheCustomerAndStaffGetsForbiddenBeforeAnyLoad() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST, "SERVICE"))
            exchange(withToken(answer(UNKNOWN, OWNER_PROPOSAL, "ACCEPT", "answer-key-0009"), role), 403);
        assertThat(episodes.writes).isEmpty();
    }

    @Test void theAnswerNeedsItsKeyAndAClosedBodyAndARepeatWithTheSameKeyWritesOnce() throws Exception {
        exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", null)), 400);
        exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "MAYBE", "answer-key-0010")), 400);
        exchange(as(OWNER_ACCOUNT, tenant, write(OWNER_EPISODE + "/proposals/" + OWNER_PROPOSAL + "/answer", Map.of("decision", "ACCEPT", "channel", "EMAIL"), "answer-key-0010")), 400);
        assertThat(episodes.writes).isEmpty();

        exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0011")), 200);
        var repeat = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, answer(OWNER_EPISODE, OWNER_PROPOSAL, "ACCEPT", "answer-key-0011")), 200));

        assertThat(repeat.get("state").asText()).isEqualTo("RESCHEDULED");
        assertThat(episodes.writes).hasSize(1);
        exchange(as(OWNER_ACCOUNT, tenant, answer(FRESH_EPISODE, new UUID(1, 0x7C9), "ACCEPT", "answer-key-0011")), 422);
    }

    @Test void staffRecordsAnOutsideAnswerWithChannelAndEvidenceAndTheEvidenceNeverComesBack() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR)) {
            episodes.save(ReschedulingEpisode.open(OWNER_EPISODE, tenant, ownerReservation, departure, CANCELLED, NOW).propose(OWNER_PROPOSAL, PROPOSED, 3, STAFF, NOW, NOW.plusSeconds(864_000)));
            var done = CatalogHttpTest.json(exchange(withToken(write(OWNER_EPISODE + "/proposals/" + OWNER_PROPOSAL + "/external-answer", outside("ACCEPT", "WHATSAPP", " captura-ficticia-777 "),
                    "outside-" + role.length()), role), 201));

            assertThat(done.get("state").asText()).isEqualTo("RESCHEDULED");
            assertThat(done.get("proposals").get(0).get("answerSource").asText()).isEqualTo("EXTERNAL");
            assertThat(done.get("proposals").get(0).get("channel").asText()).isEqualTo("WHATSAPP");
            assertThat(done.toString()).doesNotContain("captura-ficticia-777", "evidence");
        }
    }

    @Test void theOutsideAnswerIsRefusedWithoutAChannelOrEvidenceOrAValidInstantAndToEveryoneButTheTwoStaffRoles() throws Exception {
        String path = OWNER_EPISODE + "/proposals/" + OWNER_PROPOSAL + "/external-answer";
        exchange(withToken(write(path, outside("ACCEPT", "SMOKE", "ok"), "outside-key-0001"), ADMIN), 400);
        exchange(withToken(write(path, outside("ACCEPT", "EMAIL", "   "), "outside-key-0002"), ADMIN), 400);
        exchange(withToken(write(path, outside("ACCEPT", "EMAIL", "x".repeat(501)), "outside-key-0003"), ADMIN), 400);
        var future = outside("ACCEPT", "EMAIL", "ok"); future.put("answeredAt", NOW.plusSeconds(60).toString());
        exchange(withToken(write(path, future, "outside-key-0004"), ADMIN), 400);
        var noInstant = outside("ACCEPT", "EMAIL", "ok"); noInstant.remove("answeredAt");
        exchange(withToken(write(path, noInstant, "outside-key-0005"), ADMIN), 400);
        exchange(withToken(write(path, outside("ACCEPT", "EMAIL", "ok"), null), ADMIN), 400);
        assertThat(episodes.writes).isEmpty();

        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, "SERVICE"))
            exchange(withToken(write(UNKNOWN + "/proposals/" + OWNER_PROPOSAL + "/external-answer", outside("ACCEPT", "EMAIL", "ok"), "outside-key-0006"), role), 403);
        exchange(as(OWNER_ACCOUNT, tenant, write(path, outside("ACCEPT", "EMAIL", "ok"), "outside-key-0007")), 403);
        exchange(withToken(write(UNKNOWN + "/proposals/" + OWNER_PROPOSAL + "/external-answer", outside("ACCEPT", "EMAIL", "ok"), "outside-key-0008"), ADMIN), 404);
        exchange(withToken(write(FOREIGN_EPISODE + "/proposals/" + OWNER_PROPOSAL + "/external-answer", outside("ACCEPT", "EMAIL", "ok"), "outside-key-0009"), ADMIN), 404);
        assertThat(episodes.writes).isEmpty();
    }

    @Test void staffSendsAProposalOnceWithItsKeyAndTheOtherRolesAndTheCustomerAreForbidden() throws Exception {
        String path = FRESH_EPISODE + "/proposals";
        var sent = CatalogHttpTest.json(exchange(withToken(write(path, Map.of("proposedDepartureId", PROPOSED.toString()), "proposal-key-0001"), COLLABORATOR), 201));
        var repeat = CatalogHttpTest.json(exchange(withToken(write(path, Map.of("proposedDepartureId", PROPOSED.toString()), "proposal-key-0001"), COLLABORATOR), 200));

        assertThat(sent.get("proposals")).hasSize(1);
        assertThat(repeat.get("proposals")).hasSize(1);
        assertThat(episodes.writes).hasSize(1);
        exchange(withToken(write(path, Map.of("proposedDepartureId", PROPOSED.toString()), null), ADMIN), 400);
        exchange(withToken(write(path, Map.of("proposedSalidaId", PROPOSED.toString()), "proposal-key-0002"), ADMIN), 400);
        exchange(withToken(write(path, Map.of("proposedDepartureId", "not-a-uuid"), "proposal-key-0003"), ADMIN), 400);
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, "SERVICE")) exchange(withToken(write(UNKNOWN + "/proposals", Map.of("proposedDepartureId", PROPOSED.toString()), "proposal-key-0004"), role), 403);
        exchange(as(OWNER_ACCOUNT, tenant, write(path, Map.of("proposedDepartureId", PROPOSED.toString()), "proposal-key-0005")), 403);
        exchange(withToken(write(FOREIGN_EPISODE + "/proposals", Map.of("proposedDepartureId", PROPOSED.toString()), "proposal-key-0006"), ADMIN), 404);
        assertThat(episodes.writes).hasSize(1);
    }

    @Test void noAlternativeClosesTheEpisodeAsCancelledAndARepeatAnswersTheSameEpisode() throws Exception {
        String path = FRESH_EPISODE + "/no-alternative";
        var first = CatalogHttpTest.json(exchange(withToken(write(path, Map.of(), null), ADMIN), 200));
        var repeat = CatalogHttpTest.json(exchange(withToken(write(path, Map.of(), null), COLLABORATOR), 200));

        assertThat(first.get("state").asText()).isEqualTo("CANCELLED");
        assertThat(first.get("closingReason").asText()).isEqualTo("NO_ALTERNATIVE");
        assertThat(repeat.get("reschedulingId")).isEqualTo(first.get("reschedulingId"));
        exchange(withToken(write(OWNER_EPISODE + "/no-alternative", Map.of(), null), ADMIN), 422);
        exchange(withToken(write(path, Map.of("reason", "x"), null), ADMIN), 400);
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, "SERVICE")) exchange(withToken(write(UNKNOWN + "/no-alternative", Map.of(), null), role), 403);
        exchange(as(OWNER_ACCOUNT, tenant, write(path, Map.of(), null)), 403);
        exchange(withToken(write(FOREIGN_EPISODE + "/no-alternative", Map.of(), null), ADMIN), 404);
    }
}
