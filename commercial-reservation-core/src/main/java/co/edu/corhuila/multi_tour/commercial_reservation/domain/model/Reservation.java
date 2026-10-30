package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Aggregate root of the Reservations context (plan D-02). It is born PENDING_PAYMENT, NO_PAYMENT, with the pending balance
// equal to the final value and no seat held (RN-RES-006, RN-RES-007). HU-COM-009 adds the payment attempts as children:
// registered is not validated, and the financial situation moves only with validated values (RN-01, RN-02).
public final class Reservation {
    private final UUID reservationId, tenantId, customerId, departureId, createdBy;
    private final int people;
    private final ReservedService service;
    private final Cents projectedValue, finalValue;
    private final List<FixedDiscount> fixedDiscounts;
    private final PaymentType expectedPaymentType;
    private final PaymentMethod expectedPaymentMethod;
    private final Instant createdAt;
    private final List<Companion> companions;
    private final List<PaymentAttempt> attempts = new ArrayList<>();
    private final List<PaymentEvent> events = new ArrayList<>();
    private ReservationStatus reservationStatus = ReservationStatus.PENDING_PAYMENT;
    private PaymentStatus paymentStatus = PaymentStatus.NO_PAYMENT;
    private Cents pendingBalance;
    private boolean seatHeld;

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
        if (group.stream().map(Companion::documentNumber).distinct().count() != group.size())
            throw new BusinessRuleViolation("a document is already in the group");
        if (projected == null || !projected.equals(service.projectedFor(people)))
            throw new DomainException("projectedValue", "projectedValue must be the tour plus the transport of every person");
        List<FixedDiscount> lines = discounts == null ? List.of() : discounts;
        Cents left = lines.isEmpty() ? projected : lines.get(lines.size() - 1).subtotal();
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

    // RN-01: a transfer is born in validation and changes neither the status nor the situation. DP-05 (default): at most the
    // pending balance, and the first deposit at least the minimum.
    public PaymentAttempt registerTransfer(UUID attemptId, Cents amount, Cents minimumDeposit, UUID registeredBy, Instant now) {
        requireOpenToPayment();
        requireAmount(amount, minimumDeposit);
        if (amount.amount() > pendingBalance.amount()) throw new DomainException("amountCents", "the transfer cannot exceed the pending balance");
        PaymentAttempt attempt = new PaymentAttempt(attemptId, PaymentMethod.TRANSFER, amount, ValidationStatus.IN_VALIDATION, paymentStatus, registeredBy, now);
        attempts.add(attempt);
        events.add(new PaymentEvidenceRegistered(tenantId, reservationId, attemptId, PaymentMethod.TRANSFER, amount, ValidationStatus.IN_VALIDATION, now));
        return attempt;
    }

    // DP-01: cash is money received, validated at once; the excess over the balance is returned, never dropped (DP-05).
    public CashReceipt registerCash(UUID attemptId, Cents amount, Cents minimumDeposit, UUID registeredBy, Instant now) {
        requireOpenToPayment();
        requireAmount(amount, minimumDeposit);
        PaymentAttempt attempt = new PaymentAttempt(attemptId, PaymentMethod.CASH, amount, null, paymentStatus, registeredBy, now);
        attempts.add(attempt);
        events.add(new PaymentEvidenceRegistered(tenantId, reservationId, attemptId, PaymentMethod.CASH, amount, null, now));
        long applied = Math.min(amount.amount(), pendingBalance.amount());
        recordValidatedPayment(new Cents(applied), minimumDeposit, registeredBy, now);
        return new CashReceipt(attempt, new Cents(applied), new Cents(amount.amount() - applied));
    }

    // The one place that adds a validated amount (HU-COM-010 reuses it for an approved transfer): the balance goes down, the
    // situation is recomputed and the reservation is confirmed when the condition of its payment type is met.
    private void recordValidatedPayment(Cents applied, Cents minimumDeposit, UUID validator, Instant now) {
        pendingBalance = new Cents(pendingBalance.amount() - applied.amount());
        Cents validated = new Cents(finalValue.amount() - pendingBalance.amount());
        boolean confirms = reservationStatus == ReservationStatus.PENDING_PAYMENT
                && FinancialSituation.meetsConfirmation(finalValue, validated, expectedPaymentType, minimumDeposit);
        if (confirms) reservationStatus = ReservationStatus.CONFIRMED;
        paymentStatus = FinancialSituation.of(finalValue, validated, expectedPaymentType, minimumDeposit, reservationStatus);
        events.add(new PaymentValidated(tenantId, reservationId, applied, paymentStatus, pendingBalance, validator, now));
        if (confirms) events.add(new ReservationConfirmed(tenantId, reservationId, paymentStatus, people, now));
    }

    private void requireAmount(Cents amount, Cents minimumDeposit) {
        if (amount == null || amount.amount() <= 0) throw new DomainException("amountCents", "amountCents must be greater than zero");
        boolean firstPayment = pendingBalance.equals(finalValue) && reservationStatus == ReservationStatus.PENDING_PAYMENT;
        if (expectedPaymentType == PaymentType.DEPOSIT && minimumDeposit == null)
            throw new DomainException("minimumDeposit", "a deposit needs its minimum");
        if (firstPayment && expectedPaymentType == PaymentType.DEPOSIT && amount.amount() < minimumDeposit.amount())
            throw new DomainException("amountCents", "the first deposit cannot be below the minimum deposit");
    }

    // RN-RES-007: the first payment holds the seats; the use case asked the capacity row before calling this.
    public void allocateSeat(Instant now) {
        if (seatHeld) throw new BusinessRuleViolation("the seats of the reservation are already held");
        seatHeld = true;
        events.add(new CapacityAllocated(tenantId, reservationId, service.serviceId(), departureId, CapacityAllocated.RESERVATION_KIND, now));
    }

    // The events born since the last pull, in order; the use case writes them to the outbox in the same transaction.
    public List<PaymentEvent> pullEvents() {
        List<PaymentEvent> pulled = List.copyOf(events);
        events.clear();
        return pulled;
    }
}
