package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;

// HU-COM-019 RN-02, declared debt DD-2: the definitive closing of a cancelled reservation, which belongs to HU-COM-016 and HU-COM-017. When the inactivation of the
// tenant froze a decision period the closing is blocked, and the year counts from the cancellation instead (AC-19.5).
public record Closing(Instant at, boolean blockedByInactivation) {
}
