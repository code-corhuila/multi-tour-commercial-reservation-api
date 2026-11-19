package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.MirrorSalidaStateUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// Spec 027 on the real schema: SalidaRescheduled moves hora_inicio and estimated_ends_at with the processed event id, and never touches the held seats.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class DepartureReschedulePersistenceTest {
    private final UUID tenant = UUID.randomUUID();
    private JdbcTemplate jdbc;
    private JdbcDepartureStateRepository departures;
    private MirrorSalidaStateUseCase mirror;
    private CatalogService tour;

    @BeforeEach
    void connectAsTheApplicationUser() {
        assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        DriverManagerDataSource source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        JdbcCatalogServiceRepository catalog = new JdbcCatalogServiceRepository(jdbc, transactions);
        departures = new JdbcDepartureStateRepository(jdbc);
        mirror = new MirrorSalidaStateUseCase(departures, new JdbcProcessedEvents(jdbc), new JdbcSalidaMirrorTransaction(transactions), catalog);
        tour = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "Canyon",
                CommercialTerms.of(10_000_000L, 4_000_000L, 12, false, false), Instant.now());
        catalog.save(tour);
    }

    @Test
    void theMoveWritesTheStartAndTheEndWithTheMarkerAndKeepsTheHeldSeats() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID departureId = UUID.randomUUID();
        departures.save(new DepartureState(departureId, tenant, tour.serviceId(), now.plus(2, ChronoUnit.DAYS), DepartureStatus.SCHEDULED, 12, 0, now));
        jdbc.update("UPDATE commercial_reservation.salida_capacity SET held_seats = 4 WHERE tenant_id = ? AND salida_id = ?", tenant, departureId);
        Instant newStart = now.plus(9, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(6, ChronoUnit.HOURS);
        UUID eventId = UUID.randomUUID();
        SalidaEvent.Rescheduled event = new SalidaEvent.Rescheduled(eventId, tenant, departureId, tour.serviceId(), newStart, newEnd, now.plusSeconds(5));

        assertThat(mirror.apply(event)).isEqualTo(MirrorOutcome.APPLIED);
        assertThat(mirror.apply(event)).isEqualTo(MirrorOutcome.DUPLICATE);

        DepartureState stored = departures.find(tenant, departureId).orElseThrow();
        assertThat(stored.startsAt()).isEqualTo(newStart);
        assertThat(stored.heldSeats()).isEqualTo(4);
        assertThat(stored.lastEventOccurredAt()).isEqualTo(now.plusSeconds(5));
        Timestamp end = jdbc.queryForObject("SELECT estimated_ends_at FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?",
                Timestamp.class, tenant, departureId);
        assertThat(end.toInstant()).isEqualTo(newEnd);
    }

    @Test
    void aMoveOfAnotherTenantChangesNoRow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID departureId = UUID.randomUUID();
        Instant start = now.plus(2, ChronoUnit.DAYS);
        departures.save(new DepartureState(departureId, tenant, tour.serviceId(), start, DepartureStatus.SCHEDULED, 12, 0, now));

        departures.moveSchedule(UUID.randomUUID(), departureId, start.plus(1, ChronoUnit.DAYS), start.plus(2, ChronoUnit.DAYS), now);

        assertThat(departures.find(tenant, departureId).orElseThrow().startsAt()).isEqualTo(start);
    }
}
