package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// A proof file above the 5 MB limit (AC-09.4): a validation error on the field proof that the HTTP adapter answers with 413, not 400.
public final class ProofTooLargeException extends DomainException {
    public ProofTooLargeException() {
        super("proof", "the proof file cannot be larger than 5 MB");
    }
}
