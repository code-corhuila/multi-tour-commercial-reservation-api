package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// Spec 023: DONE when every alive reservation has its episode and the event is marked; RETRY_LATER when one failed, so the adapter answers 503 and the relay redelivers.
public enum CancelledDepartureOutcome {
    DONE, RETRY_LATER
}
