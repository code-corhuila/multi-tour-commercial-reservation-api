package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// The proof goes to its own table in the transaction of its attempt (ADR-005 E6); the table is never updated and never listed. The
// type judged from the content is the one stored, the file name is never kept, and another tenant reads nothing.
public final class JdbcPaymentProofStore implements PaymentProofStore {
    private final JdbcTemplate jdbc;

    public JdbcPaymentProofStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID tenantId, UUID attemptId, ProofFile proof) {
        jdbc.update("INSERT INTO commercial_reservation.payment_proof_file (payment_proof_file_id, tenant_id, payment_attempt_id, content, "
                + "content_type, size_bytes, sha256) VALUES (?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), tenantId, attemptId, proof.bytes(),
                proof.contentType(), proof.sizeBytes(), proof.sha256());
    }

    @Override
    public Optional<ProofFile> read(UUID tenantId, UUID attemptId) {
        return jdbc.query("SELECT content FROM commercial_reservation.payment_proof_file WHERE tenant_id = ? AND payment_attempt_id = ?",
                (row, index) -> ProofFile.of(row.getBytes("content")), tenantId, attemptId).stream().findFirst();
    }
}
