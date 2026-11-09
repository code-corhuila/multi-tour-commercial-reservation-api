package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FreezeCause;
import java.time.Instant;
import java.util.UUID;

// HU-COM-018 AC-18.8, HU-COM-020 AC-20.2: freezes every open economic deadline of a reservation at an instant with the time it had left and the cause, in the caller's
// transaction. Freezing a frozen row changes nothing. The adapter writes frozen_at and remaining_at_freeze_minutes of HU-COM-011 (and the cause of HU-COM-020).
public interface EconomicDeadlineFreezer {

    void freeze(UUID tenantId, UUID reservationId, Instant at, FreezeCause cause);
}
