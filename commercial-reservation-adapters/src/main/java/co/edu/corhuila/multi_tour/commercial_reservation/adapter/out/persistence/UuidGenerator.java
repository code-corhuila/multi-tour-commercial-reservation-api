package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import java.util.UUID;

public final class UuidGenerator implements IdGenerator {
    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
