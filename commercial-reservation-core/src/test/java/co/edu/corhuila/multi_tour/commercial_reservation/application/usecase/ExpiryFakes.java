package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineWriter;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentExtensionPolicy;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// The doubles of HU-COM-011: the maximum extension (the payment condition, DP-05), the write of the extension on the deadline row
// and the treatment of HU-COM-016 (a double until that story exists, DD-4). The deadline row itself lives in PaymentFakes.
final class ExpiryFakes implements PaymentExtensionPolicy, PaymentDeadlineWriter, CancelledValueRecorder {

    private final PaymentFakes store;
    Integer maximumHours = 48;
    final List<String> extensions = new ArrayList<>();
    final List<String> values = new ArrayList<>();

    ExpiryFakes(PaymentFakes store) {
        this.store = store;
    }

    @Override
    public Optional<Integer> maximumHours(UUID tenantId, Reservation reservation) {
        return Optional.ofNullable(maximumHours);
    }

    @Override
    public void saveExtension(UUID tenantId, UUID reservationId, int hours, Instant grantedAt, UUID grantedBy) {
        extensions.add(hours + "h by " + grantedBy + " at " + grantedAt);
        PaymentDeadline current = store.deadlines.get(reservationId);
        store.deadlines.put(reservationId, new PaymentDeadline(current.kind(), current.dueAt(), hours, grantedAt, grantedBy,
                current.frozenAt(), current.remainingAtFreezeMinutes()));
    }

    @Override
    public void record(UUID tenantId, UUID reservationId, Cents value, Origin origin) {
        values.add(origin + ":" + value.amount());
    }
}
