package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SendProposalCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReschedulingRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReschedulingEpisode;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-015 AC-15.5, RN-02, RN-03; DD-6 of spec 018 (PROPOSED default, owner Project Lead): sending a proposal of a new departure writes one notice RESCHEDULING_PROPOSED in the transaction of the
// proposal, triggered by the proposal itself under the name ReschedulingProposed, because no outbox event names it yet. The episode exists only for a departure cancelled for a cause not attributable to
// the customer, so the cause condition of AC-15.5 holds by construction. A proposal that is refused writes nothing. The persistence adapters are covered over the real schema in the app module.
class SendReschedulingProposalNoticeTest {

    private static final UUID TENANT = new UUID(15, 21), ADMIN = new UUID(15, 22), CUSTOMER = new UUID(15, 23), SERVICE = new UUID(15, 24), ORIGINAL = new UUID(15, 25),
            PROPOSED = new UUID(15, 26), RESERVATION = new UUID(15, 27), EPISODE = new UUID(15, 28), PROPOSAL = new UUID(15, 100);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private record Notice(UUID tenantId, UUID reservationId, NoticeType type, String triggeringEventType, UUID triggeringEventId, Instant occurredAt) { }

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Map<UUID, ReschedulingEpisode> stored = new LinkedHashMap<>();
    private final List<Notice> written = new ArrayList<>();
    private final ReschedulingRepository episodes = new ReschedulingRepository() {
        @Override public Optional<ReschedulingEpisode> findByIncident(UUID tenantId, UUID reservationId, UUID originalSalidaId) { return Optional.empty(); }
        @Override public Optional<ReschedulingEpisode> find(UUID tenantId, UUID episodeId) { return Optional.ofNullable(stored.get(episodeId)).filter(e -> e.tenantId().equals(tenantId)); }
        @Override public void save(ReschedulingEpisode episode) { stored.put(episode.id(), episode); }
    };
    private final SalidaCapacityHolder holder = new SalidaCapacityHolder() {
        @Override public boolean hold(UUID tenantId, UUID salidaId, int requested) { return fakes.hold(tenantId, salidaId, requested); }
        @Override public int release(UUID tenantId, UUID reservationId) { return fakes.release(tenantId, reservationId); }
        @Override public boolean holdForRescheduling(UUID tenantId, UUID reservationId, UUID salidaId, int seats) { return fakes.hold(tenantId, salidaId, seats); }
    };
    private final ClientNoticeOutbox notices = (tenantId, reservationId, type, triggeringEventType, triggeringEventId, occurredAt) ->
            written.add(new Notice(tenantId, reservationId, type, triggeringEventType, triggeringEventId, occurredAt));
    private final SendReschedulingProposalUseCase send = new SendReschedulingProposalUseCase(episodes, fakes, departures, holder, fakes, () -> PROPOSAL,
            Clock.fixed(NOW, ZoneOffset.UTC), notices);

    SendReschedulingProposalNoticeTest() {
        departures.plant(new DepartureState(ORIGINAL, TENANT, SERVICE, NOW.plus(Duration.ofDays(30)), DepartureStatus.CANCELLED, 10, 0));
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 7));
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, ORIGINAL, ADMIN, 3, new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)),
                new Cents(450_000), new Cents(450_000), List.of(), PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(2))));
        stored.put(EPISODE, ReschedulingEpisode.open(EPISODE, TENANT, RESERVATION, ORIGINAL, NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(2))));
    }

    private SendProposalCommand command(Role role) {
        return new SendProposalCommand(TENANT, ADMIN, role, EPISODE, PROPOSED);
    }

    @Test
    void aProposalWritesOneNoticeTriggeredByTheProposalItself() {
        send.send(command(Role.TENANT_ADMINISTRATOR));

        assertThat(written).containsExactly(new Notice(TENANT, RESERVATION, NoticeType.RESCHEDULING_PROPOSED, "ReschedulingProposed", PROPOSAL, NOW));
    }

    @Test
    void aProposalWithoutSeatsWritesNoNotice() {
        departures.plant(new DepartureState(PROPOSED, TENANT, SERVICE, NOW.plus(Duration.ofDays(40)), DepartureStatus.SCHEDULED, 10, 8));

        assertThatThrownBy(() -> send.send(command(Role.TENANT_ADMINISTRATOR))).isInstanceOf(BusinessRuleViolation.class);

        assertThat(written).isEmpty();
    }

    @Test
    void aRefusedRoleWritesNoNotice() {
        assertThatThrownBy(() -> send.send(command(Role.MANAGER))).isInstanceOf(PaymentAccessDeniedException.class);

        assertThat(written).isEmpty();
    }
}
