package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;

// HU-COM-005 companions over the real schema (api spec T-03, RN-04, AC-05.12): every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationCompanionsPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null
                && System.getenv("TEST_DATABASE_PASSWORD") != null, "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                new JdbcReservationCreatedOutbox(fixture.jdbc));
    }

    @Test
    void aDocumentTypedWithSpacesAndLowerCaseIsStoredNormalized() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true, new Companion("Acompanante Ficticio Tres",
                DocumentNumber.of("  doc-fict-0009 "), BirthDateOrAge.ofAge(40), null)));

        var stored = fixture.jdbc.queryForList("SELECT document_number FROM commercial_reservation.reservation_companion "
                + "WHERE reservation_id = ?", String.class, id);
        assertThat(stored).containsExactly("DOC-FICT-0009");
    }

    @Test
    void theEngineRefusesTheSameDocumentTwiceInOneReservationAndKeepsTheFirstOne() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true, fixture.plainCompanion()));

        assertThatThrownBy(() -> fixture.jdbc.update("INSERT INTO commercial_reservation.reservation_companion "
                + "(reservation_companion_id, tenant_id, reservation_id, full_name, document_number, birth_date_or_age) "
                + "VALUES (?, ?, ?, 'Otra Persona Ficticia', ?, 'x')", UUID.randomUUID(), fixture.tenant, id,
                ReservationPersistenceFixture.COMPANION_DOCUMENT)).isInstanceOf(DataAccessException.class);
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation_companion "
                + "WHERE reservation_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void theEngineRefusesADocumentThatIsNotNormalized() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));

        assertThatThrownBy(() -> fixture.jdbc.update("INSERT INTO commercial_reservation.reservation_companion "
                + "(reservation_companion_id, tenant_id, reservation_id, full_name, document_number, birth_date_or_age) "
                + "VALUES (?, ?, ?, 'Persona Ficticia', ' doc-x ', 'x')", UUID.randomUUID(), fixture.tenant, id))
                .isInstanceOf(DataAccessException.class);
    }
}
