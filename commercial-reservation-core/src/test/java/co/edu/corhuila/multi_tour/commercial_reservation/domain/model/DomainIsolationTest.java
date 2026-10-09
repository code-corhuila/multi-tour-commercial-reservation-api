package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

// The enforcer rule fails the build first; these tests keep the rule honest if someone weakens it.
class DomainIsolationTest {

    private static final String DOMAIN_PACKAGE = "co.edu.corhuila.multi_tour.commercial_reservation.domain.model";

    @Test
    void springIsNotOnTheCoreClasspath() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.stereotype.Component"));
    }

    @Test
    void aSpringAnnotationOnADomainClassDoesNotCompile() {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        boolean compiled = compile("@org.springframework.stereotype.Component class Leaky {}", diagnostics);

        assertFalse(compiled, "a framework annotation in the domain must not compile");
        assertTrue(diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .anyMatch(d -> d.getMessage(null).contains("org.springframework")));
    }

    @Test
    void theSameClassWithoutTheAnnotationCompiles() {
        // Control: proves the failure above comes from Spring being absent, not from a broken compiler setup.
        assertTrue(compile("class Clean {}", new DiagnosticCollector<>()));
    }

    private static boolean compile(String declaration, DiagnosticCollector<JavaFileObject> diagnostics) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String name = declaration.contains("Leaky") ? "Leaky" : "Clean";
        JavaFileObject source = new SimpleJavaFileObject(
                URI.create("string:///" + DOMAIN_PACKAGE.replace('.', '/') + "/" + name + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return "package " + DOMAIN_PACKAGE + ";\n" + declaration;
            }
        };
        // Compile to a scratch directory so the probe class never lands in target/classes.
        List<String> options = List.of("-proc:none", "-d", System.getProperty("java.io.tmpdir"));
        return compiler.getTask(null, null, diagnostics, options, null, List.of(source)).call();
    }
}
