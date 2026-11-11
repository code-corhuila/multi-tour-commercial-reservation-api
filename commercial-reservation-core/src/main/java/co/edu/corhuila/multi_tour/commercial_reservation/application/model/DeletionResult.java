package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

// HU-COM-019 plan section 2: what a deletion call answers. A reservation that is not due, has no anchor or was already deleted is an answer, not an error.
public record DeletionResult(Outcome outcome, int companionRows, boolean customerDataDeleted) {
    public enum Outcome { DELETED, NOT_DUE, NO_ANCHOR, ALREADY_DELETED }
}
