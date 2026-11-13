package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.Optional;

// HU-COM-020 AC-20.13, RN-12, DP-05 (PROPOSED default): the reason that the envelope of an event carries when the action in course is a restricted one. It is the marker and nothing else, never a value.
// The outbox ports read it, so no command carries a flag. Work outside a request has no marker.
@FunctionalInterface
public interface ActionMarker {
    String RESTRICTED_ACCESS = "RESTRICTED_ACCESS";
    ActionMarker NONE = Optional::empty;

    Optional<String> reason();
}
