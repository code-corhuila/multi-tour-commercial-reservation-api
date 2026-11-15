package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataAccessed;

public interface SensitiveDataAccessOutbox {
    void append(SensitiveDataAccessed event);
}
