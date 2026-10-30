package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationModified;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the additional discount have their own persistence tests. The snapshot is read from
// the reservation fake, so what the registration sees is what the breakdown shows; the state and the paid amount can be set.
@TestConfiguration
class AdditionalDiscountHttpFixture {
    @Bean @Primary MemoryAdditionalDiscounts memoryAdditionalDiscounts(ReservationHttpFixture.MemoryReservations reservations) {
        return new MemoryAdditionalDiscounts(reservations);
    }

    @Bean @Primary MemoryReasons memoryReasons() { return new MemoryReasons(); }

    // Apart from the rest: the reservation reader and the reason repository share the signature findByTenantAndId.
    static final class MemoryReasons implements AuthorizableReasonRepository {
        final Map<UUID, AuthorizableReason> reasons = new HashMap<>();
        @Override public void save(AuthorizableReason reason) { reasons.put(reason.reasonId(), reason); }
        @Override public Optional<AuthorizableReason> findByTenantAndId(UUID tenant, UUID id) {
            return Optional.ofNullable(reasons.get(id)).filter(found -> found.tenantId().equals(tenant));
        }
        @Override public List<AuthorizableReason> findByTenant(UUID tenant) {
            return reasons.values().stream().filter(found -> found.tenantId().equals(tenant)).toList();
        }
    }

    static final class MemoryAdditionalDiscounts implements ReservationReader, AdditionalDiscountRepository,
            ReservationModifiedOutbox, ExcessTreatment {
        private final ReservationHttpFixture.MemoryReservations reservations;
        final Map<UUID, AdditionalDiscount> discounts = new HashMap<>();
        final Map<UUID, ReservationSnapshot.State> states = new HashMap<>();
        final Map<UUID, Cents> paid = new HashMap<>();
        final List<ReservationModified> events = new ArrayList<>();
        final List<Cents> excesses = new ArrayList<>();
        // Runs when the use case asks whether the reservation already has a discount: lets a test commit a concurrent winner first.
        Runnable beforeTheExistenceCheck = () -> { };

        MemoryAdditionalDiscounts(ReservationHttpFixture.MemoryReservations reservations) {
            this.reservations = reservations;
        }

        void clear() { discounts.clear(); states.clear(); paid.clear(); events.clear(); excesses.clear(); beforeTheExistenceCheck = () -> { }; }

        @Override public Optional<ReservationSnapshot> findByTenantAndId(UUID tenant, UUID id) {
            return reservations.findByTenantAndId(tenant, id).map(found -> new ReservationSnapshot(tenant, id,
                    states.getOrDefault(id, ReservationSnapshot.State.PENDING_PAYMENT), found.finalValue(),
                    paid.getOrDefault(id, new Cents(0)), (int) found.fixedDiscounts().stream()
                            .filter(line -> line.amount().amount() > 0).count() + (discounts.containsKey(id) ? 1 : 0)));
        }
        @Override public void save(AdditionalDiscount discount) { discounts.put(discount.reservationId(), discount); }
        @Override public boolean existsByReservation(UUID tenant, UUID reservation) {
            beforeTheExistenceCheck.run();
            return discounts.containsKey(reservation);
        }
        @Override public Optional<AdditionalDiscount> findByReservation(UUID tenant, UUID reservation) {
            return Optional.ofNullable(discounts.get(reservation)).filter(found -> found.tenantId().equals(tenant));
        }
        @Override public void append(ReservationModified event) { events.add(event); }
        @Override public void open(UUID tenant, UUID reservation, Cents excess) { excesses.add(excess); }
    }
}
