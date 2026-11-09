package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// HU-COM-018 DP-13: a proposal is pending until it is answered (accepted or rejected), voided (its departure was cancelled) or expired.
public enum ProposalState {
    PENDING, ACCEPTED, REJECTED, VOID, EXPIRED
}
