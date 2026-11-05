package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcAdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// HU-COM-004: the registration use case is wired once over the JDBC adapters, without a reachable database.
@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class AdditionalDiscountWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void theRegistrationUseCaseExistsOnceOverTheJdbcPorts() {
        assertThat(context.getBeansOfType(RegisterAdditionalDiscount.class)).hasSize(1);
        assertThat(context.getBean(ReservationReader.class)).isInstanceOf(JdbcReservationReader.class);
        assertThat(context.getBean(AdditionalDiscountRepository.class)).isInstanceOf(JdbcAdditionalDiscountRepository.class);
    }

    // D-06: the pending treatment is the real opening of HU-COM-016 now; opening it needs the database, so only the wiring is checked here.
    @Test
    void theExcessTreatmentPortIsAnsweredByTheRealOpening() {
        assertThat(context.getBeansOfType(ExcessTreatment.class)).hasSize(1);
        assertThat(context.getBean(ExcessTreatment.class)).isInstanceOf(FinancialDecisionOpening.class);
    }
}
