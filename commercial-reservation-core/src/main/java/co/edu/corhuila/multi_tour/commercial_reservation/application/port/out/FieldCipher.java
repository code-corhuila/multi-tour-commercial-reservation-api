package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

/**
 * Field-level encryption port. The application encrypts sensitive customer
 * values with it before they reach a repository; the repository only ever
 * stores the returned envelope.
 */
public interface FieldCipher {

    /**
     * @param plain          the value to protect
     * @param associatedData context bound to the value (for example tenantId|field),
     *                       so the envelope cannot be moved to another field or tenant
     * @return an opaque, versioned envelope
     * @throws FieldCipherException when the value cannot be protected
     */
    String encrypt(String plain, String associatedData);

    /**
     * @throws FieldCipherException when the envelope is unknown, tampered with,
     *                              or the associated data does not match
     */
    String decrypt(String envelope, String associatedData);
}
