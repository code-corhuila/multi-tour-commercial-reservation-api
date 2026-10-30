package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeparturePage;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import java.util.List;
import java.util.UUID;

// The paginated answers of the reservation routes: {data, meta}. estimatedEndsAt is absent until its source exists (DP-04).
public record ReservationListResponses<T>(List<T> data, Meta meta) {
    public record Meta(int page, int limit, long total, long totalPages) {}

    public record Departure(UUID departureId, String startsAt, int availableSeats, int totalSeats, boolean full) {
        static Departure of(DepartureState state) {
            int available = state.seats().available();
            return new Departure(state.departureId(), state.startsAt().toString(), available, state.effectiveCapacity(), available == 0);
        }
    }

    static ReservationListResponses<Departure> of(DeparturePage page, int number, int limit) {
        return new ReservationListResponses<>(page.items().stream().map(Departure::of).toList(), meta(number, limit, page.total()));
    }

    private static Meta meta(int page, int limit, long total) {
        return new Meta(page, limit, total, total / limit + (total % limit == 0 ? 0 : 1));
    }
}
