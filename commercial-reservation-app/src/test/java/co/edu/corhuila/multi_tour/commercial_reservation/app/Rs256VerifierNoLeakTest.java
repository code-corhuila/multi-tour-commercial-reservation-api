package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.InvalidTokenException;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.Rs256Verifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

// K4 (rubric c3-release): a token that is refused leaves in the log the reason of the refusal and nothing of the token: not the token, not one
// of its three segments, and not a value of its claims, even when the claims are well signed and a library fails on one of them.
@ExtendWith(OutputCaptureExtension.class)
class Rs256VerifierNoLeakTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Rs256Verifier verifier = new Rs256Verifier(TestJwtKeys.TRUSTED.getPublic(), Clock.fixed(TestJwtKeys.NOW, ZoneOffset.UTC), JSON);

    private String signed(Map<String, Object> extra) throws Exception {
        Map<String, Object> claims = TestJwtKeys.claims();
        claims.putAll(extra);
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private void refuses(String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(InvalidTokenException.class);
    }

    private static void assertNothingOfTheTokenIsLogged(String token, CapturedOutput output, String... values) {
        assertThat(output.getAll()).as("the log of a refused token").contains("Access token rejected").doesNotContain(token);
        for (String segment : token.split("\\.")) {
            if (segment.length() > 8) assertThat(output.getAll()).doesNotContain(segment);
        }
        if (values.length > 0) assertThat(output.getAll()).doesNotContain(values);
    }

    @Test void aTextThatIsNotATokenLeavesNoTraceOfItself(CapturedOutput output) {
        String token = "SECRET-TEXT-NOT-A-TOKEN";
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output);
    }

    @Test void aSegmentThatIsNotBase64UrlLeavesNoTraceOfItself(CapturedOutput output) {
        String token = "SECRETHEADER!.SECRETPAYLOAD.SECRETSIGNATURE";
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output, "SECRETHEADER", "SECRETPAYLOAD", "SECRETSIGNATURE");
    }

    @Test void aTokenSignedWithAnotherKeyLeavesNoTraceOfItsSignature(CapturedOutput output) throws Exception {
        String token = TestJwtKeys.token(Map.of("alg", "RS256"), TestJwtKeys.claims(), TestJwtKeys.generate());
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output);
    }

    @Test void aWellSignedTokenWhoseTenantIsNotAnIdentifierLeavesNoValueOfItsClaims(CapturedOutput output) throws Exception {
        String token = signed(Map.of("tenantId", "SECRET-TENANT-VALUE"));
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output, "SECRET-TENANT-VALUE");
    }

    @Test void aWellSignedExceptionalTokenWhoseAuthorizationIsNotAnIdentifierLeavesNoValueOfItsClaims(CapturedOutput output) throws Exception {
        String token = signed(Map.of("access", "exceptional", "scope", List.of("RESERVATIONS"), "authorizationId", "SECRET-AUTHORIZATION-VALUE"));
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output, "SECRET-AUTHORIZATION-VALUE");
    }

    @Test void aWellSignedExceptionalTokenWhoseSubjectIsNotAnIdentifierLeavesNoValueOfItsClaims(CapturedOutput output) throws Exception {
        String token = signed(Map.of("sub", "secret.person@example.invalid", "access", "exceptional", "scope", List.of("RESERVATIONS"),
                "authorizationId", "11111111-1111-1111-1111-111111111111"));
        refuses(token);
        assertNothingOfTheTokenIsLogged(token, output, "secret.person@example.invalid");
    }
}
