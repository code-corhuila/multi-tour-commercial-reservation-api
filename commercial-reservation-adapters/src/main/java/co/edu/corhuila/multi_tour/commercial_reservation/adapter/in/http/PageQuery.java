package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.Map;
import java.util.Set;

// The page and limit of a list route: from 1, at most 100, and no parameter the contract does not name.
final class PageQuery {
    final int page, limit;

    PageQuery(Map<String, String> query, Set<String> allowed) {
        for (String field : query.keySet()) {
            if (!allowed.contains(field)) throw new DomainException(field, "unknown query parameter");
        }
        page = integer(query, "page", 1, Integer.MAX_VALUE);
        limit = integer(query, "limit", 20, 100);
    }

    ReservationListResponses.Meta meta(long total) {
        return new ReservationListResponses.Meta(page, limit, total, total / limit + (total % limit == 0 ? 0 : 1));
    }

    private static int integer(Map<String, String> query, String field, int fallback, int maximum) {
        try {
            int value = query.containsKey(field) ? Integer.parseInt(query.get(field)) : fallback;
            if (value < 1 || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new DomainException(field, "must be an integer between 1 and " + maximum);
        }
    }
}
