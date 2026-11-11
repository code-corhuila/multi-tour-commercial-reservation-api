package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentRegistration;
import java.util.UUID;
import java.util.function.Supplier;

public interface EstablishmentImages {
    // Image bytes, association, events and key must commit together; a replay stores no second image.
    EstablishmentRegistration register(byte[] bytes, String mediaType, Supplier<EstablishmentRegistration> registration);
    Image read(UUID tenantId, String reference);

    record Image(byte[] bytes, String mediaType) {
        public Image { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
