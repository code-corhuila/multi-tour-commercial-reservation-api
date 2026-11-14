package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// Hexagonal layers by a scan of the imports of the sources (HU-COM-010 audit, 2026-11-06; ADR-004, norm 5.2). No library: a class of
// adapter.in.http does not import adapter.out nor the ports ReservationRepository and CustomerRepository, and the core imports no
// Spring, JDBC or Jackson. The known exceptions are listed here, by class and by import: a new violation fails the test, and so does an
// exception that no longer applies, so the list only shrinks. A class of the http package opens a transaction through the port RequestTransaction,
// never with a Spring class (theHttpAdaptersImportNoSpringTransaction).
class ArchitectureRulesTest {
    private static final String ROOT = "co.edu.corhuila.multi_tour.commercial_reservation.";
    private static final String KEYS = ROOT + "adapter.out.persistence.IdempotencyKeys";
    private static final String RESERVATIONS = ROOT + "application.port.out.ReservationRepository";
    private static final String CUSTOMERS = ROOT + "application.port.out.CustomerRepository";

    // class (simple name, in adapter.in.http) -> the forbidden imports it is still allowed to have
    private static final Map<String, Set<String>> KNOWN_HTTP_EXCEPTIONS = Map.of();

    private static final List<String> FORBIDDEN_IN_CORE = List.of("org.springframework.", "java.sql.", "javax.sql.", "com.fasterxml.jackson.",
            "jakarta.", "org.postgresql.", "tools.jackson.");

    private static Path sources(String module) {
        for (String base : List.of("..", ".")) {
            Path path = Path.of(base, module, "src", "main", "java");
            if (Files.isDirectory(path)) return path;
        }
        throw new IllegalStateException("sources of " + module + " not found from " + Path.of("").toAbsolutePath());
    }

    private static List<String> imports(Path file) throws IOException {
        List<String> found = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            String text = line.strip();
            if (!text.startsWith("import ")) continue;
            text = text.substring("import ".length()).replaceFirst("^static ", "");
            found.add(text.endsWith(";") ? text.substring(0, text.length() - 1) : text);
        }
        return found;
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> walk = Files.walk(directory)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static boolean forbiddenForHttp(String imported) {
        return imported.startsWith(ROOT + "adapter.out.") || imported.equals(RESERVATIONS) || imported.equals(CUSTOMERS);
    }

    @Test void theHttpAdaptersImportNoOutAdapterAndNoRepositoryPortBeyondTheKnownExceptions() throws IOException {
        Path http = sources("commercial-reservation-adapters").resolve(ROOT.replace('.', '/') + "adapter/in/http");
        Map<String, Set<String>> found = new TreeMap<>();
        for (Path file : javaFiles(http)) {
            Set<String> violations = new TreeSet<>(imports(file).stream().filter(ArchitectureRulesTest::forbiddenForHttp).toList());
            if (!violations.isEmpty()) found.put(file.getFileName().toString().replace(".java", ""), violations);
        }

        Map<String, Set<String>> unexpected = new TreeMap<>();
        found.forEach((name, imported) -> {
            Set<String> extra = new TreeSet<>(imported);
            extra.removeAll(KNOWN_HTTP_EXCEPTIONS.getOrDefault(name, Set.of()));
            if (!extra.isEmpty()) unexpected.put(name, extra);
        });
        Map<String, Set<String>> stale = new TreeMap<>();
        KNOWN_HTTP_EXCEPTIONS.forEach((name, allowed) -> {
            Set<String> gone = new TreeSet<>(allowed);
            gone.removeAll(found.getOrDefault(name, Set.of()));
            if (!gone.isEmpty()) stale.put(name, gone);
        });

        assertThat(unexpected).as("imports a class of adapter.in.http must not have").isEmpty();
        assertThat(stale).as("known exceptions that no longer apply: remove them from the list").isEmpty();
    }

    @Test void theCoreImportsNoSpringJdbcOrJackson() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        List<Path> coreFiles = javaFiles(sources("commercial-reservation-core"));
        assertThat(coreFiles).hasSizeGreaterThan(100);
        for (Path file : coreFiles) {
            List<String> violations = imports(file).stream().filter(imported -> FORBIDDEN_IN_CORE.stream().anyMatch(imported::startsWith)).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString(), violations);
        }

        assertThat(found).isEmpty();
    }

    // K2 (rubric c3-release): a transaction is a concern of the use cases and of the out adapters, not of the entry layer. A class of
    // adapter.in.http opens one through the port RequestTransaction, never through a Spring transaction class.
    @Test void theHttpAdaptersImportNoSpringTransaction() throws IOException {
        Path http = sources("commercial-reservation-adapters").resolve(ROOT.replace('.', '/') + "adapter/in/http");
        Map<String, List<String>> found = new TreeMap<>();
        for (Path file : javaFiles(http)) {
            List<String> violations = imports(file).stream().filter(imported -> imported.startsWith("org.springframework.transaction.")).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString().replace(".java", ""), violations);
        }

        assertThat(found).as("a class of adapter.in.http must open its transaction through RequestTransaction").isEmpty();
    }

    @Test void theCoreImportsNoAdapter() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        for (Path file : javaFiles(sources("commercial-reservation-core"))) {
            List<String> violations = imports(file).stream().filter(imported -> imported.startsWith(ROOT + "adapter.")).toList();
            if (!violations.isEmpty()) found.put(file.getFileName().toString(), violations);
        }

        assertThat(found).isEmpty();
    }
}
