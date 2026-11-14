package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashSessionStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentConditionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentDeadlineReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentParametersChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationCancellation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the payment ports are exercised by their own persistence tests. One store backs every
// port the payment routes need, so a test can seed it and read back what the routes wrote.
@TestConfiguration
class PaymentHttpFixture {
    @Bean @Primary MemoryPayments memoryPayments() { return new MemoryPayments(); }

    static final class MemoryPayments implements PaymentDeadlineReader, PaymentConditionReader, SalidaCapacityHolder, PaymentProofStore,
            PaymentEventOutbox, PaymentTransaction, CashSessionStatus, CashIncomeRecorder, CreditBalanceRecorder, PaymentParameterRepository,
            CancelledValueRecorder, ReservationCancellation, PaymentParametersChangeOutbox {
        final Map<UUID, ProofFile> proofs = new HashMap<>();
        final List<PaymentEvent> events = new ArrayList<>();
        final List<PaymentParameter> parameters = new ArrayList<>();
        final List<PaymentParametersChange> parameterChanges = new ArrayList<>();
        final List<String> income = new ArrayList<>();
        final List<UUID> cancelled = new ArrayList<>();
        Cents minimumDeposit = new Cents(90_000);
        boolean sessionOpen = true, availability = true, cancellationAvailable = true, cashDown;
        PaymentDeadline deadline;

        void clear() {
            proofs.clear(); events.clear(); parameters.clear(); parameterChanges.clear(); income.clear(); cancelled.clear();
            cashDown = false; minimumDeposit = new Cents(90_000); sessionOpen = true; availability = true; cancellationAvailable = true; deadline = null;
        }
        public Optional<PaymentDeadline> openDeadline(UUID tenant, UUID reservation) { return Optional.ofNullable(deadline); }
        public Optional<Cents> minimumDepositFor(UUID tenant, Reservation reservation) { return Optional.ofNullable(minimumDeposit); }
        public boolean hold(UUID tenant, UUID salida, int requested) { return availability; }
        public int release(UUID tenant, UUID reservation) { return 0; }
        public void save(UUID tenant, UUID attempt, ProofFile proof) { proofs.put(attempt, proof); }
        public Optional<ProofFile> read(UUID tenant, UUID attempt) { return Optional.ofNullable(proofs.get(attempt)); }
        public void append(PaymentEvent event) { events.add(event); }
        public void append(PaymentParametersChange change) { parameterChanges.add(change); }
        public <T> T inTransaction(Supplier<T> work) { return work.get(); }
        public boolean isOpen(UUID tenant) { return sessionOpen; }
        public UUID record(UUID tenant, UUID reservation, String intentKey, Cents amount, UUID registeredBy) {
            if (cashDown) throw new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashUnavailableException("the cash does not answer");
            UUID movement = UUID.randomUUID();
            income.add(movement + ":" + amount.amount());
            return movement;
        }
        public void reverse(UUID tenant, UUID movement) { income.removeIf(row -> row.startsWith(movement.toString())); }
        public void recordExcess(UUID tenant, UUID reservation, UUID attempt, Cents amount) { income.add("excess:" + amount.amount()); }
        public void record(UUID tenant, UUID reservation, Cents value, Origin origin) { income.add(origin + ":" + value.amount()); }
        public void cancelForNonPayment(UUID tenant, UUID reservation, Instant now) {
            if (!cancellationAvailable) throw new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation(
                    "the cancellation for non-payment is not available yet");
            cancelled.add(reservation);
        }
        public List<PaymentParameter> findByTenant(UUID tenant) { return List.copyOf(parameters); }
        public void save(UUID tenant, PaymentParameter parameter) { parameters.add(parameter); }
    }
}
