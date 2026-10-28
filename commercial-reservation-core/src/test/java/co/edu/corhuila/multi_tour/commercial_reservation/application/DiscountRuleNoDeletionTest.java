package co.edu.corhuila.multi_tour.commercial_reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// RN-13 / AC-03.14 is sustained by an absence on three surfaces: aggregate, outbound port and route.
class DiscountRuleNoDeletionTest {

    @Test
    void aggregateExposesNoDeletion() {
        assertThat(DiscountRule.class.getMethods()).extracting(Method::getName)
                .noneMatch(DiscountRuleNoDeletionTest::isDeletionName);
    }

    @Test
    void outboundPortExposesNoDeletion() {
        assertThat(DiscountRuleRepository.class.getMethods()).extracting(Method::getName)
                .noneMatch(DiscountRuleNoDeletionTest::isDeletionName);
    }

    // No HTTP route exists yet (T-06); once a discount-rule adapter appears it must not map DELETE.
    @Test
    void noDiscountRuleRouteMapsDelete() throws IOException {
        Path http = Path.of("..", "commercial-reservation-adapters", "src", "main", "java");
        assertThat(http).isDirectory();
        try (Stream<Path> files = Files.walk(http)) {
            assertThat(files.filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> mentionsDiscountRules(read(f)))
                    .filter(f -> read(f).contains("DeleteMapping") || read(f).contains("RequestMethod.DELETE")))
                    .isEmpty();
        }
    }

    private static boolean mentionsDiscountRules(String source) {
        return source.toLowerCase(Locale.ROOT).contains("discountrule") || source.contains("discount-rules");
    }

    private static String read(Path file) {
        try { return Files.readString(file); } catch (IOException e) { throw new IllegalStateException(e); }
    }

    private static boolean isDeletionName(String name) {
        return name.toLowerCase(Locale.ROOT).matches("^(delete|remove|purge|erase|destroy).*");
    }
}
