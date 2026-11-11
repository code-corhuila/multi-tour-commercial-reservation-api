package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-016 DP-07: forward only, with the single step back EXECUTING -> AUTHORIZED of the undo. Overdue is a mark, not a state.
public enum ExecutionState {
    AUTHORIZED, EXECUTING, EXECUTED
}
