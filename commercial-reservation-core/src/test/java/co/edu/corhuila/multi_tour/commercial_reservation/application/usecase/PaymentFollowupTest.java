package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeadlineView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentDeadline;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RecordFollowupCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DeadlineKind;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentFollowup;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-011 and the reads of the plan (section 2): RN-05, RN-11, RN-12; AC-11.5 to AC-11.8, AC-11.10. A follow-up is appended
// with the clock and the token, never changes the deadline and is never edited; the deadline is read by the staff and by the owner
// customer only.
class PaymentFollowupTest {

    private static final UUID TENANT = new UUID(11, 0xd1), ADMIN = new UUID(11, 0xd2), CUSTOMER = new UUID(11, 0xd3), SERVICE = new UUID(11, 0xd4),
            DEPARTURE = new UUID(11, 0xd5), RESERVATION = new UUID(11, 0xd6), OTHER_TENANT = new UUID(11, 0xd7), OTHER_CUSTOMER = new UUID(11, 0xd8);
    private static final Instant CREATED = Instant.parse("2026-11-03T10:00:00Z");
    private static final Instant NOW = CREATED.plus(Duration.ofHours(3));
    private static final Instant DUE = CREATED.plus(Duration.ofHours(24));
    private static final Instant START = DUE.plus(Duration.ofDays(30));

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final FollowupFakes followups = new FollowupFakes();
    private final RecordPaymentFollowupUseCase record = new RecordPaymentFollowupUseCase(fakes, followups, followups, fakes,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final ListPaymentFollowupsUseCase list = new ListPaymentFollowupsUseCase(fakes, followups);
    private final GetPaymentDeadlineUseCase read = new GetPaymentDeadlineUseCase(fakes, fakes, departures);
    private final ListPendingPaymentsUseCase pending = new ListPendingPaymentsUseCase(followups);

    PaymentFollowupTest() {
        departures.plant(new DepartureState(DEPARTURE, TENANT, SERVICE, START, DepartureStatus.SCHEDULED, 10, 0));
        fakes.reservations.put(RESERVATION, Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, CREATED));
        fakes.deadlines.put(RESERVATION, new PaymentDeadline(DeadlineKind.INITIAL_PAYMENT, DUE));
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, ADMIN, role);
    }

    @Test
    void aFollowupWithACustomerReplyLeavesTheDeadlineUnchanged() {
        PaymentDeadline before = fakes.deadlines.get(RESERVATION);

        PaymentFollowup saved = record.record(new RecordFollowupCommand(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION,
                "The customer will pay on Friday"));

        assertThat(saved.reservationId()).isEqualTo(RESERVATION);
        assertThat(fakes.deadlines.get(RESERVATION)).isEqualTo(before);
        assertThat(fakes.saved).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test
    void aFollowupWithoutNoteIsRefusedNamingTheField() {
        for (String note : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> record.record(new RecordFollowupCommand(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, note)))
                    .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.field()).isEqualTo("note"));
        }
        assertThat(followups.followups).isEmpty();
    }

    @Test
    void followupsAreAppendedWithDateTimeAndActorAndNeverChanged() {
        record.record(new RecordFollowupCommand(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, "First call"));

        PaymentFollowup stored = followups.followups.get(0);
        assertThat(stored.contactedAt()).isEqualTo(NOW);
        assertThat(stored.contactedBy()).isEqualTo(ADMIN);
        assertThat(stored.tenantId()).isEqualTo(TENANT);
        assertThat(list.list(actor(Role.MANAGER), RESERVATION, 1, 20).items()).containsExactly(stored);
        assertThat(PaymentFollowup.class.getRecordComponents()).extracting("name").doesNotContain("deadline");
    }

    @Test
    void aFollowupOfAnotherTenantOrOfAReservationNotPendingIsRefused() {
        PaymentActor stranger = new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR);
        assertThatThrownBy(() -> record.record(new RecordFollowupCommand(stranger, RESERVATION, "x")))
                .isInstanceOf(ReservationNotFoundException.class);
        fakes.reservations.get(RESERVATION).registerCash(new UUID(11, 1), new Cents(300_000), null, ADMIN, CREATED);

        assertThatThrownBy(() -> record.record(new RecordFollowupCommand(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, "x")))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(followups.followups).isEmpty();
    }

    @Test
    void onlyAdministratorAndCollaboratorWriteTheOthersOnlyRead() {
        for (Role role : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThatThrownBy(() -> record.record(new RecordFollowupCommand(actor(role), RESERVATION, "x")))
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
        for (Role role : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThat(list.list(actor(role), RESERVATION, 1, 20).total()).isZero();
            assertThat(pending.list(actor(role), null, 1, 20).total()).isZero();
        }
        assertThatThrownBy(() -> list.list(actor(Role.FINAL_CUSTOMER), RESERVATION, 1, 20)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThatThrownBy(() -> pending.list(actor(Role.FINAL_CUSTOMER), null, 1, 20)).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(followups.pendingQueries).hasSize(5);
    }

    @Test
    void aCustomerReadsOnlyTheDeadlineOfAnOwnReservation() {
        DeadlineView view = read.get(actor(Role.ANALYST), RESERVATION);

        assertThat(view.effective()).isEqualTo(DUE);
        assertThat(view.departureStartsAt()).isEqualTo(START);
        assertThat(read.get(new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION).deadline().kind())
                .isEqualTo(DeadlineKind.INITIAL_PAYMENT);
        assertThatThrownBy(() -> read.get(new PaymentActor(TENANT, OTHER_CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> read.get(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
        fakes.deadlines.remove(RESERVATION);
        assertThatThrownBy(() -> read.get(actor(Role.TENANT_ADMINISTRATOR), RESERVATION)).isInstanceOf(ReservationNotFoundException.class);
    }
}
