package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

// The proof of a transfer (plan D-03): PDF, JPEG or PNG judged from the first bytes, at most 5 MB. The name is never
// kept and the declared type is never trusted (ADR-005 E6).
public final class ProofFile {

    public static final int MAX_BYTES = 5_242_880;
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final byte[] bytes;
    private final String contentType, sha256;

    private ProofFile(byte[] bytes, String contentType) {
        this.bytes = bytes;
        this.contentType = contentType;
        this.sha256 = digest(bytes);
    }

    public static ProofFile of(byte[] content) {
        if (content == null || content.length == 0) throw new DomainException("proof", "the proof file is required");
        if (content.length > MAX_BYTES) throw new DomainException("proof", "the proof file cannot be larger than 5 MB");
        byte[] copy = content.clone();
        if (startsWith(copy, PDF)) return new ProofFile(copy, "application/pdf");
        if (startsWith(copy, JPEG)) return new ProofFile(copy, "image/jpeg");
        if (startsWith(copy, PNG)) return new ProofFile(copy, "image/png");
        throw new DomainException("proof", "the proof must be a PDF, JPG, JPEG or PNG file");
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        return content.length >= signature.length && Arrays.equals(Arrays.copyOf(content, signature.length), signature);
    }

    private static String digest(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    public byte[] bytes() { return bytes.clone(); }
    public String contentType() { return contentType; }
    public int sizeBytes() { return bytes.length; }
    public String sha256() { return sha256; }
}
