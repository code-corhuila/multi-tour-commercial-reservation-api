package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, List<Detail> details, String traceId) {
    public record Detail(String field, String message) {}
    public enum Code {
        VALIDATION_ERROR(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404),
        INVALID_STATUS_TRANSITION(422), BUSINESS_RULE_VIOLATION(422), INTERNAL_ERROR(500);
        private final int status;
        Code(int status) { this.status = status; }
        public int status() { return status; }
    }
}
