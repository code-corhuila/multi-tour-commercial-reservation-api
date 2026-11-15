package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.List;
import java.util.Map;
import java.util.Set;

// {data, meta} of the contract; paging is cut here because a tenant holds few rules.
public record DiscountRulePageResponse(List<DiscountRuleResponse> data, CatalogPageResponse.Meta meta) {
    static DiscountRulePageResponse of(List<DiscountRule> all, Map<String, String> query) {
        for (String field : query.keySet()) {
            if (!Set.of("page", "limit").contains(field)) throw new DomainException(field, "unknown query parameter");
        }
        int page = integer(query, "page", 1, Integer.MAX_VALUE);
        int limit = integer(query, "limit", 20, 100);
        long pages = all.size() / limit + (all.size() % limit == 0 ? 0 : 1);
        int from = (int) Math.min((long) (page - 1) * limit, all.size());
        var data = all.subList(from, Math.min(from + limit, all.size())).stream().map(DiscountRuleResponse::from).toList();
        return new DiscountRulePageResponse(data, new CatalogPageResponse.Meta(page, limit, all.size(), pages));
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
