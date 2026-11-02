package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentProofStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaCapacityHolder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ProofFile;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ValidationStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-009 and HU-COM-010 over the real schema (api tasks T-04, T-05, T-06): the conditional hold of the capacity row, the release
// of the open seat, the proof file in its own table and the list of the proofs by status and age. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class PaymentCapacityProofPersistenceTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository reservations;
    private JdbcSalidaCapacityHolder holder;
    private JdbcPaymentProofStore proofs;
    private JdbcPaymentProofReader reader;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        holder = new JdbcSalidaCapacityHolder(fixture.jdbc);
        proofs = new JdbcPaymentProofStore(fixture.jdbc);
        reader = new JdbcPaymentProofReader(fixture.jdbc, reservations);
        OffsetDateTime start = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusDays(5);
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 7)", UUID.randomUUID(),
                fixture.tenant, fixture.salida, fixture.service, start);
    }

    private int held() {
        return fixture.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, fixture.tenant, fixture.salida);
    }

    private Reservation transfer(UUID attempt, long amount, java.time.Instant at) {
        UUID id = UUID.randomUUID();
        reservations.save(fixture.reservation(id, true));
        Reservation reservation = reservations.findByTenantAndId(fixture.tenant, id).orElseThrow();
        reservation.registerTransfer(attempt, new Cents(amount), MINIMUM, fixture.actor, at);
        reservation.allocateSeat(at);
        reservations.save(reservation);
        return reservation;
    }

    @Test
    void theHoldIsOneConditionalUpdateWithinTheEffectiveCapacity() {
        assertThat(holder.hold(fixture.tenant, fixture.salida, 3)).isTrue();
        assertThat(held()).isEqualTo(10);
        assertThat(holder.hold(fixture.tenant, fixture.salida, 1)).isFalse();
        assertThat(held()).isEqualTo(10);
        assertThat(holder.hold(UUID.randomUUID(), fixture.salida, 1)).isFalse();
        assertThat(holder.hold(fixture.tenant, UUID.randomUUID(), 1)).isFalse();
    }

    @Test
    void theReleaseClosesTheOpenSeatAndGivesTheSeatsBackOnce() {
        holder.hold(fixture.tenant, fixture.salida, 3);
        Reservation reservation = transfer(UUID.randomUUID(), 100_000, ReservationPersistenceFixture.NOW);

        assertThat(holder.release(fixture.tenant, reservation.reservationId())).isEqualTo(3);
        assertThat(held()).isEqualTo(7);
        assertThat(holder.release(fixture.tenant, reservation.reservationId())).isZero();
        assertThat(held()).isEqualTo(7);
        assertThat(holder.release(UUID.randomUUID(), reservation.reservationId())).isZero();
        assertThat(reservations.findByTenantAndId(fixture.tenant, reservation.reservationId()).orElseThrow().seatHeld()).isFalse();
    }

    @Test
    void theProofIsStoredInItsOwnTableAndReadBackOnlyByItsTenant() {
        UUID attempt = UUID.randomUUID();
        transfer(attempt, 100_000, ReservationPersistenceFixture.NOW);

        proofs.save(fixture.tenant, attempt, ProofFile.of(PNG));

        ProofFile read = proofs.read(fixture.tenant, attempt).orElseThrow();
        assertThat(read.bytes()).isEqualTo(PNG);
        assertThat(read.contentType()).isEqualTo("image/png");
        assertThat(read.sha256()).isEqualTo(ProofFile.of(PNG).sha256());
        assertThat(proofs.read(UUID.randomUUID(), attempt)).isEmpty();
        assertThat(proofs.read(fixture.tenant, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theListIsByStatusAndAgeWithTheCountOfTheOverdueOnes() {
        UUID old = UUID.randomUUID(), recent = UUID.randomUUID();
        transfer(old, 100_000, ReservationPersistenceFixture.NOW.minusSeconds(30 * 3600));
        transfer(recent, 100_000, ReservationPersistenceFixture.NOW.minusSeconds(3600));
        var cutoff = ReservationPersistenceFixture.NOW.minusSeconds(24 * 3600);

        var all = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 1, 10);
        var overdue = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, cutoff, 1, 10);

        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items().get(0).attempt().attemptId()).isEqualTo(old);
        assertThat(all.items().get(0).reservation().attempts()).hasSize(1);
        assertThat(overdue.total()).isEqualTo(1);
        assertThat(overdue.items().get(0).attempt().attemptId()).isEqualTo(old);
        assertThat(reader.countAtOrBefore(fixture.tenant, ValidationStatus.IN_VALIDATION, cutoff)).isEqualTo(1);
        assertThat(reader.find(fixture.tenant, ValidationStatus.APPROVED, null, 1, 10).total()).isZero();
        assertThat(reader.find(UUID.randomUUID(), ValidationStatus.IN_VALIDATION, null, 1, 10).items()).isEmpty();
        assertThat(reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 2, 1).items()).hasSize(1);
    }
}
