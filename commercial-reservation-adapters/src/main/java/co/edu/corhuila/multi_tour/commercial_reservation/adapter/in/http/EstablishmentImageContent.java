package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Arrays;

final class EstablishmentImageContent {
    static String type(byte[] bytes) {
        if (bytes.length == 0) throw new DomainException("image", "image is required");
        if (bytes.length > 819200) throw new Rejected(413, "image must not exceed 819200 bytes");
        if (starts(bytes, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (starts(bytes, new byte[] {(byte) 255, (byte) 216, (byte) 255})) return "image/jpeg";
        if (bytes.length >= 12 && starts(bytes, new byte[] {82, 73, 70, 70})
                && Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), new byte[] {87, 69, 66, 80})) return "image/webp";
        throw new Rejected(415, "image content must be PNG, JPEG or WebP");
    }
    private static boolean starts(byte[] bytes, byte[] signature) {
        return bytes.length >= signature.length && Arrays.equals(Arrays.copyOf(bytes, signature.length), signature);
    }
    static final class Rejected extends DomainException {
        final int status;
        Rejected(int status, String message) { super("image", message); this.status = status; }
    }
}
