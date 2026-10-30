package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

// T-02 of HU-COM-009: RN-04, AC-09.4. The file is judged by its first bytes, never by a name or a declared type.
class ProofFileTest {

    private static final int FIVE_MB = 5_242_880;

    private static byte[] withSignature(byte[] signature, int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void aProofIsJudgedFromItsContentNotItsExtension() throws Exception {
        assertThat(ProofFile.of(withSignature(PDF, 100)).contentType()).isEqualTo("application/pdf");
        assertThat(ProofFile.of(withSignature(JPEG, 100)).contentType()).isEqualTo("image/jpeg");
        assertThat(ProofFile.of(withSignature(PNG, 100)).contentType()).isEqualTo("image/png");

        byte[] text = "this is only a text that someone named proof.pdf".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ProofFile.of(text)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("proof"));
    }

    @Test
    void theSha256IsComputedOnceFromTheBytes() throws Exception {
        byte[] bytes = withSignature(PNG, 64);

        ProofFile proof = ProofFile.of(bytes);

        assertThat(proof.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertThat(proof.sizeBytes()).isEqualTo(64);
    }

    @Test
    void aProofAboveFiveMegabytesIsRefusedBeforeStoring() {
        assertThat(ProofFile.of(withSignature(PDF, FIVE_MB)).sizeBytes()).isEqualTo(FIVE_MB);

        assertThatThrownBy(() -> ProofFile.of(withSignature(PDF, FIVE_MB + 1))).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("proof"));
    }

    @Test
    void anEmptyOrMissingProofIsRefusedAndNamed() {
        assertThatThrownBy(() -> ProofFile.of(null)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.field()).isEqualTo("proof"));
        assertThatThrownBy(() -> ProofFile.of(new byte[0])).isInstanceOf(DomainException.class);
    }

    @Test
    void theBytesCannotBeChangedFromOutside() {
        byte[] bytes = withSignature(PDF, 20);
        ProofFile proof = ProofFile.of(bytes);

        bytes[0] = 0;
        proof.bytes()[1] = 0;

        assertThat(Arrays.equals(proof.bytes(), withSignature(PDF, 20))).isTrue();
    }
}
