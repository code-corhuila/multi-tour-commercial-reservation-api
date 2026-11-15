package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import static co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository.time;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingRecord;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-018 plan D-04 of the db (rescheduling_record, with UPDATE withdrawn and DELETE never granted): the agreed change is appended once per episode and never edited; the unique key of the episode
// refuses a second record. Every row names its tenant.
public final class JdbcReschedulingRecords implements ReschedulingRecords {
    private final JdbcTemplate jdbc;

    public JdbcReschedulingRecords(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ReschedulingRecord r) {
        jdbc.update("INSERT INTO commercial_reservation.rescheduling_record (rescheduling_record_id, tenant_id, rescheduling_episode_id, original_reservation_id, linked_reservation_id, agreed_salida_id, "
                + "agreed_start_at, approved_by, recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", r.id(), r.tenantId(), r.episodeId(), r.originalReservationId(), r.linkedReservationId(), r.agreedSalidaId(),
                time(r.agreedStartAt()), r.approvedBy(), time(r.recordedAt()));
    }
}
