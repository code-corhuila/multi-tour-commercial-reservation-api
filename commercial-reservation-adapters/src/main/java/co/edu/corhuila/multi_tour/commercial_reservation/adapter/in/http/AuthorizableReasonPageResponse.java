package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.List;

public record AuthorizableReasonPageResponse(List<AuthorizableReasonResponse> data, CatalogPageResponse.Meta meta) {
    static AuthorizableReasonPageResponse of(List<TransactionalAuthorizableReasons.Item> all, int page, int limit) {
        long pages = all.size() / limit + (all.size() % limit == 0 ? 0 : 1);
        int from = (int) Math.min((long) (page - 1) * limit, all.size());
        var data = all.subList(from, Math.min(from + limit, all.size())).stream()
                .map(AuthorizableReasonResponse::from).toList();
        return new AuthorizableReasonPageResponse(data, new CatalogPageResponse.Meta(page, limit, all.size(), pages));
    }
}
