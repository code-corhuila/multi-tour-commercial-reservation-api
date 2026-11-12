package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ListProofsQuery;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSource;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofSourcePage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ProofView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantStatusReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.TenantStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SuspensionIntervals;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04, T-05 and T-07 of HU-COM-010, the core part: RN-06, RN-07, RN-16; AC-10.6, AC-10.8, AC-10.14, AC-10.17, AC-10.19 and
// AC-09.16 of HU-COM-009. The mark is derived from the clock at read time and is never stored. The internal route for job 2 and
// the roles of the route table are adapter work.
class PaymentProofQueriesTest {

    private static final UUID TENANT = new UUID(10, 0xc1), OTHER_TENANT = new UUID(10, 0xc2), ADMIN = new UUID(10, 0xc3),
            CUSTOMER = new UUID(10, 0xc4), OTHER_CUSTOMER = new UUID(10, 0xc5), SERVICE = new UUID(10, 0xc6), DEPARTURE = new UUID(10, 0xc7),
            RESERVATION = new UUID(10, 0xc8), OLD = new UUID(10, 0xd1), FRESH = new UUID(10, 0xd2), REJECTED = new UUID(10, 0xd3);
    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1};

    private final DepartureFakes departures = new DepartureFakes();
    private final PaymentFakes fakes = new PaymentFakes(departures);
    private final Reservation reservation;
    private final PaymentProofReader reader = new PaymentProofReader() {
        @Override
        public ProofSourcePage find(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore, int page, int limit) {
            List<ProofSource> all = new ArrayList<>();
            for (Reservation each : fakes.reservations.values()) {
                if (!each.tenantId().equals(tenantId)) continue;
                each.attempts().stream().filter(a -> a.validationStatus() == status)
                        .filter(a -> registeredAtOrBefore == null || !a.registeredAt().isAfter(registeredAtOrBefore))
                        .forEach(a -> all.add(new ProofSource(each, a)));
            }
            all.sort(Comparator.comparing((ProofSource s) -> s.attempt().registeredAt()));
            int from = Math.min((page - 1) * limit, all.size());
            return new ProofSourcePage(all.subList(from, Math.min(from + limit, all.size())), all.size());
        }

        @Override
        public long countAtOrBefore(UUID tenantId, ValidationStatus status, Instant registeredAtOrBefore) {
            return find(tenantId, status, registeredAtOrBefore, 1, 1_000).total();
        }
    };
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private TenantStatus tenantStatus = TenantStatus.ACTIVE;
    private final TenantStatusReader statuses = tenant -> TENANT.equals(tenant) ? Optional.of(tenantStatus) : Optional.empty();
    private final ListPaymentProofsUseCase list = new ListPaymentProofsUseCase(reader, statuses, clock);
    private final PaymentProofQueriesUseCase queries = new PaymentProofQueriesUseCase(fakes, clock);
    private static final UUID ACCOUNT = new UUID(10, 0xe1), OTHER_ACCOUNT = new UUID(10, 0xe2), UNLINKED_ACCOUNT = new UUID(10, 0xe3);
    // The token subject of a Final Customer is the account; the customer of the reservation is the one linked to it.
    private final CustomerByAccount accounts = (tenant, account) -> TENANT.equals(tenant) && ACCOUNT.equals(account)
            ? Optional.of(customer(CUSTOMER, ACCOUNT)) : TENANT.equals(tenant) && OTHER_ACCOUNT.equals(account)
            ? Optional.of(customer(OTHER_CUSTOMER, OTHER_ACCOUNT)) : Optional.empty();
    private final ReadPaymentProofFileUseCase file = new ReadPaymentProofFileUseCase(fakes, fakes, accounts);

    private static Customer customer(UUID id, UUID account) {
        return Customer.registerLead(id, TENANT, "CC" + id.getLeastSignificantBits(), "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, NOW).linkedTo(account);
    }

    PaymentProofQueriesTest() {
        // 300.000 pay in full: a proof 24 hours old, one 23 hours 59 minutes old and one rejected.
        reservation = Reservation.create(RESERVATION, TENANT, CUSTOMER, DEPARTURE, ADMIN, 2,
                new ReservedService(SERVICE, new Cents(100_000), new Cents(50_000)), new Cents(300_000), new Cents(300_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.TRANSFER, NOW.minus(Duration.ofDays(3)));
        reservation.registerTransfer(OLD, new Cents(50_000), null, ADMIN, NOW.minus(Duration.ofHours(24)));
        reservation.registerTransfer(FRESH, new Cents(60_000), null, ADMIN, NOW.minus(Duration.ofHours(23)).minusSeconds(3_540));
        reservation.registerTransfer(REJECTED, new Cents(70_000), null, ADMIN, NOW.minus(Duration.ofHours(30)));
        reservation.reject(REJECTED, "illegible", ADMIN, NOW.minus(Duration.ofHours(29)));
        reservation.pullEvents();
        fakes.reservations.put(RESERVATION, reservation);
        fakes.proofs.put(OLD, ProofFile.of(PNG));
    }

    private static PaymentActor actor(Role role) {
        return new PaymentActor(TENANT, ADMIN, role);
    }

    @Test
    void aProofOf24HoursIsMarkedWithoutChangingStateAndOneOf23HoursFiftyNineIsNot() {
        ProofPage page = list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 1, 20));

        assertThat(page.items()).extracting(ProofView::attemptId).containsExactly(OLD, FRESH);
        assertThat(page.items().get(0).reviewOverdue()).isTrue();
        assertThat(page.items().get(0).status()).isEqualTo(ValidationStatus.IN_VALIDATION);
        assertThat(page.items().get(1).reviewOverdue()).isFalse();
        assertThat(page.overdueCount()).isEqualTo(1);
        assertThat(reservation.reservationStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(fakes.saved).isEmpty();
    }

    @Test
    void theOverdueFilterKeepsOnlyTheMarkedOnesAndTheStatusFilterShowsTheRejected() {
        assertThat(list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, true, 1, 20)).items())
                .extracting(ProofView::attemptId).containsExactly(OLD);
        assertThat(list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), ValidationStatus.REJECTED, false, 1, 20)).items())
                .extracting(ProofView::attemptId).containsExactly(REJECTED);
    }

    @Test
    void theListIsPaginatedWithinOneAndAHundred() {
        ProofPage second = list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 2, 1));

        assertThat(second.items()).extracting(ProofView::attemptId).containsExactly(FRESH);
        assertThat(second.total()).isEqualTo(2);
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 1, 101))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("limit"));
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 0, 10))).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.field()).isEqualTo("page"));
    }

    @Test
    void theRolesOfTheMatrixAreTheOnlyOnesAllowedToReadAndAnotherTenantSeesNothing() {
        for (Role reader : List.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR)) {
            assertThat(list.list(new ListProofsQuery(actor(reader), null, false, 1, 20)).items()).hasSize(2);
        }
        assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(Role.FINAL_CUSTOMER), null, false, 1, 20)))
                .isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(list.list(new ListProofsQuery(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), null, false, 1, 20)).items())
                .isEmpty();
    }

    @Test
    void theOverdueListOfJob2TakesTheTenantFromThePathAndKeepsOnlyTheMarkedOnes() {
        ProofPage page = list.overdueOf(TENANT, 1, 20);

        assertThat(page.items()).extracting(ProofView::attemptId).containsExactly(OLD);
        assertThat(list.overdueOf(OTHER_TENANT, 1, 20).items()).isEmpty();
    }

    // AC-20.1, DP-08: the alert is not raised on suspended time, so the overdue list of an inactive tenant is empty, with no count; the page
    // asked is echoed. Active again, the same proofs come back.
    @Test
    void theOverdueListOfAnInactiveTenantIsEmptyAndComesBackWhenItIsActiveAgain() {
        tenantStatus = TenantStatus.INACTIVE;

        ProofPage page = list.overdueOf(TENANT, 2, 5);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.overdueCount()).isZero();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.limit()).isEqualTo(5);
        tenantStatus = TenantStatus.ACTIVE;
        assertThat(list.overdueOf(TENANT, 1, 20).items()).extracting(ProofView::attemptId).containsExactly(OLD);
    }

    @Test
    void theOverdueListOfAnInactiveTenantStillRefusesAnInvalidPage() {
        tenantStatus = TenantStatus.INACTIVE;

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> list.overdueOf(TENANT, 0, 20)).isInstanceOf(DomainException.class);
    }

    // Only job 2 is silenced: the panel list of the Tenant Administrator keeps listing the proofs of an inactive tenant (the review is
    // suspended, the screen is not).
    @Test
    void thePanelListOfAnInactiveTenantStillListsItsProofs() {
        tenantStatus = TenantStatus.INACTIVE;

        ProofPage page = list.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, true, 1, 20));

        assertThat(page.items()).extracting(ProofView::attemptId).containsExactly(OLD);
    }

    @Test
    void theReservationHistoryListsEveryAttemptIncludingTheRejectedOnes() {
        List<ProofView> history = queries.history(actor(Role.OPERATIONAL_COLLABORATOR), RESERVATION);

        assertThat(history).extracting(ProofView::attemptId).containsExactly(OLD, FRESH, REJECTED);
        ProofView rejected = history.get(2);
        assertThat(rejected.status()).isEqualTo(ValidationStatus.REJECTED);
        assertThat(rejected.decisionReason()).isEqualTo("illegible");
        assertThat(rejected.declaredAmount()).isEqualTo(new Cents(70_000));
        assertThat(rejected.verifiedAmount()).isNull();
    }

    @Test
    void aCustomerReadsOnlyTheirOwnReservationAndAnotherTenantOrCustomerAnswersNotFound() {
        assertThat(queries.history(new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION)).hasSize(3);

        assertThatThrownBy(() -> queries.history(new PaymentActor(TENANT, OTHER_CUSTOMER, Role.FINAL_CUSTOMER), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> queries.history(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), RESERVATION))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void oneProofIsReadWithItsAmountsAndAnotherTenantsAnswersNotFound() {
        assertThat(queries.get(actor(Role.TENANT_ADMINISTRATOR), OLD).declaredAmount()).isEqualTo(new Cents(50_000));
        assertThat(queries.get(actor(Role.TENANT_ADMINISTRATOR), OLD).reviewOverdue()).isTrue();

        assertThatThrownBy(() -> queries.get(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), OLD))
                .isInstanceOf(PaymentProofNotFoundException.class);
    }

    @Test
    void theProofFileIsReadOnlyByTheHolderOrTheOwner() {
        assertThat(file.read(actor(Role.TENANT_ADMINISTRATOR), OLD).contentType()).isEqualTo("image/png");
        assertThat(file.read(new PaymentActor(TENANT, ADMIN, Role.OPERATIONAL_COLLABORATOR, Set.of("VALIDATE_PAYMENT_PROOFS")), OLD)).isNotNull();
        assertThat(file.read(new PaymentActor(TENANT, ACCOUNT, Role.FINAL_CUSTOMER), OLD)).isNotNull();

        for (PaymentActor refused : List.of(actor(Role.OPERATIONAL_COLLABORATOR), actor(Role.MANAGER), actor(Role.ACCOUNTANT),
                actor(Role.ANALYST), new PaymentActor(TENANT, OTHER_ACCOUNT, Role.FINAL_CUSTOMER),
                new PaymentActor(TENANT, UNLINKED_ACCOUNT, Role.FINAL_CUSTOMER), new PaymentActor(TENANT, CUSTOMER, Role.FINAL_CUSTOMER))) {
            assertThatThrownBy(() -> file.read(refused, OLD)).isInstanceOf(PaymentAccessDeniedException.class);
        }
    }

    @Test
    void theFileOfAnotherTenantOrWithoutAFileAnswersNotFound() {
        assertThatThrownBy(() -> file.read(new PaymentActor(OTHER_TENANT, ADMIN, Role.TENANT_ADMINISTRATOR), OLD))
                .isInstanceOf(PaymentProofNotFoundException.class);
        assertThatThrownBy(() -> file.read(actor(Role.TENANT_ADMINISTRATOR), FRESH)).isInstanceOf(PaymentProofNotFoundException.class);
    }

    // DP-08, T-11: one hour of inactivity inside the 24 hours of OLD (presented 24 hours ago) puts its due instant one hour ahead; the list, the overdue
    // filter, the counter and the single read agree, and the 23-hour-59-minute proof stays unmarked.
    @Test
    void theOverdueMarkLeavesOutTheTenantsSuspendedHoursInTheListTheFilterAndTheRead() {
        SuspensionIntervals oneHour = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(NOW.minus(Duration.ofHours(12)), NOW.minus(Duration.ofHours(11)))));
        ListPaymentProofsUseCase paused = new ListPaymentProofsUseCase(reader, statuses, tenant -> oneHour, clock);
        PaymentProofQueriesUseCase pausedQueries = new PaymentProofQueriesUseCase(fakes, tenant -> oneHour, clock);

        ProofPage page = paused.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, false, 1, 20));
        assertThat(page.items()).extracting(ProofView::reviewOverdue).containsExactly(false, false);
        assertThat(page.items().get(0).reviewDueAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(page.overdueCount()).isZero();
        assertThat(paused.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, true, 1, 20)).items()).isEmpty();
        assertThat(paused.overdueOf(TENANT, 1, 20).items()).isEmpty();
        assertThat(pausedQueries.get(actor(Role.TENANT_ADMINISTRATOR), OLD).reviewOverdue()).isFalse();
        assertThat(pausedQueries.get(actor(Role.TENANT_ADMINISTRATOR), OLD).reviewDueAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(pausedQueries.history(actor(Role.TENANT_ADMINISTRATOR), RESERVATION)).filteredOn(view -> view.attemptId().equals(OLD))
                .extracting(ProofView::reviewOverdue).containsExactly(false);
    }

    @Test
    void aTenantWithSuspensionsOutsideTheReviewKeepsTheMarkAtTwentyFourHours() {
        SuspensionIntervals before = new SuspensionIntervals(List.of(new SuspensionIntervals.Interval(NOW.minus(Duration.ofDays(9)), NOW.minus(Duration.ofDays(8)))));
        ListPaymentProofsUseCase other = new ListPaymentProofsUseCase(reader, statuses, tenant -> before, clock);

        ProofPage overdue = other.list(new ListProofsQuery(actor(Role.TENANT_ADMINISTRATOR), null, true, 1, 20));

        assertThat(overdue.items()).extracting(ProofView::attemptId).containsExactly(OLD);
        assertThat(overdue.items().get(0).reviewDueAt()).isEqualTo(NOW);
        assertThat(overdue.overdueCount()).isEqualTo(1);
    }

    // HU-COM-010 DP-02 (Project Lead, 2026-11-12): Manager, Accountant and Analyst do not read the proofs, in the list, the detail or the history.
    @Test
    void theManagerTheAccountantAndTheAnalystReadNoProofInTheListTheDetailOrTheHistory() {
        for (Role refused : List.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST)) {
            assertThatThrownBy(() -> list.list(new ListProofsQuery(actor(refused), null, false, 1, 20))).as("list " + refused)
                    .isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> queries.get(actor(refused), OLD)).as("detail " + refused).isInstanceOf(PaymentAccessDeniedException.class);
            assertThatThrownBy(() -> queries.history(actor(refused), RESERVATION)).as("history " + refused)
                    .isInstanceOf(PaymentAccessDeniedException.class);
        }
    }
}
