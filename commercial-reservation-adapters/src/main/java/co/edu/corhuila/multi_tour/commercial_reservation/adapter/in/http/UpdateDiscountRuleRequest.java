package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRulePatch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// JSON merge of the contract (updateDiscountRule): the body is read as a tree so that an absent field and a
// null stay different, and a field the contract does not accept is named (identifier, tenant, status, timestamps).
public final class UpdateDiscountRuleRequest {
    private static final Set<String> ACCEPTED = Set.of("name", "validFrom", "validTo", "priority", "calculationBase",
            "percentageBasisPoints", "amountCents", "serviceCoverage", "serviceIds", "capCents", "stackable");

    private UpdateDiscountRuleRequest() { }

    public static DiscountRulePatch toPatch(JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty())
            throw new DomainException("body", "at least one field to change is required");
        body.fieldNames().forEachRemaining(field -> {
            if (!ACCEPTED.contains(field)) throw new DomainException(field, field + " is not accepted by this operation");
        });
        DiscountRulePatch patch = DiscountRulePatch.none();
        if (body.has("name")) patch = patch.withName(text(body, "name"));
        if (body.has("validFrom")) patch = patch.withValidFrom(date(body, "validFrom"));
        if (body.has("validTo")) patch = patch.withValidTo(date(body, "validTo"));
        if (body.has("priority")) patch = patch.withPriority(integer(body, "priority"));
        if (body.has("calculationBase")) patch = patch.withCalculationBase(base(body));
        if (body.has("percentageBasisPoints")) patch = patch.withPercentageBasisPoints(integer(body, "percentageBasisPoints"));
        if (body.has("amountCents")) patch = patch.withAmountCents(longValue(body, "amountCents"));
        if (body.has("capCents")) patch = patch.withCapCents(body.get("capCents").isNull() ? null : longValue(body, "capCents"));
        if (body.has("stackable")) patch = patch.withStackable(bool(body, "stackable"));
        if (body.has("serviceCoverage") || body.has("serviceIds")) patch = patch.withServiceCoverage(coverage(body));
        return patch;
    }

    private static ServiceCoverage coverage(JsonNode body) {
        if (!body.has("serviceCoverage"))
            throw new DomainException("serviceIds", "serviceIds are accepted only with serviceCoverage SELECTED");
        String kind = text(body, "serviceCoverage");
        boolean hasIds = body.has("serviceIds") && !(body.get("serviceIds").isArray() && body.get("serviceIds").isEmpty());
        if ("ALL".equals(kind)) {
            if (hasIds) throw new DomainException("serviceIds", "serviceIds are accepted only with serviceCoverage SELECTED");
            return ServiceCoverage.all();
        }
        if (!"SELECTED".equals(kind)) throw new DomainException("serviceCoverage", "serviceCoverage must be ALL or SELECTED");
        Set<UUID> ids = new LinkedHashSet<>();
        JsonNode list = body.get("serviceIds");
        if (list != null && !list.isNull()) {
            if (!list.isArray()) throw new DomainException("serviceIds", "serviceIds must be a list of identifiers");
            for (JsonNode item : list) {
                try { ids.add(UUID.fromString(item.asText())); }
                catch (IllegalArgumentException invalid) { throw new DomainException("serviceIds", "serviceIds must hold identifiers"); }
            }
        }
        return ServiceCoverage.of(ids);
    }

    private static String text(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) throw new DomainException(field, field + " must be a text");
        return node.asText();
    }

    private static LocalDate date(JsonNode body, String field) {
        try { return LocalDate.parse(text(body, field)); }
        catch (DateTimeParseException invalid) { throw new DomainException(field, field + " must be a date (yyyy-MM-dd)"); }
    }

    private static Integer integer(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) throw new DomainException(field, field + " must be an integer");
        return node.asInt();
    }

    private static Long longValue(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) throw new DomainException(field, field + " must be an integer");
        return node.asLong();
    }

    private static Boolean bool(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isBoolean()) throw new DomainException(field, field + " must be true or false");
        return node.asBoolean();
    }

    private static DiscountBase base(JsonNode body) {
        try { return DiscountBase.valueOf(text(body, "calculationBase")); }
        catch (IllegalArgumentException invalid) { throw new DomainException("calculationBase", "calculationBase must be ORIGINAL_VALUE or PREVIOUS_SUBTOTAL"); }
    }
}
