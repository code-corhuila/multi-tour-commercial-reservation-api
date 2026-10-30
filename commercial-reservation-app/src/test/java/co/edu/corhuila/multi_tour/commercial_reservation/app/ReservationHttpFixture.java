package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationListItem;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.OwnReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

// Test fake only: the JDBC adapters of the reservation ports are exercised by their own persistence tests.
@TestConfiguration
class ReservationHttpFixture {
    @Bean @Primary MemoryReservations memoryReservations() { return new MemoryReservations(); }

    static final class MemoryReservations implements ReservationRepository, ReservationQueries, DepartureReader,
            EnabledPaymentOptions, TransportRateReader, OwnReservationQueries {
        final Map<UUID, Reservation> reservations = new HashMap<>();
        final Map<UUID, DepartureState> departures = new HashMap<>();
        Optional<Cents> transportRate = Optional.empty();
        boolean lastIncludeArchived;
        int saves;

        void clear() {
            reservations.clear(); departures.clear(); transportRate = Optional.empty();
            lastIncludeArchived = false; saves = 0;
        }
        public void save(Reservation reservation) { saves++; reservations.put(reservation.reservationId(), reservation); }
        public ReservationPage page(UUID tenant, boolean includeArchived, int page, int limit) {
            lastIncludeArchived = includeArchived;
            List<ReservationListItem> rows = new ArrayList<>(reservations.values().stream()
                    .filter(found -> found.tenantId().equals(tenant)).sorted(Comparator.comparing(Reservation::createdAt).reversed())
                    .map(found -> new ReservationListItem(found.reservationId(), "Persona Ficticia", "Tour Ficticio",
                            Instant.parse("2026-11-20T13:00:00Z"), found.reservationStatus(), found.paymentStatus(),
                            found.pendingBalance())).toList());
            return new ReservationPage(rows.stream().skip((page - 1L) * limit).limit(limit).toList(), rows.size());
        }
        public ReservationPage page(UUID tenant, UUID customer, boolean includeArchived, int page, int limit) {
            var own = reservations.values().stream().filter(found -> found.customerId().equals(customer)).toList();
            var all = page(tenant, includeArchived, 1, Integer.MAX_VALUE);
            var rows = all.items().stream().filter(row -> own.stream().anyMatch(found -> found.reservationId().equals(row.reservationId()))).toList();
            return new ReservationPage(rows.stream().skip((page - 1L) * limit).limit(limit).toList(), rows.size());
        }
        public Optional<Reservation> findByTenantCustomerAndId(UUID tenant, UUID customer, UUID id) {
            return findByTenantAndId(tenant, id).filter(found -> found.customerId().equals(customer));
        }
        public Optional<Reservation> findByTenantAndId(UUID tenant, UUID id) {
            return Optional.ofNullable(reservations.get(id)).filter(found -> found.tenantId().equals(tenant));
        }
        public List<DepartureState> findOpen(UUID tenant, UUID service, Instant now) {
            return departures.values().stream().filter(row -> row.tenantId().equals(tenant) && row.serviceId().equals(service)).toList();
        }
        @Override public Optional<DepartureState> find(UUID tenant, UUID id) {
            return Optional.ofNullable(departures.get(id)).filter(row -> row.tenantId().equals(tenant));
        }
        public boolean isEnabled(UUID tenant, UUID service, PaymentType type, PaymentMethod method) { return true; }
        public Optional<Cents> perPersonCents(UUID tenant, UUID tour, UUID departure) { return transportRate; }
    }
}
