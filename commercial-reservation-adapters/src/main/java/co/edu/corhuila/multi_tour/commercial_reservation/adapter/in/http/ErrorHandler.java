package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AssociatedEstablishmentNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.MissingHighRiskDataException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReasonRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EstablishmentAccessDeniedException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ErrorHandler {
    private final ApiErrorWriter errors;

    public ErrorHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception ex, HttpServletRequest request) {
        ApiError.Code code = ApiError.Code.INTERNAL_ERROR;
        String message = "an unexpected error occurred";
        List<ApiError.Detail> details = null;
        if (ex instanceof BusinessRuleViolation) {
            code = ApiError.Code.BUSINESS_RULE_VIOLATION;
            message = "PATCH".equals(request.getMethod())
                    ? "The capacity cannot be lower than the seats already held in a scheduled departure"
                    : "The item cannot be reactivated while a restriction applies";
        } else if (ex instanceof InvalidTokenException) {
            code = ApiError.Code.UNAUTHORIZED;
            message = "authentication is required";
        } else if (ex instanceof MissingHighRiskDataException missing) {
            // AC-05.2: one entry per missing field, so the screen can point at each.
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            details = missing.fields().stream().map(field -> new ApiError.Detail(field, field + " is required")).toList();
        } else if (ex instanceof ReasonRuleViolation violation) {
            code = ApiError.Code.BUSINESS_RULE_VIOLATION;
            message = violation.getMessage();
        } else if (ex instanceof DomainException domain) {
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            details = List.of(new ApiError.Detail(domain.field() == null ? "body" : domain.field(), domain.getMessage()));
        } else if (ex instanceof org.springframework.web.multipart.MultipartException) {
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            details = List.of(new ApiError.Detail("image", "must be a valid image within the multipart size limit"));
        } else if (ex instanceof HttpMediaTypeNotSupportedException) {
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            details = List.of(new ApiError.Detail("Content-Type", "must use a supported media type"));
        } else if (ex instanceof HttpMessageNotReadableException) {
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            String field = jsonField(ex);
            details = List.of(new ApiError.Detail(field,
                    field.equals("body") ? "must contain valid JSON" : "has an invalid value"));
        } else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            code = ApiError.Code.VALIDATION_ERROR;
            message = "the request has invalid fields";
            details = List.of(new ApiError.Detail(mismatch.getName(), "has an invalid format"));
        } else if (ex instanceof CatalogServiceNotFoundException || ex instanceof AssociatedEstablishmentNotFoundException
                || ex instanceof DiscountRuleNotFoundException || ex instanceof CustomerNotFoundException || ex instanceof AuthorizableReasonNotFoundException
                || ex instanceof NoResourceFoundException
                || ex instanceof NoHandlerFoundException || ex instanceof HttpRequestMethodNotSupportedException) {
            // Annex C has no method-not-allowed code; use the agreed missing-resource response.
            code = ApiError.Code.NOT_FOUND;
            message = "resource not found";
        } else if (ex instanceof EstablishmentAccessDeniedException || ex instanceof DiscountRuleAccessDeniedException
                || ex instanceof CustomerAccessDeniedException
                || ex instanceof ResponseStatusException status && status.getStatusCode().value() == 403) {
            code = ApiError.Code.FORBIDDEN;
            message = "access is forbidden";
        } else {
            LoggerFactory.getLogger(ErrorHandler.class).error("Unexpected request failure, traceId={}",
                    request.getAttribute(CorrelationFilter.TRACE_ATTRIBUTE), ex);
        }
        int status = ex instanceof EstablishmentImageContent.Rejected image ? image.status
                : ex instanceof RejectedRequest rejected ? rejected.status() : code.status();
        if (ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException) status = 413;
        return ResponseEntity.status(status).body(errors.body(request, code, message, details));
    }

    private static String jsonField(Throwable error) {
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof JsonMappingException mapping) {
                for (var reference : mapping.getPath()) {
                    if (reference.getFieldName() != null) return reference.getFieldName();
                }
            }
        }
        return "body";
    }
}
