package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-011 api spec T-08 (RN-12, RNF-03): the follow-up, the single extension, the due list and the expiry run with the application loggers
// at TRACE and the web layer at DEBUG, and no captured log line (message, arguments, MDC, exception), no answer and no event carries the
// name, document, e-mail or telephone of the customer or the text of a follow-up note. Every value is invented.
class PaymentExpiryLogsHttpTest extends ReservationHttpBase {
    private static final String NOTE = "Nota-privada-del-seguimiento-8812";
    private static final List<String> PERSONAL = List.of("Persona Ficticia", "CC12345", "persona@example.invalid", "3000000000", NOTE);
    private final UUID reservationId = new UUID(33, 1);
    @Autowired PaymentDeadlineHttpFixture.MemoryDeadlines deadlines;
    private ListAppender<ILoggingEvent> logs;
    private Logger own, web;
    private Level ownBefore, webBefore;
    private String service;

    @BeforeEach void plant() throws Exception {
        deadlines.clear();
        service = CustomerHttpTest.token(null, "SERVICE", List.of());
        store.reservations.put(reservationId, Reservation.create(reservationId, tenant, customer, departure, customer, 3,
                new ReservedService(tour, new Cents(100_000), new Cents(0)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofHours(2))));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.plus(Duration.ofHours(2)));
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

    private MockHttpServletRequestBuilder write(String path, Map<String, Object> body, String key, String role) throws Exception {
        return withToken(post(BASE + path), role).contentType(MediaType.APPLICATION_JSON).content(TestJwtKeys.JSON.writeValueAsBytes(body))
                .header("Idempotency-Key", key);
    }

    private String answer(MockHttpServletRequestBuilder request, int code) throws Exception {
        return exchange(request, code).getResponse().getContentAsString();
    }

    @Test void theRoutesOfTheExpiryLeaveNoPersonalDataInTheLogsTheAnswersOrTheEvents() throws Exception {
        // Control: proves the capture sees the application loggers, so an empty scan cannot come from a broken capture.
        LoggerFactory.getLogger("co.edu.corhuila.control").trace("capture-control");
        String path = "/reservations/" + reservationId;
        List<String> answers = new java.util.ArrayList<>();
        answers.add(answer(write(path + "/payment-followups", Map.of("note", NOTE), "logs-expiry-001", COLLABORATOR), 201));
        answers.add(answer(withToken(get(BASE + path + "/payment-followups"), ADMIN), 200));
        answers.add(answer(write(path + "/payment-deadline/extension", Map.of("hours", 5), "logs-expiry-002", ADMIN), 201));
        answers.add(answer(withToken(get(BASE + path + "/payment-deadline"), ADMIN), 200));
        payments.deadline = new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, NOW.minus(Duration.ofHours(10)));
        deadlines.due.add(new DeadlineView(reservationId, payments.deadline, NOW.plusSeconds(86_400), NOW.minus(Duration.ofHours(1))));
        answers.add(answer(get(BASE + "/internal/tenants/" + tenant + "/payment-deadlines?due=true&limit=10")
                .header("Authorization", "Bearer " + service).header("X-Correlation-Id", TRACE), 200));
        answers.add(answer(post(BASE + "/internal/tenants/" + tenant + "/reservations/" + reservationId + "/expiry")
                .header("Authorization", "Bearer " + service).header("X-Correlation-Id", TRACE).header("Idempotency-Key", "logs-expiry-003"), 200));

        assertThat(logs.list).anyMatch(event -> event.getFormattedMessage().equals("capture-control"));
        StringBuilder text = new StringBuilder(String.valueOf(payments.events));
        for (ILoggingEvent event : logs.list) {
            text.append('\n').append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            event.getMDCPropertyMap().values().forEach(value -> text.append('\n').append(value));
        }
        for (String value : PERSONAL) assertThat(text.toString()).as(value).doesNotContain(value);
        // The answers of the follow-up routes return the note to the staff by design; no answer may carry the customer.
        for (String value : PERSONAL.subList(0, 4)) assertThat(String.join("\n", answers)).as(value).doesNotContain(value);
    }
}
