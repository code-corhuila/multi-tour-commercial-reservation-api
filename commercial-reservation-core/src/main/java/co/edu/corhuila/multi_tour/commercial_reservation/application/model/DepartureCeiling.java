package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// What the ceiling route answers: the ceiling before the call, the ceiling now and the seats held.
public record DepartureCeiling(int previousCeiling, int ceiling, int seatsHeld) {}
