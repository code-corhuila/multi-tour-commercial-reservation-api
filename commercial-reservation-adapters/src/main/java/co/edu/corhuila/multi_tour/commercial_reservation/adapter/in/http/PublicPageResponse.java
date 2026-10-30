package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.List;

// {data, meta} of the public lists (guidelines 1.6). The items are the allow-list records of the core, never a model of the management side.
public record PublicPageResponse<T>(List<T> data, Meta meta) {
    public record Meta(int page, int limit, long total, long totalPages) {}

    static <T> PublicPageResponse<T> of(List<T> items, long total, int page, int limit) {
        return new PublicPageResponse<>(items, new Meta(page, limit, total, (total + limit - 1) / limit));
    }
}
