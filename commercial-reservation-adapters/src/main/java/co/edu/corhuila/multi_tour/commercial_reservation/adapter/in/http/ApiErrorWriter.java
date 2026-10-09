package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;

public class ApiErrorWriter {
    private final ObjectMapper json;

    public ApiErrorWriter(ObjectMapper json) {
        this.json = json;
    }

    public ApiError body(HttpServletRequest request, ApiError.Code code, String message, List<ApiError.Detail> details) {
        return new ApiError(code.name(), message, details,
                (String) request.getAttribute(CorrelationFilter.TRACE_ATTRIBUTE));
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ApiError.Code code, String message)
            throws IOException {
        response.setStatus(code.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body(request, code, message, null));
    }
}
