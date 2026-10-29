package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import java.util.UUID;

// tenantId is never exposed.
public record AuthorizableReasonResponse(UUID reasonId, String text, String reasonStatus, String createdAt,
        String updatedAt) {
    static AuthorizableReasonResponse from(TransactionalAuthorizableReasons.Item item) {
        var reason = item.reason();
        return new AuthorizableReasonResponse(reason.reasonId(), reason.text(),
                reason.enabled() ? "ENABLED" : "DISABLED", item.times().createdAt().toString(),
                item.times().updatedAt().toString());
    }
}
