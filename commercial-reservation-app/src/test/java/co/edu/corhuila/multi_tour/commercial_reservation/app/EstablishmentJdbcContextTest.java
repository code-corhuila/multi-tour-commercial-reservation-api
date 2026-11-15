package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListEstablishmentChanges;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-002: the JDBC adapter of the associated establishments implements the repository AND the change log, so the wiring must name which one each use case
// takes. The application context has to start on a real PostgreSQL with that adapter, and the change log use case has to read through the very same adapter.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@ContextConfiguration(initializers = ReservationJdbcHttpTest.Database.class)
class EstablishmentJdbcContextTest {
    @Autowired ListEstablishmentChanges changes;
    @Autowired AssociatedEstablishmentRepository repository;

    @Test
    void theContextStartsOnPostgresWithOneChangeLogAndTheChangeLogUseCaseIsWired() {
        assertThat(changes).isNotNull();
        assertThat(repository).isInstanceOf(
                co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAssociatedEstablishmentRepository.class);
    }
}
