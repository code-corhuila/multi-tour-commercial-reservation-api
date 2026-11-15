package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EmergencyOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PersonalEmergency;

// Spec 024: applies PersonalEmergencyRecorded once. The refund is the one the service determines (spec 026); the reservation is never changed.
public interface ApplyPersonalEmergency {
    EmergencyOutcome apply(PersonalEmergency emergency);
}
