package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-018 plan D-02, DP-13: an episode is open until it ends as an accepted rescheduling or as a cancelled reservation.
public enum EpisodeState {
    OPEN, RESCHEDULED, CANCELLED
}
