package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipherException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AesGcmFieldCipherTest {

    private static final String AAD = "tenant-1|document";
    private static final String PLAIN = "CC-1075234567";

    private static String key(long seed) {
        byte[] bytes = new byte[32];
        new Random(seed).nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static AesGcmFieldCipher cipher(String current, String... ids) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (String id : ids) keys.put(id, key(id.hashCode()));
        return new AesGcmFieldCipher(current, keys);
    }

    @Test
    void roundTripReturnsTheOriginalValue() {
        AesGcmFieldCipher cipher = cipher("k1", "k1");
        assertThat(cipher.decrypt(cipher.encrypt(PLAIN, AAD), AAD)).isEqualTo(PLAIN);
    }

    @Test
    void envelopeIsVersionedWithTheKeyIdAndHidesThePlainText() {
        String envelope = cipher("k1", "k1").encrypt(PLAIN, AAD);
        assertThat(envelope).startsWith("v1:k1:");
        assertThat(envelope).doesNotContain(PLAIN);
        byte[] raw = Base64.getDecoder().decode(envelope.substring("v1:k1:".length()));
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain(PLAIN);
    }

    @Test
    void sameTextEncryptedTwiceGivesDifferentEnvelopes() {
        AesGcmFieldCipher cipher = cipher("k1", "k1");
        assertThat(cipher.encrypt(PLAIN, AAD)).isNotEqualTo(cipher.encrypt(PLAIN, AAD));
    }

    @Test
    void aTamperedByteFails() {
        AesGcmFieldCipher cipher = cipher("k1", "k1");
        String envelope = cipher.encrypt(PLAIN, AAD);
        byte[] raw = Base64.getDecoder().decode(envelope.substring("v1:k1:".length()));
        raw[raw.length / 2] ^= 1;
        String tampered = "v1:k1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    void differentAssociatedDataFails() {
        AesGcmFieldCipher cipher = cipher("k1", "k1");
        String envelope = cipher.encrypt(PLAIN, AAD);
        assertThatThrownBy(() -> cipher.decrypt(envelope, "tenant-2|document"))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    void aWrongKeyFails() {
        String envelope = cipher("k1", "k1").encrypt(PLAIN, AAD);
        AesGcmFieldCipher other = new AesGcmFieldCipher("k1", Map.of("k1", key(99)));
        assertThatThrownBy(() -> other.decrypt(envelope, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    void anUnknownKeyIdFails() {
        String envelope = cipher("k1", "k1").encrypt(PLAIN, AAD);
        AesGcmFieldCipher other = cipher("k2", "k2");
        assertThatThrownBy(() -> other.decrypt(envelope, AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    void anUnknownVersionOrMalformedEnvelopeFails() {
        AesGcmFieldCipher cipher = cipher("k1", "k1");
        String envelope = cipher.encrypt(PLAIN, AAD);
        assertThatThrownBy(() -> cipher.decrypt("v2" + envelope.substring(2), AAD))
                .isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> cipher.decrypt("garbage", AAD)).isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:k1:AAAA", AAD)).isInstanceOf(FieldCipherException.class);
    }

    @Test
    void decryptsWithAnOldKeyAfterRotatingTheCurrentOne() {
        String oldEnvelope = cipher("k1", "k1").encrypt(PLAIN, AAD);
        AesGcmFieldCipher rotated = cipher("k2", "k1", "k2");
        assertThat(rotated.decrypt(oldEnvelope, AAD)).isEqualTo(PLAIN);
        assertThat(rotated.encrypt(PLAIN, AAD)).startsWith("v1:k2:");
    }

    @Test
    void failsClosedWithoutAUsableConfiguration() {
        assertThatThrownBy(() -> new AesGcmFieldCipher("k1", Map.of())).isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> new AesGcmFieldCipher(null, Map.of("k1", key(1))))
                .isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> new AesGcmFieldCipher("k3", Map.of("k1", key(1))))
                .isInstanceOf(FieldCipherException.class);
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new AesGcmFieldCipher("k1", Map.of("k1", shortKey)))
                .isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> new AesGcmFieldCipher("k1", Map.of("k1", "not base64 !!")))
                .isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> AesGcmFieldCipher.fromConfiguration("k1", "  "))
                .isInstanceOf(FieldCipherException.class);
        assertThatThrownBy(() -> AesGcmFieldCipher.fromConfiguration("k1", null))
                .isInstanceOf(FieldCipherException.class);
    }

    @Test
    void readsTheKeysFromAConfigurationString() {
        AesGcmFieldCipher cipher = AesGcmFieldCipher.fromConfiguration("k2", "k1=" + key(1) + ", k2=" + key(2));
        assertThat(cipher.decrypt(cipher.encrypt(PLAIN, AAD), AAD)).isEqualTo(PLAIN);
        assertThat(cipher.encrypt(PLAIN, AAD)).startsWith("v1:k2:");
    }

    @Test
    void noExceptionAndNoToStringRevealsTheValueOrTheKey() {
        String secretKey = key(1);
        AesGcmFieldCipher cipher = new AesGcmFieldCipher("k1", Map.of("k1", secretKey));
        String envelope = cipher.encrypt(PLAIN, AAD);
        assertThat(cipher.toString()).doesNotContain(secretKey).doesNotContain(PLAIN);
        Throwable wrongAad = catchThrowable(() -> cipher.decrypt(envelope, "x"));
        Throwable shortKey = catchThrowable(
                () -> new AesGcmFieldCipher("k1", Map.of("k1", Base64.getEncoder().encodeToString(new byte[5]))));
        Throwable badFormat = catchThrowable(
                () -> AesGcmFieldCipher.fromConfiguration("k1", "k1=" + secretKey.substring(1)));
        for (Throwable failure : new Throwable[] {wrongAad, shortKey, badFormat}) {
            assertThat(failure).isInstanceOf(FieldCipherException.class);
            assertThat(failure.getMessage()).doesNotContain(PLAIN).doesNotContain(secretKey).doesNotContain(envelope);
        }
    }
}
