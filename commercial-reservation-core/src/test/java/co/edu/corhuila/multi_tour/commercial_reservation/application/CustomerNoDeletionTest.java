package co.edu.corhuila.multi_tour.commercial_reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.Test;

// Spec section 6: no physical deletion of a person; the repository only saves and finds by tenant and id.
class CustomerNoDeletionTest {

    @Test
    void aggregateExposesNoDeletion() {
        assertThat(Customer.class.getMethods()).extracting(Method::getName)
                .noneMatch(CustomerNoDeletionTest::isDeletion);
    }

    @Test
    void outboundPortExposesOnlySaveAndFindByTenant() {
        assertThat(CustomerRepository.class.getMethods()).extracting(Method::getName)
                .containsExactlyInAnyOrder("save", "findByTenantAndId");
    }

    private static boolean isDeletion(String name) {
        return name.toLowerCase(Locale.ROOT).matches("^(delete|remove|purge|erase|destroy).*");
    }
}
