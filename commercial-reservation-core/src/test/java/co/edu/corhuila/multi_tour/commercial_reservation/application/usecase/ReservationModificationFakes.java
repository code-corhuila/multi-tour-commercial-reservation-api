package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ModificationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationModificationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifications;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The stored history of the modifications, newest first on reading (HU-COM-012 AC-12.6).
final class ReservationModificationFakes implements ReservationModifications {

    final List<ReservationModificationRecord> rows = new ArrayList<>();

    @Override
    public void append(UUID tenantId, ReservationModificationRecord record) {
        rows.add(record);
    }

    @Override
    public Optional<ReservationModificationRecord> find(UUID tenantId, UUID modificationId) {
        return rows.stream().filter(row -> row.modificationId().equals(modificationId)).findFirst();
    }

    @Override
    public ModificationPage page(UUID tenantId, UUID reservationId, int page, int limit) {
        List<ReservationModificationRecord> mine = rows.stream().filter(row -> row.reservationId().equals(reservationId))
                .sorted(Comparator.comparing(ReservationModificationRecord::registeredAt).reversed()).toList();
        return new ModificationPage(mine.stream().skip((long) (page - 1) * limit).limit(limit).toList(), mine.size());
    }
}
