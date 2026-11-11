package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProposalAnswer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-018 AC-18.15, AC-18.16, AC-18.18 over HTTP, without a database: the list and the detail of the episodes. Two customers and two tenants (audit M1): a Final Customer reads only the
// episode of their own reservation, a foreign one and one of another tenant look missing, and the staff roles read the whole tenant. The evidence of an answer is never in the answer.
@Import({ReschedulingReadsHttpFixture.class})
class ReschedulingReadHttpTest extends ReservationHttpBase {

    private static final UUID OWNER_ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), SECOND_ACCOUNT = new UUID(0, 0x6A1), UNLINKED_ACCOUNT = new UUID(0, 0x6A9),
            SECOND_CUSTOMER = new UUID(1, 0x6A2), FOREIGN_CUSTOMER = new UUID(1, 0x6A3), OWNER_EPISODE = new UUID(1, 0x6B1), SECOND_EPISODE = new UUID(1, 0x6B2),
            FOREIGN_EPISODE = new UUID(1, 0x6B3), PROPOSAL = new UUID(1, 0x6C1), PROPOSED = new UUID(1, 0x6D1), STAFF = new UUID(0, 0x6E1),
            OWNER_RESERVATION = new UUID(1, 0x6F1), SECOND_RESERVATION = new UUID(1, 0x6F2), FOREIGN_RESERVATION = new UUID(1, 0x6F3);
    private static final Instant CANCELLED = NOW.minusSeconds(3_600);
    @Autowired ReschedulingReadsHttpFixture.MemoryEpisodeReads episodes;

    @BeforeEach void plant() throws Exception {
        episodes.clear();
        customers.save(customers.customers.get(customer).linkedTo(OWNER_ACCOUNT));
        customers.save(Customer.registerLead(SECOND_CUSTOMER, tenant, "CC2222", "Segunda Persona", BirthDateOrAge.ofAge(41),
                new ContactInfo("segunda@example.invalid", "3000000002"), true, NOW).linkedTo(SECOND_ACCOUNT));
        directory.tenants.put(otherTenant.toString(), new TenantRef(otherTenant, true));
        customers.save(Customer.registerLead(FOREIGN_CUSTOMER, otherTenant, "CC3333", "Persona de Otro Tenant", BirthDateOrAge.ofAge(35),
                new ContactInfo("ajena@example.invalid", "3000000003"), true, NOW).linkedTo(OWNER_ACCOUNT));
        episodes.plant(ReschedulingEpisode.open(OWNER_EPISODE, tenant, OWNER_RESERVATION, departure, CANCELLED, NOW), customer);
        episodes.plant(answered(SECOND_EPISODE, SECOND_RESERVATION), SECOND_CUSTOMER);
        episodes.plant(ReschedulingEpisode.open(FOREIGN_EPISODE, otherTenant, FOREIGN_RESERVATION, departure, CANCELLED, NOW), FOREIGN_CUSTOMER);
    }

    private ReschedulingEpisode answered(UUID id, UUID reservation) {
        return ReschedulingEpisode.open(id, tenant, reservation, departure, CANCELLED, NOW.minusSeconds(3_000))
                .propose(PROPOSAL, PROPOSED, 3, STAFF, NOW.minusSeconds(1_800), NOW.plusSeconds(864_000))
                .answer(ProposalAnswer.outside(true, STAFF, "WHATSAPP", "captura-ficticia-777", NOW.minusSeconds(1_200)), NOW.minusSeconds(600));
    }

    private MockHttpServletRequestBuilder as(UUID account, UUID tenantId, MockHttpServletRequestBuilder request) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("tenantId", tenantId.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return request.header("Authorization", "Bearer " + TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED)).header("X-Correlation-Id", TRACE);
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("data").forEach(item -> ids.add(item.get("reschedulingId").asText()));
        return ids;
    }

    @Test void theStaffRolesReadTheWholeTenantAndNeverTheEpisodesOfAnotherTenant() throws Exception {
        for (String role : List.of(ADMIN, COLLABORATOR, MANAGER, ACCOUNTANT, ANALYST)) {
            var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings"), role), 200));
            assertThat(ids(page)).containsExactlyInAnyOrder(OWNER_EPISODE.toString(), SECOND_EPISODE.toString());
            assertThat(page.get("meta").get("total").asInt()).isEqualTo(2);
        }
    }

    @Test void theListNarrowsByReservationDepartureAndStateAndRefusesAnUnknownParameter() throws Exception {
        var byReservation = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings").param("reservationId", SECOND_RESERVATION.toString()), ADMIN), 200));
        var byState = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings").param("state", "RESCHEDULED"), ADMIN), 200));
        var byDeparture = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings").param("departureId", fullDeparture.toString()), ADMIN), 200));

        assertThat(ids(byReservation)).containsExactly(SECOND_EPISODE.toString());
        assertThat(ids(byState)).containsExactly(SECOND_EPISODE.toString());
        assertThat(ids(byDeparture)).isEmpty();
        exchange(withToken(get(BASE + "/reschedulings").param("state", "NOPE"), ADMIN), 400);
        exchange(withToken(get(BASE + "/reschedulings").param("customerId", customer.toString()), ADMIN), 400);
    }

    @Test void theEpisodeShowsTheDeadlinesTheProposalAndTheHistoryWithoutTheEvidence() throws Exception {
        var episode = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings/" + SECOND_EPISODE), COLLABORATOR), 200));

        assertThat(episode.get("state").asText()).isEqualTo("RESCHEDULED");
        assertThat(episode.get("closingReason").asText()).isEqualTo("ACCEPTED");
        assertThat(episode.get("reservationId").asText()).isEqualTo(SECOND_RESERVATION.toString());
        assertThat(episode.get("customerId").asText()).isEqualTo(SECOND_CUSTOMER.toString());
        assertThat(episode.get("originalDepartureId").asText()).isEqualTo(departure.toString());
        assertThat(episode.get("episodeLimitAt").asText()).isEqualTo(CANCELLED.plusSeconds(96 * 3_600L).toString());
        assertThat(episode.get("frozen").asBoolean()).isFalse();
        var proposal = episode.get("proposals").get(0);
        assertThat(proposal.get("proposedDepartureId").asText()).isEqualTo(PROPOSED.toString());
        assertThat(proposal.get("state").asText()).isEqualTo("ACCEPTED");
        assertThat(proposal.get("answerSource").asText()).isEqualTo("EXTERNAL");
        assertThat(proposal.get("channel").asText()).isEqualTo("WHATSAPP");
        assertThat(episode.get("history")).isNotEmpty();
        assertThat(episode.toString()).doesNotContain("captura-ficticia-777", "evidence");
    }

    @Test void aFinalCustomerListsOnlyTheEpisodeOfTheirOwnReservation() throws Exception {
        var mine = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, get(BASE + "/reschedulings")), 200));
        var theirs = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/reschedulings")), 200));
        var narrowed = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/reschedulings").param("reservationId", OWNER_RESERVATION.toString())), 200));

        assertThat(ids(mine)).containsExactly(OWNER_EPISODE.toString());
        assertThat(ids(theirs)).containsExactly(SECOND_EPISODE.toString());
        assertThat(ids(narrowed)).as("a reservation id of another customer opens nothing").isEmpty();
        assertThat(theirs.toString()).doesNotContain(OWNER_EPISODE.toString(), OWNER_RESERVATION.toString());
    }

    @Test void anAccountOfAnotherTenantAndAnUnlinkedAccountSeeNoEpisodeOfThisTenant() throws Exception {
        assertThat(ids(CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, otherTenant, get(BASE + "/reschedulings")), 200)))).containsExactly(FOREIGN_EPISODE.toString());
        assertThat(ids(CatalogHttpTest.json(exchange(as(UNLINKED_ACCOUNT, tenant, get(BASE + "/reschedulings")), 200)))).isEmpty();
    }

    @Test void theDetailOfTheEpisodeOfAnotherCustomerOrTenantLooksMissingAndTheOwnerReadsTheirs() throws Exception {
        var own = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, get(BASE + "/reschedulings/" + OWNER_EPISODE)), 200));
        var foreign = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/reschedulings/" + OWNER_EPISODE)), 404));
        var otherTenantRead = CatalogHttpTest.json(exchange(as(OWNER_ACCOUNT, tenant, get(BASE + "/reschedulings/" + FOREIGN_EPISODE)), 404));
        var staffOtherTenant = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reschedulings/" + FOREIGN_EPISODE), ADMIN), 404));
        var unlinked = CatalogHttpTest.json(exchange(as(UNLINKED_ACCOUNT, tenant, get(BASE + "/reschedulings/" + OWNER_EPISODE)), 404));
        var missing = CatalogHttpTest.json(exchange(as(SECOND_ACCOUNT, tenant, get(BASE + "/reschedulings/" + new UUID(1, 0x6FF))), 404));

        assertThat(own.get("reschedulingId").asText()).isEqualTo(OWNER_EPISODE.toString());
        assertThat(foreign.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(foreign.get("error")).isEqualTo(missing.get("error"));
        assertThat(otherTenantRead.get("error")).isEqualTo(missing.get("error"));
        assertThat(staffOtherTenant.get("error")).isEqualTo(missing.get("error"));
        assertThat(unlinked.get("error")).isEqualTo(missing.get("error"));
        assertThat(foreign.toString()).doesNotContain(customer.toString(), OWNER_RESERVATION.toString());
    }

    @Test void aTokenWithoutAReadingRoleIsRefusedAndAnAnonymousCallIsNotAuthenticated() throws Exception {
        exchange(withToken(get(BASE + "/reschedulings"), "SERVICE"), 403);
        exchange(withToken(get(BASE + "/reschedulings/" + OWNER_EPISODE), "SERVICE"), 403);
        exchange(get(BASE + "/reschedulings").header("X-Correlation-Id", TRACE), 401);
    }
}
