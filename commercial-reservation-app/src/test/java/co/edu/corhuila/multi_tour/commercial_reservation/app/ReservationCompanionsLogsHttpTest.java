package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

// AC-05.16 (RN-13, RNF-03) for the companions: no document, name, blood type or contact reaches a log line or a response
// body, whether the creation succeeds or is refused. Every value below is invented.
class ReservationCompanionsLogsHttpTest extends ReservationHttpBase {
    private static final List<String> SENSITIVE = List.of("CC77777", "Acompanante Reservado", "ZZ-NEG-FICTICIA",
            "Contacto Reservado 3009998877", "Restriccion Reservada", "CC12345");
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach void capture() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach void release() {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private Map<String, Object> companion(String document, boolean complete) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fullName", "Acompanante Reservado");
        item.put("documentNumber", document);
        item.put("age", 33);
        if (complete) {
            item.put("informedConsentSigned", true);
            item.put("emergencyInfo", Map.of("bloodType", "ZZ-NEG-FICTICIA", "emergencyContact", "Contacto Reservado 3009998877",
                    "mobilityRestrictions", "Restriccion Reservada"));
        }
        return item;
    }

    private void assertNothingSensitive(String... answers) {
        var text = new StringBuilder(String.join("\n", answers));
        for (ILoggingEvent event : logs.list) {
            text.append('\n').append(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) text.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            event.getMDCPropertyMap().values().forEach(value -> text.append('\n').append(value));
        }
        for (String value : SENSITIVE) assertThat(text.toString()).doesNotContain(value);
    }

    @Test void aCreatedReservationAndEveryRefusalOfTheGroupLeaveNoPersonalOrSensitiveDataInTheLogsOrTheAnswers() throws Exception {
        var created = createBody(); created.put("companions", List.of(companion("CC77777", true)));
        String ok = exchange(create(created, "logs-key-1", ADMIN), 201).getResponse().getContentAsString();

        var duplicate = createBody(); duplicate.put("companions", List.of(companion("cc12345", true)));
        String twice = exchange(create(duplicate, "logs-key-2", ADMIN), 422).getResponse().getContentAsString();

        var incomplete = createBody(); incomplete.put("companions", List.of(companion("CC77777", false), Map.of("age", 3)));
        String bad = exchange(create(incomplete, "logs-key-3", ADMIN), 400).getResponse().getContentAsString();

        assertThat(logs.list).isNotEmpty();
        assertNothingSensitive(ok, twice, bad);
    }
}
