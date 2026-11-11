package co.edu.corhuila.multi_tour.commercial_reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class NoDeletionTest {

    // RN-06 is sustained by an absence, and an absence rots in silence.
    @Test
    void aggregateExposesNoDeletion() {
        assertThat(CatalogService.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(NoDeletionTest::isDeletionName);
    }

    @Test
    void outboundPortExposesNoDeletion() {
        assertThat(CatalogServiceRepository.class.getMethods())
                .extracting(Method::getName)
                .noneMatch(NoDeletionTest::isDeletionName);
    }

    private static boolean isDeletionName(String name) {
        return name.toLowerCase(Locale.ROOT).matches("^(delete|remove|purge|erase|destroy).*");
    }
}
