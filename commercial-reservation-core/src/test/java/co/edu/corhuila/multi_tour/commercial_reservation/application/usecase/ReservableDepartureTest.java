package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// T-04 of HU-COM-007: RN-05, RN-06; AC-07.7 to AC-07.9. The check reads the capacity row and never writes it (plan D-05).
class ReservableDepartureTest {

    private static final UUID TENANT = new UUID(7, 0xd1), OTHER_TENANT = new UUID(7, 0xd2), TOUR = new UUID(7, 0xd3),
            OTHER_TOUR = new UUID(7, 0xd4), DEPARTURE = new UUID(7, 0xd5);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private final DepartureFakes departures = new DepartureFakes();
    private final ReservableDeparture check = new ReservableDeparture(departures, Clock.fixed(NOW, ZoneOffset.UTC));

    private DepartureState plant(DepartureStatus status, Instant startsAt, int capacity, int held) {
        DepartureState state = new DepartureState(DEPARTURE, TENANT, TOUR, startsAt, status, capacity, held);
        departures.plant(state);
        return state;
    }

    @Test
    void aScheduledFutureDepartureOfTheTourWithSeatsIsReservable() {
        DepartureState planted = plant(DepartureStatus.SCHEDULED, LATER, 10, 6);

        assertThat(check.require(TENANT, DEPARTURE, TOUR, 4)).isEqualTo(planted);
    }

    @Test
    void aDepartureThatDoesNotExistOrBelongsToAnotherTenantLooksMissing() {
        plant(DepartureStatus.SCHEDULED, LATER, 10, 0);

        assertThatThrownBy(() -> check.require(OTHER_TENANT, DEPARTURE, TOUR, 1)).isInstanceOf(DepartureNotFoundException.class);
        assertThatThrownBy(() -> check.require(TENANT, new UUID(7, 0xff), TOUR, 1)).isInstanceOf(DepartureNotFoundException.class);
    }

    @Test
    void aDepartureOfAnotherTourRefusesTheCreation() {
        plant(DepartureStatus.SCHEDULED, LATER, 10, 0);

        assertThatThrownBy(() -> check.require(TENANT, DEPARTURE, OTHER_TOUR, 1)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void aDepartureNotProgramadaRefusesTheCreation() {
        for (DepartureStatus status : new DepartureStatus[] {DepartureStatus.IN_EXECUTION, DepartureStatus.FINALIZED, DepartureStatus.CANCELLED}) {
            plant(status, LATER, 10, 0);
            assertThatThrownBy(() -> check.require(TENANT, DEPARTURE, TOUR, 1)).isInstanceOf(BusinessRuleViolation.class);
        }
    }

    @Test
    void aDepartureThatReachedItsStartTimeRefusesTheCreation() {
        plant(DepartureStatus.SCHEDULED, NOW, 10, 0);
        assertThatThrownBy(() -> check.require(TENANT, DEPARTURE, TOUR, 1)).isInstanceOf(BusinessRuleViolation.class);

        plant(DepartureStatus.SCHEDULED, NOW.minusSeconds(1), 10, 0);
        assertThatThrownBy(() -> check.require(TENANT, DEPARTURE, TOUR, 1)).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void notEnoughCapacityCreatesNothingAndLeavesHeldSeatsUntouched() {
        DepartureState planted = plant(DepartureStatus.SCHEDULED, LATER, 10, 8);

        assertThat(check.require(TENANT, DEPARTURE, TOUR, 2)).isEqualTo(planted);
        assertThatThrownBy(() -> check.require(TENANT, DEPARTURE, TOUR, 3))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("2 seats");
        assertThat(departures.rows.get(DEPARTURE)).isEqualTo(planted);
        assertThat(departures.journal).isEmpty();
    }
}
