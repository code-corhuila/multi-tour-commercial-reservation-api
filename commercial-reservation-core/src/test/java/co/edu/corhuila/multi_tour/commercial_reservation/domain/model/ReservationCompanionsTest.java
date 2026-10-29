package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// RN-08 in the aggregate: the companions belong to the reservation, fit in the people left by the lead and share no document.
class ReservationCompanionsTest {

    private static final UUID ID = new UUID(8, 1), TENANT = new UUID(8, 2), CUSTOMER = new UUID(8, 3), DEPARTURE = new UUID(8, 4),
            SERVICE = new UUID(8, 5), CREATOR = new UUID(8, 6);
    private static final ReservedService TOUR = new ReservedService(SERVICE, new Cents(100_000), null);

    private static Companion companion(String document) {
        return new Companion("Person " + document, DocumentNumber.of(document), BirthDateOrAge.ofAge(30), null);
    }

    private static Reservation born(int people, List<Companion> companions) {
        return Reservation.create(ID, TENANT, CUSTOMER, DEPARTURE, CREATOR, people, TOUR, new Cents(100_000L * people),
                new Cents(100_000L * people), List.of(), PaymentType.DEPOSIT, PaymentMethod.CASH,
                Instant.parse("2026-10-28T10:00:00Z"), companions);
    }

    @Test
    void theCompanionsBelongToTheReservation() {
        assertThat(born(3, List.of(companion("A1"), companion("A2"))).companions())
                .extracting(c -> c.documentNumber().value()).containsExactly("A1", "A2");
    }

    @Test
    void theCompanionsOfAReservationCannotBeChangedFromOutside() {
        Reservation reservation = born(2, List.of(companion("A1")));

        assertThatThrownBy(() -> reservation.companions().add(companion("A2"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aDocumentTwiceInTheCompanionsIsRefused() {
        assertThatThrownBy(() -> born(3, List.of(companion("a1"), companion(" A1 ")))).isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    void moreCompanionsThanThePeopleLeftByTheLeadAreRefused() {
        assertThatThrownBy(() -> born(2, List.of(companion("A1"), companion("A2"))))
                .isInstanceOf(DomainException.class).extracting("field").isEqualTo("companions");
    }
}
