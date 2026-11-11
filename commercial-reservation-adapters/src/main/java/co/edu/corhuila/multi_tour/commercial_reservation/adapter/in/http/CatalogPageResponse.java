package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CatalogPage;
import java.util.List;

public record CatalogPageResponse(List<CatalogServiceResponse> data, Meta meta) {
    public record Meta(int page, int limit, long total, long totalPages) {}

    static CatalogPageResponse from(CatalogPage result, int page, int limit) {
        long pages = result.total() / limit + (result.total() % limit == 0 ? 0 : 1);
        return new CatalogPageResponse(result.items().stream().map(CatalogServiceResponse::from).toList(),
                new Meta(page, limit, result.total(), pages));
    }
}
