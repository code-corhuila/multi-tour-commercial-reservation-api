package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipherException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM field cipher with a versioned envelope {@code v1:<keyId>:<base64(iv||ciphertext||tag)>}.
 * The current key encrypts; older keys, found by id, still decrypt after a rotation.
 * It fails closed and never puts a value or a key in a message or in toString.
 * Configuration (not wired yet, the JDBC slice will do it): FIELD_ENCRYPTION_CURRENT_KEY_ID and
 * FIELD_ENCRYPTION_KEYS as {@code id=base64Key,id2=base64Key2}.
 */
public class AesGcmFieldCipher implements FieldCipher {

    private static final String VERSION = "v1";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String currentKeyId;
    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();

    public AesGcmFieldCipher(String currentKeyId, Map<String, String> base64KeysById) {
        if (base64KeysById == null || base64KeysById.isEmpty()) throw fail("no field encryption key is configured");
        for (Map.Entry<String, String> entry : base64KeysById.entrySet()) {
            if (entry.getKey() == null || !KEY_ID.matcher(entry.getKey()).matches()) {
                throw fail("a field encryption key id is invalid");
            }
            keys.put(entry.getKey(), toKey(entry.getValue()));
        }
        if (currentKeyId == null || !keys.containsKey(currentKeyId)) {
            throw fail("the current field encryption key id is not configured");
        }
        this.currentKeyId = currentKeyId;
    }

    public static AesGcmFieldCipher fromConfiguration(String currentKeyId, String keys) {
        if (keys == null || keys.isBlank()) throw fail("no field encryption key is configured");
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : keys.split(",")) {
            int separator = pair.indexOf('=');
            if (separator < 1) throw fail("the field encryption key list is malformed");
            parsed.put(pair.substring(0, separator).strip(), pair.substring(separator + 1).strip());
        }
        return new AesGcmFieldCipher(currentKeyId, parsed);
    }

    @Override
    public String encrypt(String plain, String associatedData) {
        if (plain == null || associatedData == null) throw fail("a value and its associated data are required");
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(currentKeyId), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return VERSION + ":" + currentKeyId + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException failure) {
            throw fail("the value could not be encrypted");
        }
    }

    @Override
    public String decrypt(String envelope, String associatedData) {
        if (envelope == null || associatedData == null) throw fail("an envelope and its associated data are required");
        String[] parts = envelope.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) throw fail("the envelope version is not supported");
        SecretKeySpec key = keys.get(parts[1]);
        if (key == null) throw fail("the envelope key id is unknown");
        try {
            byte[] payload = Base64.getDecoder().decode(parts[2]);
            if (payload.length < IV_BYTES + TAG_BITS / 8) throw fail("the envelope is malformed");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw fail("the envelope could not be decrypted");
        }
    }

    @Override
    public String toString() {
        return "AesGcmFieldCipher[currentKeyId=" + currentKeyId + "]";
    }

    private static SecretKeySpec toKey(String base64) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64 == null ? "" : base64);
            if (raw.length != KEY_BYTES) throw fail("a field encryption key must be 32 bytes");
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException failure) {
            throw fail("a field encryption key is not valid base64");
        }
    }

    private static FieldCipherException fail(String message) {
        return new FieldCipherException(message);
    }
}
