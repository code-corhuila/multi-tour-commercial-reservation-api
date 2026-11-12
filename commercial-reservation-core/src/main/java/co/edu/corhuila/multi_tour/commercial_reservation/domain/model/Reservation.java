package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Aggregate root of the Reservations context (plan D-02). It is born PENDING_PAYMENT, NO_PAYMENT, with the pending balance
// equal to the final value and no seat held (RN-RES-006, RN-RES-007). HU-COM-009 adds the payment attempts as children:
// registered is not validated, and the financial situation moves only with validated values (RN-01, RN-02).
public final class Reservation {
    private final UUID reservationId, tenantId, customerId, createdBy;
    private UUID departureId;
    private int people;
    private ReservedService service;
    private Cents projectedValue, finalValue;
    private List<FixedDiscount> fixedDiscounts;
    private final PaymentType expectedPaymentType;
    private final PaymentMethod expectedPaymentMethod;
    private final Instant createdAt;
    private List<Companion> companions;
    private final List<PaymentAttempt> attempts = new ArrayList<>();
    private final List<PaymentEvent> events = new ArrayList<>();
    private ReservationStatus reservationStatus = ReservationStatus.PENDING_PAYMENT;
    private PaymentStatus paymentStatus = PaymentStatus.NO_PAYMENT;
    private Cents pendingBalance;
    private boolean seatHeld;
    private CancellationCause cancellationCause;
    private Instant cancelledAt;
    private UUID cancelledBy;

    private Reservation(UUID id, UUID tenant, UUID customer, UUID departure, UUID creator, int people, ReservedService service,
            Cents projected, Cents finalValue, List<FixedDiscount> discounts, PaymentType type, PaymentMethod method, Instant now, List<Companion> companions) {
        reservationId = id; tenantId = tenant; customerId = customer; departureId = departure; createdBy = creator;
        this.people = people; this.service = service; projectedValue = projected; this.finalValue = finalValue;
        fixedDiscounts = List.copyOf(discounts); expectedPaymentType = type; expectedPaymentMethod = method; createdAt = now; this.companions = List.copyOf(companions);
        pendingBalance = finalValue;
    }

    public static Reservation create(UUID id, UUID tenant, UUID customer, UUID departure, UUID creator, int people,
            ReservedService service, Cents projected, Cents finalValue, List<FixedDiscount> discounts, PaymentType type,
            PaymentMethod method, Instant now) {
        return create(id, tenant, customer, departure, creator, people, service, projected, finalValue, discounts, type, method,
                now, List.of());
    }

    public static Reservation create(UUID id, UUID tenant, UUID customer, UUID departure, UUID creator, int people,
            ReservedService service, Cents projected, Cents finalValue, List<FixedDiscount> discounts, PaymentType type,
            PaymentMethod method, Instant now, List<Companion> companions) {
        var missing = new ArrayList<String>();
        if (id == null) missing.add("reservationId");
        if (tenant == null) missing.add("tenantId");
        if (customer == null) missing.add("customerId");
        if (departure == null) missing.add("salidaId");
        if (service == null) missing.add("serviceId");
        if (people < 1) missing.add("people");
        if (creator == null) missing.add("createdBy");
        if (type == null) missing.add("paymentType");
        if (method == null) missing.add("paymentMethod");
        if (now == null) missing.add("createdAt");
        if (!missing.isEmpty()) throw new MissingReservationDataException(missing);
        List<Companion> group = companions == null ? List.of() : companions;
        if (group.size() > people - 1)
            throw new DomainException("companions", "the companions must fit in the people left by the lead customer");
        if (group.stream().map(companion -> companion.documentNumber().comparable()).distinct().count() != group.size())
            throw new BusinessRuleViolation("a document is already in the group");
        if (projected == null || !projected.equals(service.projectedFor(people)))
            throw new DomainException("projectedValue", "projectedValue must be the tour plus the transport of every person");
        List<FixedDiscount> lines = discounts == null ? List.of() : discounts;
        Cents left = DiscountChain.lastSubtotal(projected, lines);
        if (finalValue == null || !finalValue.equals(left))
            throw new DomainException("finalValue", "finalValue must be what the discounts leave of the projected value");
        return new Reservation(id, tenant, customer, departure, creator, people, service, projected, finalValue, lines, type, method, now, group);
    }

    public UUID reservationId() { return reservationId; }
    public UUID tenantId() { return tenantId; }
    public UUID customerId() { return customerId; }
    public UUID departureId() { return departureId; }
    public UUID createdBy() { return createdBy; }
    public int people() { return people; }
    public ReservedService service() { return service; }
    public Cents projectedValue() { return projectedValue; }
    public Cents finalValue() { return finalValue; }

    // AC-04.4, DP-01: the registered additional discount takes its amount from the final value the charge reads. The fixed lines stay
    // as they were (AC-04.12); a later modification recalculates from them and does not carry this discount (declared limit).
    public Reservation withAdditionalDiscount(Cents amount) {
        finalValue = new Cents(finalValue.amount() - amount.amount());
        return this;
    }
    public Cents pendingBalance() { return pendingBalance; }
    public Cents creditBalance() { return new Cents(0); }
    public List<FixedDiscount> fixedDiscounts() { return fixedDiscounts; }
    public ReservationStatus reservationStatus() { return reservationStatus; }
    public PaymentStatus paymentStatus() { return paymentStatus; }
    public boolean seatHeld() { return seatHeld; }
    public List<PaymentAttempt> attempts() { return List.copyOf(attempts); }
    public PaymentType expectedPaymentType() { return expectedPaymentType; }
    public PaymentMethod expectedPaymentMethod() { return expectedPaymentMethod; }
    public Instant createdAt() { return createdAt; }
    public List<Companion> companions() { return companions; }

    // The stored state of a reservation, for the persistence adapter: the same birth data, then what happened after.
    public Reservation restoredAs(ReservationStatus status, PaymentStatus payment, Cents pending, boolean held, List<PaymentAttempt> stored) {
        reservationStatus = status; paymentStatus = payment; pendingBalance = pending; seatHeld = held;
        attempts.clear();
        attempts.addAll(stored);
        return this;
    }

    // RN-17: payments are taken on a pending-payment or confirmed reservation that still has a balance.
    public void requireOpenToPayment() {
        if (reservationStatus != ReservationStatus.PENDING_PAYMENT && reservationStatus != ReservationStatus.CONFIRMED)
            throw new BusinessRuleViolation("the reservation does not accept payments in its current state");
        if (pendingBalance.amount() == 0) throw new BusinessRuleViolation("the reservation has no pending balance");
    }

    // RN-01: a transfer is born in validation and changes neither the status nor the situation. DP-05 (decided 2026-11-11, PDR
    // v1.9): the amount is registered as declared, even below the minimum deposit or above the pending balance; the minimum only
    // conditions the confirmation and the excess is settled at validation (RN-RES-006A, CA-015A).
    public PaymentAttempt registerTransfer(UUID attemptId, Cents amount, Cents minimumDeposit, UUID registeredBy, Instant now) {
        requireOpenToPayment();
        requireAmount(amount, minimumDeposit);
        PaymentAttempt attempt = new PaymentAttempt(attemptId, PaymentMethod.TRANSFER, amount, ValidationStatus.IN_VALIDATION, paymentStatus, registeredBy, now);
        attempts.add(attempt);
        events.add(new PaymentEvidenceRegistered(tenantId, reservationId, attemptId, PaymentMethod.TRANSFER, amount, ValidationStatus.IN_VALIDATION, now));
        return attempt;
    }

    // DP-01: cash is money received, validated at once; below the minimum it is registered and the reservation stays Parcial, and
    // the excess over the balance is returned for the credit balance, never dropped (DP-05, decided 2026-11-11).
    public CashReceipt registerCash(UUID attemptId, Cents amount, Cents minimumDeposit, UUID registeredBy, Instant now) {
        requireOpenToPayment();
        requireAmount(amount, minimumDeposit);
        PaymentAttempt attempt = new PaymentAttempt(attemptId, PaymentMethod.CASH, amount, null, paymentStatus, registeredBy, now);
        attempts.add(attempt);
        events.add(new PaymentEvidenceRegistered(tenantId, reservationId, attemptId, PaymentMethod.CASH, amount, null, now));
        long applied = Math.min(amount.amount(), pendingBalance.amount());
        recordValidatedPayment(attemptId, new Cents(applied), minimumDeposit, registeredBy, now);
        return new CashReceipt(attempt, new Cents(applied), new Cents(amount.amount() - applied));
    }

    // HU-COM-017 RN-04: a credit balance applied is a validated payment with no attempt, no proof and no cash; it never exceeds the pending balance.
    public void applyCreditBalance(Cents amount, Cents minimumDeposit, UUID by, Instant now) {
        requireOpenToPayment();
        if (amount == null || amount.amount() <= 0) throw new DomainException("amountCents", "amountCents must be greater than zero");
        if (amount.amount() > pendingBalance.amount()) throw new BusinessRuleViolation("the amount is above the pending balance of the reservation");
        recordValidatedPayment(null, amount, minimumDeposit, by, now);
    }

    // The one place that adds a validated amount (HU-COM-010 reuses it for an approved transfer): the balance goes down, the
    // situation is recomputed and the reservation is confirmed when the condition of its payment type is met.
    private void recordValidatedPayment(UUID attemptId, Cents applied, Cents minimumDeposit, UUID validator, Instant now) {
        pendingBalance = new Cents(pendingBalance.amount() - applied.amount());
        Cents validated = new Cents(finalValue.amount() - pendingBalance.amount());
        boolean confirms = reservationStatus == ReservationStatus.PENDING_PAYMENT
                && FinancialSituation.meetsConfirmation(finalValue, validated, expectedPaymentType, minimumDeposit);
        if (confirms) reservationStatus = ReservationStatus.CONFIRMED;
        paymentStatus = FinancialSituation.of(finalValue, validated, expectedPaymentType, minimumDeposit, reservationStatus);
        events.add(new PaymentValidated(tenantId, reservationId, attemptId, applied, paymentStatus, pendingBalance, validator, now));
        if (confirms) events.add(new ReservationConfirmed(tenantId, reservationId, paymentStatus, people, now));
    }

    private void requireAmount(Cents amount, Cents minimumDeposit) {
        if (amount == null || amount.amount() <= 0) throw new DomainException("amountCents", "amountCents must be greater than zero");
        if (expectedPaymentType == PaymentType.DEPOSIT && minimumDeposit == null)
            throw new DomainException("minimumDeposit", "a deposit needs its minimum");
    }

    // HU-COM-010 RN-01 to RN-04, RN-08, RN-10, RN-13: the reviewer sets the verified amount. Only the pending balance is applied
    // and the excess is returned for the credit balance; on a cancelled reservation nothing is applied, the reservation is not
    // reactivated and the whole amount is for treatment. The reason is mandatory when the amount differs from the declared one.
    public ProofDecision approve(UUID attemptId, Cents verified, String reason, Cents minimumDeposit, UUID reviewer, Instant now) {
        PaymentAttempt attempt = requireInValidation(attemptId);
        if (verified == null || verified.amount() <= 0)
            throw new DomainException("verifiedAmountCents", "verifiedAmountCents must be greater than zero");
        String why = reason == null || reason.isBlank() ? null : reason.strip();
        if (why == null && !verified.equals(attempt.amount()))
            throw new DomainException("reason", "a reason is required when the verified amount differs from the declared one");
        boolean cancelled = reservationStatus == ReservationStatus.CANCELLED;
        long applied = cancelled ? 0 : Math.min(verified.amount(), pendingBalance.amount());
        long excess = cancelled ? 0 : verified.amount() - applied;
        long treatment = cancelled ? verified.amount() : 0;
        PaymentAttempt decided = attempt.approvedWith(reviewer, now, why, verified, new Cents(applied), new Cents(excess), new Cents(treatment));
        attempts.set(attempts.indexOf(attempt), decided);
        if (cancelled) events.add(new PaymentValidated(tenantId, reservationId, attemptId, verified, paymentStatus, pendingBalance, reviewer, now));
        else recordValidatedPayment(attemptId, new Cents(applied), minimumDeposit, reviewer, now);
        return new ProofDecision(decided, new Cents(applied), new Cents(excess), new Cents(treatment));
    }

    // RN-RES-006D: a rejection keeps the history and moves nothing else; the reason is mandatory (RN-04).
    public PaymentAttempt reject(UUID attemptId, String reason, UUID reviewer, Instant now) {
        PaymentAttempt attempt = requireInValidation(attemptId);
        if (reason == null || reason.isBlank()) throw new DomainException("reason", "a reason is required to reject a proof");
        PaymentAttempt decided = attempt.rejectedWith(reviewer, now, reason.strip());
        attempts.set(attempts.indexOf(attempt), decided);
        events.add(new PaymentRejected(tenantId, reservationId, attemptId, pendingBalance, reviewer, now));
        return decided;
    }

    private PaymentAttempt requireInValidation(UUID attemptId) {
        PaymentAttempt attempt = attempts.stream().filter(found -> found.attemptId().equals(attemptId)).findFirst()
                .orElseThrow(() -> new DomainException("attemptId", "the payment attempt does not belong to the reservation"));
        if (attempt.validationStatus() != ValidationStatus.IN_VALIDATION)
            throw new InvalidStatusTransitionException(attempt.validationStatus() == null
                    ? "cash is not a proof to review" : "the proof has already been decided and the decision is final");
        return attempt;
    }

    // RN-RES-007 (CONFIRMADO) with the extra condition PROPOSED in 010 AC-10.16: the first payment, before the confirmation, when
    // no other attempt is under review or validated.
    public boolean shouldReleaseSeatAfterRejection() {
        return seatHeld && reservationStatus == ReservationStatus.PENDING_PAYMENT && attempts.stream()
                .noneMatch(a -> a.method() == PaymentMethod.CASH || a.validationStatus() == ValidationStatus.IN_VALIDATION
                        || a.validationStatus() == ValidationStatus.APPROVED);
    }

    // The use case asked the capacity row to release; zero seats released means this is not called and no event is written.
    public void releaseSeat(String reason, Instant now) {
        if (!seatHeld) throw new BusinessRuleViolation("the reservation holds no seats");
        seatHeld = false;
        events.add(new CapacityReleased(tenantId, reservationId, service.serviceId(), departureId, reason, now));
    }

    // RN-RES-007: the first payment holds the seats; the use case asked the capacity row before calling this.
    public void allocateSeat(Instant now) {
        if (seatHeld) throw new BusinessRuleViolation("the seats of the reservation are already held");
        seatHeld = true;
        events.add(new CapacityAllocated(tenantId, reservationId, service.serviceId(), departureId, CapacityAllocated.RESERVATION_KIND, now));
    }

    // HU-COM-018 RN-01, AC-18.1: a date-only rescheduling keeps the same reservation, its payments and its history; only the departure changes, the seats are held there (the hold of the
    // proposal became the seat) and an IN_EXECUTION reservation returns to CONFIRMED. A reservation that is not alive cannot be rescheduled.
    public void rescheduledTo(UUID newDeparture) {
        if (reservationStatus != ReservationStatus.PENDING_PAYMENT && reservationStatus != ReservationStatus.CONFIRMED && reservationStatus != ReservationStatus.IN_EXECUTION)
            throw new InvalidStatusTransitionException("only a pending-payment, confirmed or in-execution reservation can be rescheduled");
        if (newDeparture == null) throw new DomainException("salidaId", "the new departure is required");
        departureId = newDeparture;
        seatHeld = true;
        if (reservationStatus == ReservationStatus.IN_EXECUTION) reservationStatus = ReservationStatus.CONFIRMED;
    }

    // The stored cause of a cancelled reservation, for the persistence adapter that reads it back (HU-COM-014 AC-14.9).
    public Reservation restoredCancellation(CancellationCause cause) {
        cancellationCause = cause;
        return this;
    }

    public CancellationCause cancellationCause() { return cancellationCause; }
    public Instant cancelledAt() { return cancelledAt; }
    public UUID cancelledBy() { return cancelledBy; }

    // HU-COM-011 RN-06: the one place that moves a reservation to CANCELLED, with its cause, instant and actor (null when the system
    // acted). Terminal: a cancelled reservation refuses it, and so does the engine on any later update. The use case released the seats.
    public void cancel(CancellationCause cause, UUID by, Instant now, int releasedSeats) {
        if (cause == null || now == null) throw new MissingReservationDataException(List.of("cause", "cancelledAt"));
        if (reservationStatus == ReservationStatus.CANCELLED) throw new InvalidStatusTransitionException("the reservation is already cancelled");
        boolean interrupted = reservationStatus == ReservationStatus.IN_EXECUTION && cause == CancellationCause.NOT_ATTRIBUTABLE_TO_CUSTOMER; // HU-COM-018 AC-18.7: an interrupted service may end cancelled
        if (reservationStatus != ReservationStatus.PENDING_PAYMENT && reservationStatus != ReservationStatus.CONFIRMED && !interrupted)
            throw new InvalidStatusTransitionException("a reservation in execution or completed cannot be cancelled");
        reservationStatus = ReservationStatus.CANCELLED;
        cancellationCause = cause; cancelledAt = now; cancelledBy = by;
        events.add(new ReservationCancelled(tenantId, reservationId, cause, releasedSeats, now));
    }

    // RN-04, RN-08, RN-09; AC-11.1, AC-11.4, AC-11.13: may the job cancel it for non-payment at this instant? The deadline is closed at
    // both ends: the exact instant is due, and evidence recorded at that very instant still prevails. Cash, a validated payment, or a
    // transfer presented in time and still in validation stop the expiry; for a balance deadline only a settled balance does.
    public ExpiryVerdict expiryVerdict(DeadlineKind kind, Instant effectiveDeadline, boolean frozen, Instant now) {
        if (reservationStatus == ReservationStatus.CANCELLED) return ExpiryVerdict.ALREADY_CANCELLED;
        if (frozen) return ExpiryVerdict.FROZEN;
        if (now.isBefore(effectiveDeadline)) return ExpiryVerdict.NOT_DUE;
        boolean paid = kind == DeadlineKind.INITIAL_PAYMENT
                ? reservationStatus != ReservationStatus.PENDING_PAYMENT || attempts.stream().anyMatch(a -> a.method() == PaymentMethod.CASH)
                : reservationStatus != ReservationStatus.CONFIRMED || pendingBalance.amount() == 0;
        if (paid) return ExpiryVerdict.PAID;
        boolean proofInTime = attempts.stream().anyMatch(a -> a.validationStatus() == ValidationStatus.IN_VALIDATION
                && !a.registeredAt().isAfter(effectiveDeadline));
        return proofInTime ? ExpiryVerdict.PROOF_IN_VALIDATION : ExpiryVerdict.EXPIRABLE;
    }

    // HU-COM-012 RN-01, RN-02, RN-12: the one place that changes people, service, departure, value and balance. The lines fixed at creation
    // are recomputed in order from their copied type and value, never from the rules of today (RN-RES-003). Validated payments stay:
    // pending = max(0, new final - validated), and what the validated amount exceeds the new value is the excess.
    public ModificationOutcome modify(int newPeople, ReservedService newService, UUID newDeparture, List<Companion> newCompanions,
            Instant departureStart, Instant now) {
        ModificationPlan plan = plan(newPeople, newService, newDeparture, newCompanions, departureStart, now);
        fixedDiscounts = plan.lines(); people = newPeople; service = newService; departureId = newDeparture; companions = plan.group();
        projectedValue = plan.projected(); finalValue = plan.outcome().newFinalValue(); pendingBalance = plan.outcome().newPendingBalance();
        return plan.outcome();
    }

    // AC-12.2: the same computation and the same refusals, kept nowhere: the reservation is left as it was.
    public ModificationOutcome quoteModification(int newPeople, ReservedService newService, UUID newDeparture, List<Companion> newCompanions,
            Instant departureStart, Instant now) {
        return plan(newPeople, newService, newDeparture, newCompanions, departureStart, now).outcome();
    }

    private record ModificationPlan(List<FixedDiscount> lines, List<Companion> group, Cents projected, ModificationOutcome outcome) {
    }

    private ModificationPlan plan(int newPeople, ReservedService newService, UUID newDeparture, List<Companion> newCompanions,
            Instant departureStart, Instant now) {
        if (reservationStatus != ReservationStatus.PENDING_PAYMENT && reservationStatus != ReservationStatus.CONFIRMED)
            throw new InvalidStatusTransitionException("only a pending-payment or confirmed reservation can be modified");
        if (departureStart == null || now == null || !now.isBefore(departureStart))
            throw new InvalidStatusTransitionException("a reservation whose departure has started cannot be modified");
        if (newPeople < 1) throw new DomainException("people", "people must be at least one");
        if (newService == null || newDeparture == null) throw new MissingReservationDataException(List.of("serviceId", "salidaId"));
        List<Companion> group = newCompanions == null ? List.of() : newCompanions;
        if (group.size() > newPeople - 1)
            throw new DomainException("companions", "the companions must fit in the people left by the lead customer");
        if (group.stream().map(companion -> companion.documentNumber().comparable()).distinct().count() != group.size())
            throw new BusinessRuleViolation("a document is already in the group");
        Cents projected = newService.projectedFor(newPeople);
        long previous = projected.amount();
        List<FixedDiscount> lines = new ArrayList<>();
        for (FixedDiscount line : fixedDiscounts) {
            Cents base = new Cents(line.calculationBase() == DiscountBase.ORIGINAL_VALUE ? projected.amount() : previous);
            DiscountLine raw = line.percentageBasisPoints() != null
                    ? DiscountLine.percentage(base, new BasisPoints(line.percentageBasisPoints()))
                    : DiscountLine.fixedAmount(base, new Cents(line.amountCents()));
            long amount = Math.min(Math.min(raw.amount().amount(), line.capCents() == null ? Long.MAX_VALUE : line.capCents()), previous);
            previous -= amount;
            lines.add(new FixedDiscount(line.order(), line.ruleId(), line.ruleName(), line.type(), line.percentageBasisPoints(),
                    line.amountCents(), line.calculationBase(), base, new Cents(amount), new Cents(previous), line.capCents()));
        }
        Cents oldFinal = finalValue, oldPending = pendingBalance;
        long validated = oldFinal.amount() - oldPending.amount();
        Cents newFinal = new Cents(previous);
        Cents newPending = new Cents(Math.max(0, newFinal.amount() - validated));
        Cents excess = new Cents(Math.max(0, validated - newFinal.amount()));
        int seatDelta = newPeople - people;
        return new ModificationPlan(List.copyOf(lines), List.copyOf(group), projected,
                new ModificationOutcome(oldFinal, newFinal, oldPending, newPending, excess, seatDelta));
    }

    // HU-COM-012 RN-04, AC-12.3, AC-12.14: the situation after a modification, from validated values only. A confirmed reservation stays
    // confirmed and needs no new minimum; a pending one is judged against the minimum of the new value and is never confirmed here (DP-05).
    public void settleSituation(Cents minimumDeposit) {
        long validated = finalValue.amount() - pendingBalance.amount();
        if (reservationStatus == ReservationStatus.CONFIRMED) {
            paymentStatus = pendingBalance.amount() == 0 ? PaymentStatus.FULLY_PAID
                    : expectedPaymentType == PaymentType.DEPOSIT ? PaymentStatus.DEPOSIT_RECEIVED : PaymentStatus.BALANCE_PENDING;
        } else if (validated <= 0) {
            paymentStatus = PaymentStatus.NO_PAYMENT;
        } else {
            paymentStatus = FinancialSituation.of(finalValue, new Cents(validated), expectedPaymentType, minimumDeposit, reservationStatus);
        }
    }

    // The events born since the last pull, in order; the use case writes them to the outbox in the same transaction.
    public List<PaymentEvent> pullEvents() {
        List<PaymentEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
