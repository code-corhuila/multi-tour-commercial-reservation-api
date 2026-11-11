package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.List;

// The paginated proofs and the count of those past their review window, which the panel shows as the alert.
public record PaymentProofPageResponse(List<ProofResponse> data, ReservationListResponses.Meta meta, long overdueCount) {
}
