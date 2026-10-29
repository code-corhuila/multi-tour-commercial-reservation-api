package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcSalidaMirrorTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.MirrorSalidaState;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SalidaMirrorTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// HU-COM-006: the departure ports resolve to their JDBC adapters without a reachable database.
@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class DepartureWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void theDeparturePortsAndTheMirrorUseCaseExistOnceEach() {
        assertThat(context.getBean(DepartureReader.class)).isInstanceOf(JdbcDepartureReader.class);
        assertThat(context.getBean(DepartureStateRepository.class)).isInstanceOf(JdbcDepartureStateRepository.class);
        assertThat(context.getBean(ProcessedEvents.class)).isInstanceOf(JdbcProcessedEvents.class);
        assertThat(context.getBean(SalidaMirrorTransaction.class)).isInstanceOf(JdbcSalidaMirrorTransaction.class);
        assertThat(context.getBeansOfType(MirrorSalidaState.class)).hasSize(1);
    }
}
