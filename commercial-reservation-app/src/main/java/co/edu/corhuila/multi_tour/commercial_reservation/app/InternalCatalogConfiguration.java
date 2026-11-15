package co.edu.corhuila.multi_tour.commercial_reservation.app;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http.InternalCatalogController;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// G-API8-1: the internal read of a catalog item for the saga change-departure-vehicles, over the same use case as the public read.
@Configuration
class InternalCatalogConfiguration {

    @Bean
    InternalCatalogController internalCatalogController(GetCatalogService get) {
        return new InternalCatalogController(get);
    }
}
