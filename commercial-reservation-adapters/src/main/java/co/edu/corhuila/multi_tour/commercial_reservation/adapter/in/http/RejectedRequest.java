package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;

// A request refused by size or media type before its body is read: the answer keeps the status of the contract (413, 415).
public final class RejectedRequest extends DomainException {
    private final int status;

    public RejectedRequest(int status, String message) {
        super("body", message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
