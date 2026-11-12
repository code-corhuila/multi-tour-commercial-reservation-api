package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DefaultingChangePolicyReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

// HU-COM-012 DP-01: the bean the modification and the quote read the terms through applies the default of the PDR when a tour has no row.
class ModificationPolicyWiringTest {

    @Test
    void theTermsBeanFallsBackToTheDefaultWhenATourHasNoRow() {
        assertThat(new ModificationConfiguration().changePolicyReader(new JdbcTemplate())).isInstanceOf(DefaultingChangePolicyReader.class);
    }
}
