package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EstablishmentImages;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcEstablishmentImages implements EstablishmentImages {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcEstablishmentImages(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public EstablishmentRegistration register(byte[] bytes, String mediaType,
            Supplier<EstablishmentRegistration> registration) {
        return transactions.execute(status -> {
            var result = registration.get();
            if (result.created()) {
                UUID imageId = UUID.fromString(result.association().imageReference());
                jdbc.update("INSERT INTO commercial_reservation.associated_establishment_image "
                        + "(image_id, tenant_id, association_id, content, content_type, size_bytes, sha256) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)", imageId, result.association().tenantId(),
                        result.association().associationId(), bytes, mediaType, bytes.length, sha256(bytes));
            }
            return result;
        });
    }

    @Override
    public Image read(UUID tenantId, String reference) {
        UUID imageId;
        try {
            imageId = UUID.fromString(reference);
        } catch (IllegalArgumentException invalidReference) {
            throw new AssociatedEstablishmentNotFoundException();
        }
        return jdbc.query("SELECT content, content_type FROM commercial_reservation.associated_establishment_image "
                        + "WHERE tenant_id = ? AND image_id = ?",
                (row, index) -> new Image(row.getBytes("content"), row.getString("content_type")),
                tenantId, imageId).stream().findFirst().orElseThrow(AssociatedEstablishmentNotFoundException::new);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
