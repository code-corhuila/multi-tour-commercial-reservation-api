package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// One in-memory store for everything the payment use cases write or read. Every write checks that it runs inside the
// transaction, which is how the tests prove that the attempt, the proof, the hold and the events commit together.
final class PaymentFakes implements ReservationRepository, PaymentDeadlineReader, PaymentConditionReader, SalidaCapacityHolder,
        PaymentProofStore, PaymentEventOutbox, PaymentTransaction {

    final Map<UUID, Reservation> reservations = new HashMap<>();
    final List<Reservation> saved = new ArrayList<>();
    final Map<UUID, ProofFile> proofs = new HashMap<>();
    final List<PaymentEvent> events = new ArrayList<>();
    final Map<UUID, PaymentDeadline> deadlines = new HashMap<>();
    final DepartureFakes departures;
    Cents minimumDeposit = new Cents(100_000);
    int holdCalls;
    private int depth;

    PaymentFakes(DepartureFakes departures) {
        this.departures = departures;
    }

    private void requireTransaction() {
        if (depth == 0) throw new IllegalStateException("a write ran outside the transaction");
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        depth++;
        try {
            return work.get();
        } finally {
            depth--;
        }
    }

    @Override
    public void save(Reservation reservation) {
        requireTransaction();
        reservations.put(reservation.reservationId(), reservation);
        saved.add(reservation);
    }

    @Override
    public Optional<Reservation> findByTenantAndId(UUID tenantId, UUID reservationId) {
        return Optional.ofNullable(reservations.get(reservationId)).filter(found -> found.tenantId().equals(tenantId));
    }

    @Override
    public Optional<PaymentDeadline> openDeadline(UUID tenantId, UUID reservationId) {
        return Optional.ofNullable(deadlines.get(reservationId));
    }

    @Override
    public Optional<Cents> minimumDepositFor(UUID tenantId, Reservation reservation) {
        return Optional.ofNullable(minimumDeposit);
    }

    // The conditional update of ADR-004 D4: held plus requested must stay within the effective capacity.
    @Override
    public boolean hold(UUID tenantId, UUID salidaId, int requested) {
        requireTransaction();
        holdCalls++;
        DepartureState row = departures.rows.get(salidaId);
        if (row == null || !row.tenantId().equals(tenantId)) return false;
        if (row.heldSeats() + requested > row.effectiveCapacity()) return false;
        departures.rows.put(salidaId, new DepartureState(row.departureId(), row.tenantId(), row.serviceId(), row.startsAt(),
                row.status(), row.effectiveCapacity(), row.heldSeats() + requested));
        return true;
    }

    @Override
    public void save(UUID tenantId, UUID attemptId, ProofFile proof) {
        requireTransaction();
        proofs.put(attemptId, proof);
    }

    @Override
    public void append(PaymentEvent event) {
        requireTransaction();
        events.add(event);
    }

    int heldSeats(UUID salidaId) {
        return departures.rows.get(salidaId).heldSeats();
    }
}
