package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AnchorBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// T-03 and T-04 of HU-COM-019 (-api): RN-01, RN-06, RN-07, RN-12; AC-19.7, AC-19.14, AC-19.16 over the real schema. The deletion removes the risk rows of the companions and of the
// customer and nothing else (the consents and the commercial history stay byte-equal), it is scoped to the tenant, its record is written once per reservation and a failure in
// the middle of the transaction deletes nothing. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class SensitiveDataDeletionPersistenceTest {
    private static final Instant NOW = Instant.parse("2027-11-12T12:00:00Z");
    private ReservationPersistenceFixture fixture;
    private JdbcRetentionDeletions deletions;
    private final UUID reservation = UUID.randomUUID(), other = UUID.randomUUID();

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        deletions = new JdbcRetentionDeletions(fixture.jdbc);
        var reservations = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        reservations.save(fixture.reservation(reservation, true));
        reservations.save(fixture.reservation(other, false));
        for (int index = 0; index < 2; index++) {
            UUID companion = UUID.randomUUID();
            fixture.jdbc.update("INSERT INTO commercial_reservation.reservation_companion (reservation_companion_id, tenant_id, reservation_id, full_name, document_number, "
                    + "birth_date_or_age) VALUES (?, ?, ?, 'Acompanante Ficticio', 'DOC-FICT-" + index + "', 'envelope')", companion, fixture.tenant, reservation);
            fixture.jdbc.update("INSERT INTO commercial_reservation.reservation_companion_sensitive_data (reservation_companion_id, tenant_id, activity_requirements) "
                    + "VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)", companion, fixture.tenant);
        }
        fixture.jdbc.update("INSERT INTO commercial_reservation.customer_sensitive_data (customer_id, tenant_id, emergency_info) VALUES (?, ?, '{\"envelope\":\"x\"}'::jsonb)",
                fixture.customer, fixture.tenant);
    }

    private int companionRisk() {
        return fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation_companion_sensitive_data WHERE tenant_id = ?", Integer.class, fixture.tenant);
    }

    private int customerRisk() {
        return fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.customer_sensitive_data WHERE tenant_id = ?", Integer.class, fixture.tenant);
    }

    private SensitiveDataDeletion record(UUID reservationId) {
        return new SensitiveDataDeletion(reservationId, fixture.tenant, fixture.customer, NOW.minusSeconds(400L * 86_400), AnchorBasis.SERVICE_END, NOW, true, 2);
    }

    @Test
    void theDeletionRemovesOnlyTheRiskRowsOfTheReservationAndOfTheCustomerAndKeepsTheRest() {
        String consent = fixture.jdbc.queryForObject("SELECT terms_accepted::text FROM commercial_reservation.customer WHERE customer_id = ?", String.class, fixture.customer);

        assertThat(deletions.otherReservationsOf(fixture.tenant, fixture.customer, reservation)).containsExactly(other);
        assertThat(deletions.deleteCompanionRiskData(fixture.tenant, reservation)).isEqualTo(2);
        deletions.deleteCustomerRiskData(fixture.tenant, fixture.customer);

        assertThat(companionRisk()).isZero();
        assertThat(customerRisk()).isZero();
        assertThat(fixture.count("reservation_companion", reservation)).isEqualTo(2);
        assertThat(fixture.jdbc.queryForObject("SELECT terms_accepted::text FROM commercial_reservation.customer WHERE customer_id = ?", String.class, fixture.customer)).isEqualTo(consent);
        assertThat(fixture.count("reservation", reservation)).isEqualTo(1);
    }

    @Test
    void anotherTenantTouchesNothing() {
        UUID stranger = UUID.randomUUID();

        assertThat(deletions.deleteCompanionRiskData(stranger, reservation)).isZero();
        deletions.deleteCustomerRiskData(stranger, fixture.customer);
        assertThat(deletions.otherReservationsOf(stranger, fixture.customer, reservation)).isEmpty();
        assertThat(deletions.isDeleted(stranger, reservation)).isFalse();

        assertThat(companionRisk()).isEqualTo(2);
        assertThat(customerRisk()).isEqualTo(1);
    }

    @Test
    void theRecordIsWrittenOncePerReservationAndMakesTheReservationDeleted() {
        assertThat(deletions.isDeleted(fixture.tenant, reservation)).isFalse();

        deletions.record(record(reservation));

        assertThat(deletions.isDeleted(fixture.tenant, reservation)).isTrue();
        assertThat(deletions.isDeleted(fixture.tenant, other)).isFalse();
        assertThatThrownBy(() -> deletions.record(record(reservation))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void aFailureInTheMiddleOfTheTransactionDeletesNothing() {
        var transaction = new JdbcPaymentTransaction(fixture.transactions);

        assertThatThrownBy(() -> transaction.inTransaction(() -> {
            deletions.lockCustomer(fixture.tenant, fixture.customer);
            deletions.deleteCompanionRiskData(fixture.tenant, reservation);
            deletions.deleteCustomerRiskData(fixture.tenant, fixture.customer);
            throw new IllegalStateException("the record cannot be written");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(companionRisk()).isEqualTo(2);
        assertThat(customerRisk()).isEqualTo(1);
        assertThat(List.of(deletions.isDeleted(fixture.tenant, reservation))).containsExactly(false);
    }
}
