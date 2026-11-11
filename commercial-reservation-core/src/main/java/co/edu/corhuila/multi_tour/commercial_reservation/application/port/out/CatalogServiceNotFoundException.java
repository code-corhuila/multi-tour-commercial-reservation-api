package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also raised for an item of another tenant: a 403 would reveal that it exists (RN-09, T-08).
public class CatalogServiceNotFoundException extends RuntimeException {

    public CatalogServiceNotFoundException(UUID serviceId) {
        super("catalog service " + serviceId + " not found");
    }
}
