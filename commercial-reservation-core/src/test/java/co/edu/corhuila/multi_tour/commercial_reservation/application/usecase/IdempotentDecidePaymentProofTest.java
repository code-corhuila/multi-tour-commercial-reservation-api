package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DecidedProof;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RejectProofCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdempotencyStore;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-010 AC-10.12 and RN-11 in the core (audit of 2026-11-06): the key is registered first inside the transaction, a replay answers the
// stored decision only when the body is the stored one, the permission is checked before any replay and a lost key race answers the replay.
class IdempotentDecidePaymentProofTest {

    private static final UUID TENANT = new UUID(12, 1), ADMIN = new UUID(12, 2), CUSTOMER = new UUID(12, 3), SERVICE = new UUID(12, 4),
            DEPARTURE = new UUID(12, 5), RESERVATION = new UUID(12, 6), PROOF = new UUID(12, 7), OTHER_PROOF = new UUID(12, 8);
    private static final Instant REGISTERED = Instant.parse("2026-10-29T10:00:00Z");
    private static final Instant NOW = REGISTERED.plus(Duration.ofHours(3));

    private static final class Keys implements IdempotencyStore {
        final Map<String, UUID> stored = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean loseTheRace;
        String resourceType;

        @Override public Optional<UUID> find(UUID tenantId, String key) {
            calls.add("find");
            return Optional.ofNullable(stored.get(key));
        }

        @Override public boolean register(UUID tenantId, String key, String type, UUID resourceId) {
            calls.add("register");
            resourceType = type;
            if (loseTheRace) { stored.put(key, resourceId); return false; }
            return stored.putIfAbsent(key, resourceId) == null;
        }
    }

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final ReviewFakes review = new ReviewFakes(fakes);
    private final Keys keys = new Keys();
    private final DecidePaymentProofUseCase decide = new DecidePaymentProofUseCase(fakes, fakes, departures, fakes, fakes, review, review, new CashFakes(), fakes,
            fakes, Clock.fixed(NOW, ZoneOffset.UTC));
    private final IdempotentDecidePaymentProofUseCase idempotent = new IdempotentDecidePaymentProofUseCase(decide, fakes, keys, fakes);

    IdempotentDecidePaymentProofTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, NOW.plus(Duration.ofDays(5)), DepartureStatus.SCHEDULED, 10, 2));
        Reservation reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.DEPOSIT, PaymentMethod.TRANSFER, REGISTERED);
        reservation.registerTransfer(PROOF, new Cents(100_000), new Cents(100_000), ADMIN, REGISTERED);
        reservation.registerTransfer(OTHER_PROOF, new Cents(20_000), new Cents(100_000), ADMIN, REGISTERED);
        reservation.allocateSeat(REGISTERED);
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
    }

    private static PaymentActor admin() {
        return new PaymentActor(TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);
    }

    private DecidedProof reject(UUID proof, String reason, String key) {
        return idempotent.reject(new RejectProofCommand(admin(), proof, reason), key);
    }

    @Test void theKeyIsRegisteredBeforeTheDecisionAndTheDecisionIsMade() {
        DecidedProof decided = reject(PROOF, "Illegible proof", "decision-key-001");

        assertThat(keys.calls).containsExactly("find", "register");
        assertThat(keys.stored).containsEntry("decision-key-001", PROOF);
        assertThat(keys.resourceType).isEqualTo("payment-proof-decision");
        assertThat(decided.attempt().validationStatus()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(fakes.saved).hasSize(1);
    }

    @Test void theSameKeyAndTheSameBodyReplayTheStoredDecisionAndWriteNothing() {
        DecidedProof first = reject(PROOF, "Illegible proof", "decision-key-001");
        int savedBefore = fakes.saved.size(), eventsBefore = fakes.events.size();

        DecidedProof again = reject(PROOF, "Illegible proof", "decision-key-001");

        assertThat(again.attempt().decidedAt()).isEqualTo(first.attempt().decidedAt());
        assertThat(fakes.saved).hasSize(savedBefore);
        assertThat(fakes.events).hasSize(eventsBefore);
    }

    @Test void aRejectionReplaysWithTheSameReasonEvenWithSpacesAround() {
        reject(PROOF, "Illegible proof", "decision-key-002");

        assertThat(reject(PROOF, "  Illegible proof ", "decision-key-002").attempt().validationStatus()).isEqualTo(ValidationStatus.REJECTED);
    }

    @Test void theSameKeyWithAnotherReasonIsRefused() {
        reject(PROOF, "Illegible proof", "decision-key-003");

        assertThatThrownBy(() -> reject(PROOF, "Another reason", "decision-key-003")).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void aKeyUsedOnAnotherProofIsRefused() {
        reject(PROOF, "Illegible proof", "decision-key-005");

        assertThatThrownBy(() -> reject(OTHER_PROOF, "Illegible proof", "decision-key-005")).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void thePermissionIsCheckedBeforeAnyReplay() {
        reject(PROOF, "Illegible proof", "decision-key-006");
        PaymentActor collaborator = new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR);
        keys.calls.clear();

        assertThatThrownBy(() -> idempotent.reject(new RejectProofCommand(collaborator, PROOF, "no"), "decision-key-006"))
                .isInstanceOf(PaymentAccessDeniedException.class);

        assertThat(keys.calls).isEmpty();
    }

    @Test void aLostKeyRaceAnswersTheReplayOfTheWinnerAndDecidesNothing() {
        reject(PROOF, "Illegible proof", "decision-key-007"); // the winner of the race, already committed
        keys.stored.remove("decision-key-007");
        keys.loseTheRace = true;
        int savedBefore = fakes.saved.size();

        DecidedProof answer = reject(PROOF, "Illegible proof", "decision-key-007");

        assertThat(answer.attempt().validationStatus()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(fakes.saved).hasSize(savedBefore);
    }

    @Test void aLostKeyRaceWithAnotherBodyIsRefused() {
        reject(PROOF, "Illegible proof", "decision-key-008");
        keys.stored.remove("decision-key-008");
        keys.loseTheRace = true;

        assertThatThrownBy(() -> reject(PROOF, "Another reason", "decision-key-008")).isInstanceOf(BusinessRuleViolation.class);
    }
}
