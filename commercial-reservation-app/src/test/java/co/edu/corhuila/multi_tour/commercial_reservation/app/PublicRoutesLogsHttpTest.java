package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

// HU-COM-006 RN-12 / AC-06.15 (logs half): the public routes are called with a search text, a name, a contact and an
// e-mail, and no captured log line (message, arguments, exception) may carry any of them. A characterization test.
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import({CatalogHttpFixture.class, DiscountRuleHttpFixture.class, ReservationHttpFixture.class})
class PublicRoutesLogsHttpTest {
    static final String BASE = PublicToursHttpTest.BASE, IDENTIFIER = PublicToursHttpTest.IDENTIFIER;
    static final String SEARCH = "marcelo-buscado", NAME = "Maria%20Fernanda%20Perez", CONTACT = "3105550199",
            EMAIL = "maria.perez%40example.com";
    // Set to a harmless word to see the test fail (the red proof); the real markers are the four values above.
    static final String MARKER_OVERRIDE = System.getProperty("logs.marker");
    final UUID tenant = UUID.fromString(TestJwtKeys.TENANT), tour = new UUID(2, 1);

    @Autowired MockMvc mvc;
    @Autowired TenantDirectoryHttpFixture.MemoryDirectory directory;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;
    ListAppender<ILoggingEvent> appender;
    Logger root, own;
    Level before;

    @BeforeEach void capture() {
        directory.tenants.clear(); catalog.items.clear();
        directory.tenants.put(IDENTIFIER, new TenantRef(tenant, true));
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Caminata al Mirador",
                CommercialTerms.of(100_000L, 50_000L, 12, false, false), TestJwtKeys.NOW));
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        // The configured levels stay as they are (framework TRACE/DEBUG lines are off in every deployed profile);
        // only the application's own loggers are opened to ALL so that nothing of ours can hide below a level.
        own = (Logger) LoggerFactory.getLogger("co.edu.corhuila");
        before = own.getLevel();
        own.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach void release() { root.detachAppender(appender); own.setLevel(before); }

    void call(String path) throws Exception {
        mvc.perform(get(BASE + path).header("X-Correlation-Id", CatalogHttpTest.TRACE)
                .header("X-Customer-Name", "Maria Fernanda Perez").header("X-Customer-Contact", CONTACT)).andReturn();
    }

    @Test void theLogsOfThePublicRoutesCarryNoPersonalData() throws Exception {
        String personal = "?search=" + SEARCH + "&name=" + NAME + "&contact=" + CONTACT + "&email=" + EMAIL;
        // Control: proves the appender sees the application loggers, so an empty scan cannot be a broken capture.
        LoggerFactory.getLogger("co.edu.corhuila.control").info("capture-control");
        call(IDENTIFIER + "/tours" + personal);
        call(IDENTIFIER + "/tours/" + tour + personal);
        call(IDENTIFIER + "/associated-establishments" + personal);
        call("agencia-desconocida/tours" + personal);
        call("agencia-desconocida/associated-establishments" + personal);
        List<ILoggingEvent> events = List.copyOf(appender.list);
        assertThat(events).as("the capture must see the control line").anyMatch(e -> e.getFormattedMessage().equals("capture-control"));
        List<String> markers = MARKER_OVERRIDE != null ? List.of(MARKER_OVERRIDE)
                : List.of(SEARCH, "Maria", "Perez", "3105550199", "maria.perez", "example.com");
        for (ILoggingEvent event : events) {
            String line = event.getFormattedMessage() + " " + event.getMDCPropertyMap()
                    + (event.getThrowableProxy() == null ? "" : " " + event.getThrowableProxy().getMessage());
            for (String marker : markers) assertThat(line).as("log line of " + event.getLoggerName()).doesNotContain(marker);
        }
    }

    // RN-12 / AC-06.15 (T-08): the answers of the public refusals (400, 404) and the log lines they leave carry no personal datum and no search text,
    // even when the request puts them in the query, in the headers or in the path. The answer is scanned whole: body, headers, error message.
    @Test void theLogsAndTheErrorsCarryNoPersonalData() throws Exception {
        String personal = "?search=" + SEARCH + "&name=" + NAME + "&contact=" + CONTACT + "&email=" + EMAIL;
        String[] refused = {
            IDENTIFIER + "/tours" + personal + "&limit=0",
            IDENTIFIER + "/tours" + personal + "&page=0",
            IDENTIFIER + "/tours/not-a-uuid" + personal,
            IDENTIFIER + "/tours/" + new UUID(9, 9) + personal,
            IDENTIFIER + "/associated-establishments" + personal,
            "agencia-desconocida/tours" + personal,
            "maria.perez%40example.com/tours" + personal,
            "3105550199/associated-establishments" + personal};
        List<String> markers = MARKER_OVERRIDE != null ? List.of(MARKER_OVERRIDE)
                : List.of(SEARCH, "Maria", "Perez", "3105550199", "maria.perez", "example.com");
        LoggerFactory.getLogger("co.edu.corhuila.control").info("capture-control");
        int refusals = 0;
        for (String path : refused) {
            var response = mvc.perform(get(BASE + path).header("X-Correlation-Id", CatalogHttpTest.TRACE)
                    .header("X-Customer-Name", "Maria Fernanda Perez").header("X-Customer-Contact", CONTACT)).andReturn().getResponse();
            if (response.getStatus() < 400) continue;
            refusals++;
            String answer = response.getStatus() + " " + response.getContentAsString() + " " + response.getHeaderNames().stream()
                    .map(name -> name + "=" + response.getHeaders(name)).toList() + " " + response.getErrorMessage();
            for (String marker : markers) assertThat(answer).as("answer of " + path).doesNotContain(marker);
        }
        List<ILoggingEvent> events = List.copyOf(appender.list);
        assertThat(events).as("the capture must see the control line").anyMatch(e -> e.getFormattedMessage().equals("capture-control"));
        for (ILoggingEvent event : events) {
            String line = event.getFormattedMessage() + " " + event.getMDCPropertyMap()
                    + (event.getThrowableProxy() == null ? "" : " " + event.getThrowableProxy().getMessage());
            for (String marker : markers) assertThat(line).as("log line of " + event.getLoggerName()).doesNotContain(marker);
        }
        assertThat(refusals).as("the scan must have seen refusals, not only successes").isGreaterThanOrEqualTo(refused.length - 1);
    }
}
