package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationModified;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

// One shared store so a rollback undoes the discount and its event together (norm 5.3.11).
final class AdditionalDiscountFakes implements ReservationReader, AdditionalDiscountRepository,
        ReservationModifiedOutbox, ExcessTreatment, DiscountRuleTransaction {
    static final UUID RESERVATION = new UUID(0, 0xd1), REASON = new UUID(0, 0xe1), OTHER_REASON = new UUID(0, 0xe2);

    final Map<UUID, ReservationSnapshot> reservations = new LinkedHashMap<>();
    final Map<UUID, AdditionalDiscount> discounts = new LinkedHashMap<>();
    final Reasons reasonStore = new Reasons();
    final List<ReservationModified> events = new ArrayList<>();
    final List<Cents> excesses = new ArrayList<>();
    RuntimeException failOnAppend;

    static ReservationSnapshot reservation(ReservationSnapshot.State state, long subtotal, long paid) {
        return new ReservationSnapshot(DiscountRuleFakes.TENANT, RESERVATION, state, new Cents(subtotal),
                new Cents(paid), 2);
    }

    AdditionalDiscountFakes withReservation(ReservationSnapshot snapshot) {
        reservations.put(snapshot.reservationId(), snapshot);
        return this;
    }

    AdditionalDiscountFakes withReason(UUID id, String text, boolean enabled) {
        reasonStore.save(new AuthorizableReason(DiscountRuleFakes.TENANT, id, text, enabled));
        return this;
    }

    @Override public Optional<ReservationSnapshot> findByTenantAndId(UUID tenantId, UUID reservationId) {
        return Optional.ofNullable(reservations.get(reservationId)).filter(r -> r.tenantId().equals(tenantId));
    }
    @Override public void save(AdditionalDiscount discount) { discounts.put(discount.reservationId(), discount); }
    @Override public boolean existsByReservation(UUID tenantId, UUID reservationId) {
        return discounts.containsKey(reservationId);
    }
    @Override public Optional<AdditionalDiscount> findByReservation(UUID tenantId, UUID reservationId) {
        return Optional.ofNullable(discounts.get(reservationId)).filter(d -> d.tenantId().equals(tenantId));
    }
    @Override public void append(ReservationModified event) {
        if (failOnAppend != null) throw failOnAppend;
        events.add(event);
    }
    @Override public void open(UUID tenantId, UUID reservationId, Cents excess) { excesses.add(excess); }
    @Override public <T> T inTransaction(Supplier<T> work) {
        var discountsBefore = new LinkedHashMap<>(discounts);
        var eventsBefore = new ArrayList<>(events);
        var excessesBefore = new ArrayList<>(excesses);
        try {
            return work.get();
        } catch (RuntimeException e) {
            discounts.clear(); discounts.putAll(discountsBefore);
            events.clear(); events.addAll(eventsBefore);
            excesses.clear(); excesses.addAll(excessesBefore);
            throw e;
        }
    }

    static final class Reasons implements AuthorizableReasonRepository {
        final Map<UUID, AuthorizableReason> byId = new LinkedHashMap<>();

        @Override public void save(AuthorizableReason reason) { byId.put(reason.reasonId(), reason); }
        @Override public Optional<AuthorizableReason> findByTenantAndId(UUID tenantId, UUID reasonId) {
            return Optional.ofNullable(byId.get(reasonId)).filter(r -> r.tenantId().equals(tenantId));
        }
        @Override public List<AuthorizableReason> findByTenant(UUID tenantId) {
            return byId.values().stream().filter(r -> r.tenantId().equals(tenantId)).toList();
        }
    }
}
