package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import java.util.Map;

/** Skeleton so the tests compile; the behavior arrives in the next commit. */
public class AesGcmFieldCipher implements FieldCipher {

    public AesGcmFieldCipher(String currentKeyId, Map<String, String> base64KeysById) {
    }

    public static AesGcmFieldCipher fromConfiguration(String currentKeyId, String keys) {
        return new AesGcmFieldCipher(currentKeyId, Map.of());
    }

    @Override
    public String encrypt(String plain, String associatedData) {
        return plain;
    }

    @Override
    public String decrypt(String envelope, String associatedData) {
        return envelope;
    }
}
