package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcTenantSuspensions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.SuspensionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

// HU-COM-020: the suspension history is one adapter that answers two ports (SuspensionStore for the tenant event, SuspensionReader for the proof decisions and reads). The use cases ask for them with @Lazy,
// so a second bean of the same class only fails at the first call (NoUniqueBeanDefinitionException, HTTP 500); this test resolves both ports by type in the real context, without a reachable database.
@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class SuspensionWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void theStoreAndTheReaderResolveByTypeToTheSameSingleJdbcAdapter() {
        assertThat(context.getBeansOfType(JdbcTenantSuspensions.class)).hasSize(1);
        assertThat(context.getBeansOfType(SuspensionStore.class)).hasSize(1);
        assertThat(context.getBeansOfType(SuspensionReader.class)).hasSize(1);
        assertThat(context.getBean(SuspensionStore.class)).isSameAs(context.getBean(SuspensionReader.class));
    }
}
