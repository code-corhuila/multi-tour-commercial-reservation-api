package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// The ceiling of a departure as the store holds it: the effective capacity, the seats held and whether the departure can still change.
public record CeilingState(int ceiling, int seatsHeld, boolean open) {}
