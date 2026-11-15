package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// HU-COM-006 plan D-07: available = max(effective - held, 0).
class SeatAvailabilityTest {

    @Test
    void availableSeatsAreEffectiveCapacityMinusHeld() {
        assertThat(new SeatAvailability(10, 4).available()).isEqualTo(6);
        assertThat(new SeatAvailability(10, 10).available()).isZero();
    }

    @Test
    void availabilityNeverGoesBelowZero() {
        assertThat(new SeatAvailability(8, 12).available()).isZero();
    }

    @Test
    void negativeNumbersAreRefused() {
        assertThatThrownBy(() -> new SeatAvailability(-1, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new SeatAvailability(1, -1)).isInstanceOf(DomainException.class);
    }
}
