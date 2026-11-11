package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Aggregate root of the Reservations context (plan D-02). Only its birth exists: PENDING_PAYMENT, NO_PAYMENT, the pending
// balance equal to the final value and no seat held (RN-RES-006, RN-RES-007). Later transitions add their own method.
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

    private Reservation(UUID id, UUID tenant, UUID customer, UUID departure, UUID creator, int people, ReservedService service,
            Cents projected, Cents finalValue, List<FixedDiscount> discounts, PaymentType type, PaymentMethod method, Instant now, List<Companion> companions) {
        reservationId = id; tenantId = tenant; customerId = customer; departureId = departure; createdBy = creator;
        this.people = people; this.service = service; projectedValue = projected; this.finalValue = finalValue;
        fixedDiscounts = List.copyOf(discounts); expectedPaymentType = type; expectedPaymentMethod = method; createdAt = now; this.companions = List.copyOf(companions);
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
    public Cents pendingBalance() { return finalValue; }
    public Cents creditBalance() { return new Cents(0); }
    public List<FixedDiscount> fixedDiscounts() { return fixedDiscounts; }
    public ReservationStatus reservationStatus() { return ReservationStatus.PENDING_PAYMENT; }
    public PaymentStatus paymentStatus() { return PaymentStatus.NO_PAYMENT; }
    public boolean seatHeld() { return false; }
    public PaymentType expectedPaymentType() { return expectedPaymentType; }
    public PaymentMethod expectedPaymentMethod() { return expectedPaymentMethod; }
    public Instant createdAt() { return createdAt; }
    public List<Companion> companions() { return companions; }
}
