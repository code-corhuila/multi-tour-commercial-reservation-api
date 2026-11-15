package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalanceRecorded;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

// HU-COM-017 RN-03; plan D-03: the real adapter of the port that HU-COM-010 left as a double. It runs inside the transaction of the proof decision. The excess is one
// credit balance per payment attempt, so a repeat records nothing more; it is never applied by itself. DP: the port carries no person, so the credit is recorded by
// the system actor (the person who approved the proof is in the payment record of that attempt).
public class CreditBalanceRecording implements CreditBalanceRecorder {

    public static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final CreditBalanceRepository credits;
    private final IdGenerator ids;
    private final PaymentEventOutbox outbox;
    private final Clock clock;

    public CreditBalanceRecording(CreditBalanceRepository credits, IdGenerator ids, PaymentEventOutbox outbox, Clock clock) {
        this.credits = credits;
        this.ids = ids;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Override
    public void recordExcess(UUID tenantId, UUID reservationId, UUID attemptId, Cents amount) {
        if (credits.findByAttempt(tenantId, attemptId).isPresent()) return;
        CreditBalance credit = CreditBalance.ofExcess(ids.newId(), tenantId, reservationId, attemptId, amount, SYSTEM_ACTOR, Instant.now(clock));
        credits.save(credit);
        outbox.append(new CreditBalanceRecorded(tenantId, reservationId, credit.recordedAt(), credit.id(), amount, credit.origin(), null));
    }
}
