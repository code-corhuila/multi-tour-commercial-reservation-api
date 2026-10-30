package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// The event names references and values only (RN-12): the companions of the group never reach it.
class ReservationCreatedTest {
    private static final UUID TENANT = new UUID(1, 1);
    private static final UUID CUSTOMER = new UUID(1, 2);
    private static final UUID SERVICE = new UUID(1, 3);
    private static final UUID ACTOR = new UUID(1, 4);
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    @Test
    void theEventCarriesTheReferencesTheValuesAndTheMomentOfTheReservation() {
        var reservation = Reservation.create(new UUID(1, 5), TENANT, CUSTOMER, new UUID(1, 6), ACTOR, 2,
                new ReservedService(SERVICE, new Cents(100_000), null), new Cents(200_000), new Cents(200_000), List.of(),
                PaymentType.FULL_PAYMENT, PaymentMethod.CASH, NOW);

        var event = ReservationCreated.of(reservation);

        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.reservationId()).isEqualTo(new UUID(1, 5));
        assertThat(event.customerId()).isEqualTo(CUSTOMER);
        assertThat(event.serviceId()).isEqualTo(SERVICE);
        assertThat(event.projectedValue()).isEqualTo(new Cents(200_000));
        assertThat(event.finalValue()).isEqualTo(new Cents(200_000));
        assertThat(event.actorId()).isEqualTo(ACTOR);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }
}
