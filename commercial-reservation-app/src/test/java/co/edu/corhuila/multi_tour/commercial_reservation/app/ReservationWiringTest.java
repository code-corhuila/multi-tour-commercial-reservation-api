package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcEnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.EnabledPaymentOptions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TransportRateReader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// HU-COM-007: every port the quote and the creation need resolves to one real adapter, without a reachable database.
// Before this, the lazy ports failed on the first call with NoSuchBeanDefinitionException or UnsupportedOperationException.
@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class ReservationWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void thePortsOfTheQuoteAndTheCreationExistOnceEach() {
        assertThat(context.getBean(DepartureReader.class)).isInstanceOf(JdbcDepartureReader.class);
        assertThat(context.getBean(ReservationRepository.class)).isInstanceOf(JdbcReservationRepository.class);
        assertThat(context.getBean(EnabledPaymentOptions.class)).isInstanceOf(JdbcEnabledPaymentOptions.class);
        assertThat(context.getBeansOfType(TransportRateReader.class)).hasSize(1);
    }

    @Test
    void theReadsOfTheListAndTheDetailExistOnce() {
        assertThat(context.getBeansOfType(ReservationQueries.class)).hasSize(1);
        assertThat(context.getBean(ReservationQueries.class)).isInstanceOf(JdbcReservationQueries.class);
    }

    @Test
    void untilTheFareHasAHomeNoTourHasAParametrizedTransportRate() {
        assertThat(context.getBean(TransportRateReader.class).perPersonCents(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID())).isEmpty();
    }
}
