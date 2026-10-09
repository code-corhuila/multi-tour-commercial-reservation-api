package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.InactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListCatalogServicesForManagement;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ListOfferedCatalogServices;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReactivateCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterCatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateDescriptiveInformation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = TestJwtKeys.class)
class CatalogWiringTest {
    @Autowired
    private ApplicationContext context;

    @Test
    void allEightUseCasesExistWithoutAReachableDatabase() {
        for (Class<?> port : new Class<?>[] {RegisterCatalogService.class, UpdateCommercialTerms.class,
                UpdateDescriptiveInformation.class, InactivateCatalogService.class, ReactivateCatalogService.class,
                GetCatalogService.class, ListCatalogServicesForManagement.class, ListOfferedCatalogServices.class}) {
            assertThat(context.getBeansOfType(port)).as(port.getSimpleName()).hasSize(1);
        }
        IdGenerator ids = context.getBean(IdGenerator.class);
        assertThat(ids.newId().version()).isEqualTo(4);
        assertThat(ids.newId()).isNotEqualTo(ids.newId());
    }
}
