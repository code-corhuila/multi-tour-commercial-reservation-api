package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundBasisReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundAuthorization;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.RefundOrigin;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// Spec 026 (-api), T-03: the two reads that the determination needs, over the real schema and scoped to the tenant. The refund of a reservation and an origin is
// found once, and the basis (validated value, people, service) is the one the reservation stored. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class RefundDeterminationPersistenceTest {
    private static final Instant NOW = ReservationPersistenceFixture.NOW;
    private ReservationPersistenceFixture fixture;
    private JdbcRefundAuthorizationRepository refunds;
    private JdbcRefundBasisReader basis;
    private UUID reservation;

    @BeforeEach
    void plant() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        refunds = new JdbcRefundAuthorizationRepository(fixture.jdbc);
        basis = new JdbcRefundBasisReader(fixture.jdbc);
        reservation = UUID.randomUUID();
        new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc))
                .save(fixture.reservation(reservation, true));
    }

    @Test
    void findsTheRefundOfAReservationAndAnOriginOnlyForItsTenant() {
        RefundAuthorization stored = RefundAuthorization.determine(UUID.randomUUID(), fixture.tenant, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER,
                new Cents(9_000), NOW, NOW.plus(Duration.ofDays(5)));
        refunds.save(stored);

        assertThat(refunds.findByReservationAndOrigin(fixture.tenant, reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER)).contains(stored);
        assertThat(refunds.findByReservationAndOrigin(fixture.tenant, reservation, RefundOrigin.PERSONAL_EMERGENCY_DURING_EXECUTION)).isEmpty();
        assertThat(refunds.findByReservationAndOrigin(UUID.randomUUID(), reservation, RefundOrigin.NOT_ATTRIBUTABLE_TO_CUSTOMER)).isEmpty();
    }

    @Test
    void readsTheBasisOfAReservationAndHidesItFromAnotherTenant() {
        var found = basis.find(fixture.tenant, reservation).orElseThrow();

        assertThat(found.reservationId()).isEqualTo(reservation);
        assertThat(found.people()).isEqualTo(3);
        assertThat(found.serviceId()).isEqualTo(fixture.service);
        assertThat(found.validated().amount()).isGreaterThanOrEqualTo(0);
        assertThat(basis.find(UUID.randomUUID(), reservation)).isEmpty();
        assertThat(basis.find(fixture.tenant, UUID.randomUUID())).isEmpty();
    }
}
