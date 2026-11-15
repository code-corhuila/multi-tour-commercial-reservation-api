package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-018 api spec T-07 (RN-12, AC-18.19, RNF-03): the write routes of the rescheduling (the proposal, the answer of the customer, the outside answer with its evidence and channel text, the
// declaration of no alternative) run with the application loggers at TRACE and the web layer at DEBUG, and no captured log line (message, arguments, MDC, exception) and no answer carries the evidence,
// the name, document, e-mail or telephone of the customer. The control line proves the capture works. Every value is invented.
@Import(ReschedulingWritesHttpFixture.class)
class ReschedulingLogsHttpTest extends ReservationHttpBase {
    private static final String EVIDENCE = "Captura-privada-de-la-respuesta-4471";
    private static final List<String> PERSONAL = List.of("Persona Ficticia", "CC12345", "persona@example.invalid", "3000000000", EVIDENCE);
    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), EPISODE_A = new UUID(1, 0x8B1), PROPOSAL_A = new UUID(1, 0x8C1), EPISODE_B = new UUID(1, 0x8B2),
            PROPOSAL_B = new UUID(1, 0x8C2), EPISODE_C = new UUID(1, 0x8B3), EPISODE_D = new UUID(1, 0x8B4), PROPOSED = new UUID(1, 0x8D1), STAFF = new UUID(0, 0x8E1);
    private static final Instant CANCELLED = NOW.minusSeconds(3_600);
    @Autowired ReschedulingWritesHttpFixture.MemoryEpisodes episodes;
    private ListAppender<ILoggingEvent> logs;
    private Logger own, web;
    private Level ownBefore, webBefore;

    @BeforeEach void plant() throws Exception {
        episodes.clear();
        customers.save(customers.customers.get(customer).linkedTo(ACCOUNT));
        UUID reservationA = reservationOf("logs-resched-0001"), reservationB = reservationOf("logs-resched-0002");
        UUID reservationC = reservationOf("logs-resched-0003"), reservationD = reservationOf("logs-resched-0004");
        episodes.save(ReschedulingEpisode.open(EPISODE_A, tenant, reservationA, departure, CANCELLED, NOW).propose(PROPOSAL_A, PROPOSED, 3, STAFF, NOW, NOW.plusSeconds(864_000)));
        episodes.save(ReschedulingEpisode.open(EPISODE_B, tenant, reservationB, departure, CANCELLED, NOW).propose(PROPOSAL_B, PROPOSED, 3, STAFF, NOW, NOW.plusSeconds(864_000)));
        episodes.save(ReschedulingEpisode.open(EPISODE_C, tenant, reservationC, departure, CANCELLED, NOW));
        episodes.save(ReschedulingEpisode.open(EPISODE_D, tenant, reservationD, departure, CANCELLED, NOW));
        own = (Logger) LoggerFactory.getLogger("co.edu.corhuila");
        web = (Logger) LoggerFactory.getLogger("org.springframework.web");
        ownBefore = own.getLevel();
        webBefore = web.getLevel();
        own.setLevel(Level.TRACE);
        web.setLevel(Level.DEBUG);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        own.setLevel(ownBefore);
        web.setLevel(webBefore);
    }

    private UUID reservationOf(String key) throws Exception {
        var body = createBody(); body.put("customerId", customer.toString());
        return UUID.fromString(CatalogHttpTest.json(exchange(create(body, key, ADMIN), 201)).get("reservationId").asText());
    }

    private MockHttpServletRequestBuilder customerWrite(String path, Map<String, Object> body, String key) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", ACCOUNT.toString()); claims.put("tenantId", tenant.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return post(BASE + "/reschedulings/" + path).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Idempotency-Key", key)
                .header("Authorization", "Bearer " + TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED)).header("X-Correlation-Id", TRACE);
    }

    private MockHttpServletRequestBuilder staffWrite(String path, Map<String, Object> body, String key, String role) throws Exception {
        return withToken(post(BASE + "/reschedulings/" + path), role).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body)).header("Idempotency-Key", key);
    }

    private String answer(MockHttpServletRequestBuilder request, int code) throws Exception {
        return exchange(request, code).getResponse().getContentAsString();
    }

    @Test void theWriteRoutesOfTheReschedulingLeaveNeitherTheEvidenceNorTheCustomerInTheLogsOrTheAnswers() throws Exception {
        // Control: proves the capture sees the application loggers, so an empty scan cannot come from a broken capture.
        LoggerFactory.getLogger("co.edu.corhuila.control").trace("capture-control");
        Map<String, Object> outside = new LinkedHashMap<>();
        outside.put("decision", "REJECT"); outside.put("channel", "WHATSAPP"); outside.put("evidence", EVIDENCE); outside.put("answeredAt", NOW.toString());
        List<String> answers = new ArrayList<>();
        answers.add(answer(staffWrite(EPISODE_A + "/proposals/" + PROPOSAL_A + "/external-answer", outside, "logs-resched-1001", COLLABORATOR), 201));
        answers.add(answer(customerWrite(EPISODE_B + "/proposals/" + PROPOSAL_B + "/answer", Map.of("decision", "ACCEPT"), "logs-resched-1002"), 200));
        answers.add(answer(staffWrite(EPISODE_C + "/proposals", Map.of("proposedDepartureId", PROPOSED.toString()), "logs-resched-1003", ADMIN), 201));
        answers.add(answer(staffWrite(EPISODE_D + "/no-alternative", Map.of(), "logs-resched-1004", ADMIN), 200));
        // The refusals are logged too: a wrong role, a body that does not validate and a missing episode.
        exchange(staffWrite(EPISODE_A + "/proposals/" + PROPOSAL_A + "/external-answer", outside, "logs-resched-1006", MANAGER), 403);
        outside.put("channel", "SMOKE");
        exchange(staffWrite(EPISODE_C + "/proposals/" + PROPOSAL_B + "/external-answer", outside, "logs-resched-1007", ADMIN), 400);
        exchange(staffWrite(new UUID(1, 0x8FF) + "/no-alternative", Map.of(), "logs-resched-1008", ADMIN), 404);

        assertThat(logs.list).anyMatch(event -> event.getFormattedMessage().equals("capture-control"));
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : logs.list) {
            text.append('\n').append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            event.getMDCPropertyMap().values().forEach(value -> text.append('\n').append(value));
        }
        for (String value : PERSONAL) assertThat(text.toString()).as("log: " + value).doesNotContain(value);
        for (String value : PERSONAL) assertThat(String.join("\n", answers)).as("answer: " + value).doesNotContain(value);
    }
}
