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
    private ReservationPersistenceFixture other; // a second real tenant with its own customer, tour, departure and capacity row
    private JdbcReservationRepository reservations;
    private JdbcSalidaCapacityHolder holder;
    private JdbcPaymentProofStore proofs;
    private JdbcPaymentProofReader reader;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        other = new ReservationPersistenceFixture();
        reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        holder = new JdbcSalidaCapacityHolder(fixture.jdbc);
        proofs = new JdbcPaymentProofStore(fixture.jdbc);
        reader = new JdbcPaymentProofReader(fixture.jdbc, reservations);
        OffsetDateTime start = OffsetDateTime.ofInstant(ReservationPersistenceFixture.NOW, ZoneOffset.UTC).plusDays(5);
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 7)", UUID.randomUUID(),
                fixture.tenant, fixture.salida, fixture.service, start);
        fixture.jdbc.update("INSERT INTO commercial_reservation.salida_capacity (salida_capacity_id, tenant_id, salida_id, service_id, "
                + "hora_inicio, status, effective_capacity, held_seats) VALUES (?, ?, ?, ?, ?, 'SCHEDULED', 10, 7)", UUID.randomUUID(),
                other.tenant, other.salida, other.service, start);
    }

    private int held() {
        return fixture.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, fixture.tenant, fixture.salida);
    }

    private int held(ReservationPersistenceFixture of) {
        return fixture.jdbc.queryForObject("SELECT held_seats FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Integer.class, of.tenant, of.salida);
    }

    private Reservation transferOf(ReservationPersistenceFixture of, UUID attempt, long amount, java.time.Instant at) {
        UUID id = UUID.randomUUID();
        reservations.save(of.reservation(id, true));
        Reservation reservation = reservations.findByTenantAndId(of.tenant, id).orElseThrow();
        reservation.registerTransfer(attempt, new Cents(amount), MINIMUM, of.actor, at);
        reservation.allocateSeat(at);
        reservations.save(reservation);
        return reservation;
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
        assertThat(holder.hold(other.tenant, fixture.salida, 1)).isFalse();
        assertThat(held(other)).isEqualTo(7);
        assertThat(holder.hold(fixture.tenant, UUID.randomUUID(), 1)).isFalse();
    }

    // ADR-004 D4: a cancelled departure accepts no hold, judged by the same statement that holds, so a cancellation that lands between the
    // read and the hold cannot leave a seat on it. A move onto a cancelled departure keeps the seats where they were.
    @Test
    void aCancelledDepartureAcceptsNoHoldAndKeepsItsSeats() {
        fixture.jdbc.update("UPDATE commercial_reservation.salida_capacity SET status = 'CANCELLED' WHERE tenant_id = ? AND salida_id = ?",
                fixture.tenant, fixture.salida);

        assertThat(holder.hold(fixture.tenant, fixture.salida, 1)).isFalse();
        assertThat(held()).isEqualTo(7);
        assertThat(holder.move(fixture.tenant, UUID.randomUUID(), UUID.randomUUID(), fixture.salida, 1)).isFalse();
        assertThat(held()).isEqualTo(7);
    }

    @Test
    void theReleaseClosesTheOpenSeatAndGivesTheSeatsBackOnce() {
        holder.hold(fixture.tenant, fixture.salida, 3);
        Reservation reservation = transfer(UUID.randomUUID(), 100_000, ReservationPersistenceFixture.NOW);

        assertThat(holder.release(fixture.tenant, reservation.reservationId())).isEqualTo(3);
        assertThat(held()).isEqualTo(7);
        assertThat(holder.release(fixture.tenant, reservation.reservationId())).isZero();
        assertThat(held()).isEqualTo(7);
        assertThat(holder.release(other.tenant, reservation.reservationId())).isZero();
        assertThat(held(other)).isEqualTo(7);
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
        assertThat(proofs.read(other.tenant, attempt)).isEmpty();
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
        assertThat(reader.find(other.tenant, ValidationStatus.IN_VALIDATION, null, 1, 10).items()).isEmpty();
        assertThat(reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 2, 1).items()).hasSize(1);
    }

    // Rows of two real tenants in the same tables: each reads, holds, releases and lists only its own (AC-10.17).
    @Test
    void twoTenantsWithProofsAndSeatsNeverSeeNorTouchEachOther() {
        UUID mine = UUID.randomUUID(), theirs = UUID.randomUUID();
        holder.hold(fixture.tenant, fixture.salida, 3);
        holder.hold(other.tenant, other.salida, 3);
        byte[] otherPng = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7};
        Reservation mineReservation = transferOf(fixture, mine, 100_000, ReservationPersistenceFixture.NOW);
        Reservation theirReservation = transferOf(other, theirs, 100_000, ReservationPersistenceFixture.NOW);
        proofs.save(fixture.tenant, mine, ProofFile.of(PNG));
        proofs.save(other.tenant, theirs, ProofFile.of(otherPng));

        assertThat(proofs.read(fixture.tenant, mine).orElseThrow().bytes()).isEqualTo(PNG);
        assertThat(proofs.read(other.tenant, theirs).orElseThrow().bytes()).isEqualTo(otherPng);
        assertThat(proofs.read(other.tenant, mine)).isEmpty();
        assertThat(proofs.read(fixture.tenant, theirs)).isEmpty();
        var mineList = reader.find(fixture.tenant, ValidationStatus.IN_VALIDATION, null, 1, 10);
        var theirList = reader.find(other.tenant, ValidationStatus.IN_VALIDATION, null, 1, 10);
        assertThat(mineList.total()).isEqualTo(1);
        assertThat(mineList.items().get(0).attempt().attemptId()).isEqualTo(mine);
        assertThat(theirList.total()).isEqualTo(1);
        assertThat(theirList.items().get(0).attempt().attemptId()).isEqualTo(theirs);
        assertThat(reader.countAtOrBefore(fixture.tenant, ValidationStatus.IN_VALIDATION, ReservationPersistenceFixture.NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(reader.countAtOrBefore(other.tenant, ValidationStatus.IN_VALIDATION, ReservationPersistenceFixture.NOW.plusSeconds(1))).isEqualTo(1);
        assertThat(reservations.findByTenantAndAttempt(fixture.tenant, theirs)).isEmpty();
        assertThat(reservations.findByTenantAndAttempt(other.tenant, mine)).isEmpty();
        assertThat(reservations.findByTenantAndId(other.tenant, mineReservation.reservationId())).isEmpty();

        assertThat(holder.release(fixture.tenant, theirReservation.reservationId())).isZero();
        assertThat(holder.release(other.tenant, mineReservation.reservationId())).isZero();
        assertThat(held(fixture)).isEqualTo(10);
        assertThat(held(other)).isEqualTo(10);
        assertThat(holder.release(other.tenant, theirReservation.reservationId())).isEqualTo(3);
        assertThat(held(other)).isEqualTo(7);
        assertThat(held(fixture)).isEqualTo(10);
    }
}
