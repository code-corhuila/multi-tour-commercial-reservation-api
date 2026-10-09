package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;

// Boxed money preserves absence so the core can name the missing field (AC-01.2).
public record CreateCatalogServiceRequest(
        @JsonProperty(required = true) @JsonSetter(nulls = Nulls.FAIL) CatalogServiceType serviceType,
        String commercialName, Long baseRateCents, Long baseOperationalCostCents,
        @JsonDeserialize(using = OptionalCapacity.class) Integer configuredCapacity,
        @JsonProperty(required = true) boolean transportRequired,
        @JsonProperty(required = true) boolean highRisk) {
    // The contract permits omission, but not an explicit null; creator defaults must distinguish them.
    public static final class OptionalCapacity extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            return parser.readValueAs(Integer.class);
        }

        @Override
        public Integer getNullValue(DeserializationContext context) throws JsonMappingException {
            return context.reportInputMismatch(Integer.class, "configuredCapacity must not be null");
        }

        @Override
        public Integer getAbsentValue(DeserializationContext context) {
            return null;
        }
    }
}
