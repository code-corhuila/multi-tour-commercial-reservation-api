package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.MirrorOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.SalidaEvent;

// Applies a departure event once, moving the status only forward (RN-11, AC-06.14, AC-06.16).
public interface MirrorSalidaState {
    MirrorOutcome apply(SalidaEvent event);
}
