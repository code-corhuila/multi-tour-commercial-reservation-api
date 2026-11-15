package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationPolicyReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReprogrammingMarkReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

// The doubles of the four ports of HU-COM-013 that have no real adapter yet: the penalty policy (DP-02), the rescheduling mark (HU-COM-018),
// the opening of the financial decision (HU-COM-016) and the stored record.
final class CancellationFakes implements CancellationPolicyReader, ReprogrammingMarkReader, FinancialDecisionOpener, CancellationRecords {

    OptionalInt basisPoints = OptionalInt.empty();
    final Set<UUID> marked = new HashSet<>();
    final List<String> opened = new ArrayList<>();
    final List<Instant> policyAskedAt = new ArrayList<>();
    final Map<UUID, CancellationRecord> records = new HashMap<>();

    @Override
    public OptionalInt penaltyBasisPoints(UUID tenantId, UUID serviceId, Instant departureStart, Instant now) {
        policyAskedAt.add(now);
        return basisPoints;
    }

    @Override
    public boolean isMarked(UUID tenantId, UUID reservationId) {
        return marked.contains(reservationId);
    }

    @Override
    public void open(UUID tenantId, UUID reservationId, Cents base, Cents penalty, Instant recognizedAt) {
        opened.add(base.amount() + "/" + penalty.amount() + " at " + recognizedAt);
    }

    @Override
    public void save(UUID tenantId, CancellationRecord record) {
        records.put(record.reservationId(), record);
    }

    @Override
    public Optional<CancellationRecord> find(UUID tenantId, UUID reservationId) {
        return Optional.ofNullable(records.get(reservationId));
    }
}
