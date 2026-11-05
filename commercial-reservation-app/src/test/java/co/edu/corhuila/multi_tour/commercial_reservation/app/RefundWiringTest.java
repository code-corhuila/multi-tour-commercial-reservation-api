package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.calendar.WeekdayBusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcFinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcRefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageRefundDecisions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RefundAutomation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.BusinessCalendar;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancelledValueRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionOpener;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FinancialDecisionRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RefundAuthorizedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.FinancialDecisionOpening;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// HU-COM-016 plan D-05, D-09: the refund use cases are wired once over the JDBC adapters, and one FinancialDecisionOpening answers the four ports that the
// earlier stories left as stand-ins (013, 012, 010 and 011), without a reachable database.
@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class RefundWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void theRefundUseCasesExistOnceOverTheJdbcPortsAndTheWeekdayCalendar() {
        assertThat(context.getBeansOfType(ManageRefundDecisions.class)).hasSize(1);
        assertThat(context.getBeansOfType(RefundAutomation.class)).hasSize(1);
        assertThat(context.getBean(FinancialDecisionRepository.class)).isInstanceOf(JdbcFinancialDecisionRepository.class);
        assertThat(context.getBean(RefundAuthorizationRepository.class)).isInstanceOf(JdbcRefundAuthorizationRepository.class);
        assertThat(context.getBean(RefundAuthorizedOutbox.class)).isInstanceOf(JdbcRefundAuthorizedOutbox.class);
        assertThat(context.getBean(BusinessCalendar.class)).isInstanceOf(WeekdayBusinessCalendar.class);
    }

    @Test
    void oneOpeningAnswersTheFourPortsOfTheEarlierStories() {
        assertThat(context.getBeansOfType(FinancialDecisionOpening.class)).hasSize(1);
        assertThat(context.getBean(FinancialDecisionOpener.class)).isInstanceOf(FinancialDecisionOpening.class);
        assertThat(context.getBean(ExcessDecisionOpener.class)).isInstanceOf(FinancialDecisionOpening.class);
        assertThat(context.getBean(ExcessTreatment.class)).isInstanceOf(FinancialDecisionOpening.class);
        assertThat(context.getBean(CancelledValueRecorder.class)).isInstanceOf(FinancialDecisionOpening.class);
    }
}
