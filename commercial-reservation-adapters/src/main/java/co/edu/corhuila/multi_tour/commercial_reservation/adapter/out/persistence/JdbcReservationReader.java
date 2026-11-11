package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreatedReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Every read is filtered by tenant, so another tenant's reservation is the same empty answer as a missing one (RN-09).
// The creation values are rebuilt from the stored rows and the lines are read as stored: no rule in force is evaluated (RN-04).
public final class JdbcReservationReader implements ReservationReader, CreatedReservationReader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;

    public JdbcReservationReader(JdbcTemplate jdbc, FieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    // Subtotal: what the fixed lines left (the projected value when there is none). Paid: 0 until HU-COM-010. Applied: the
    // fixed lines that took something plus the additional discount (plan D-09).
    @Override
    public Optional<ReservationSnapshot> findByTenantAndId(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT r.reservation_status, COALESCE((SELECT f.subtotal_cents FROM "
                + "commercial_reservation.reservation_fixed_discount f WHERE f.reservation_id = r.reservation_id "
                + "ORDER BY f.line_order DESC LIMIT 1), r.projected_value_cents) AS subtotal, "
                + "(SELECT count(*) FROM commercial_reservation.reservation_fixed_discount f "
                + "WHERE f.reservation_id = r.reservation_id AND f.amount_cents > 0) "
                + "+ (SELECT count(*) FROM commercial_reservation.reservation_additional_discount a "
                + "WHERE a.reservation_id = r.reservation_id) AS applied "
                + "FROM commercial_reservation.reservation r WHERE r.tenant_id = ? AND r.reservation_id = ?",
                (row, index) -> new ReservationSnapshot(tenantId, reservationId, state(row.getString("reservation_status")),
                        new Cents(row.getLong("subtotal")), new Cents(0), row.getInt("applied")),
                tenantId, reservationId).stream().findFirst();
    }

    @Override
    public Optional<Reservation> findAsCreated(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT r.customer_id, r.salida_id, r.service_id, r.party_size, r.projected_value_cents, "
                + "r.payment_type, r.payment_method, r.created_by, r.created_at, s.base_rate_cents, s.transport_rate_cents "
                + "FROM commercial_reservation.reservation r JOIN commercial_reservation.reserved_service s "
                + "ON s.tenant_id = r.tenant_id AND s.reservation_id = r.reservation_id "
                + "WHERE r.tenant_id = ? AND r.reservation_id = ?", (row, index) -> {
                    long transport = row.getLong("transport_rate_cents");
                    boolean noTransport = row.wasNull();
                    var service = new ReservedService(row.getObject("service_id", UUID.class),
                            new Cents(row.getLong("base_rate_cents")), noTransport ? null : new Cents(transport));
                    List<FixedDiscount> lines = lines(tenantId, reservationId);
                    Cents projected = new Cents(row.getLong("projected_value_cents"));
                    return Reservation.create(reservationId, tenantId, row.getObject("customer_id", UUID.class),
                            row.getObject("salida_id", UUID.class), row.getObject("created_by", UUID.class),
                            row.getInt("party_size"), service, projected,
                            lines.isEmpty() ? projected : lines.get(lines.size() - 1).subtotal(), lines,
                            PaymentType.fromCode(row.getString("payment_type")),
                            PaymentMethod.fromCode(row.getString("payment_method")),
                            row.getObject("created_at", OffsetDateTime.class).toInstant(), companions(tenantId, reservationId));
                }, tenantId, reservationId).stream().findFirst();
    }

    private List<FixedDiscount> lines(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT line_order, discount_rule_id, rule_name, discount_type, discount_percentage_bp, "
                + "discount_amount_cents, calculation_base, base_cents, amount_cents, subtotal_cents, cap_cents "
                + "FROM commercial_reservation.reservation_fixed_discount WHERE tenant_id = ? AND reservation_id = ? "
                + "ORDER BY line_order", (row, index) -> {
                    int percentage = row.getInt("discount_percentage_bp");
                    Integer basisPoints = row.wasNull() ? null : percentage;
                    long amount = row.getLong("discount_amount_cents");
                    Long fixed = row.wasNull() ? null : amount;
                    long cap = row.getLong("cap_cents");
                    Long capCents = row.wasNull() ? null : cap;
                    return new FixedDiscount(row.getInt("line_order"), row.getObject("discount_rule_id", UUID.class),
                            row.getString("rule_name"), DiscountType.valueOf(row.getString("discount_type")), basisPoints,
                            fixed, DiscountBase.valueOf(row.getString("calculation_base")),
                            new Cents(row.getLong("base_cents")), new Cents(row.getLong("amount_cents")),
                            new Cents(row.getLong("subtotal_cents")), capCents);
                }, tenantId, reservationId);
    }

    // No order was stored: the companions come back by document, which is unique in the reservation.
    private List<Companion> companions(UUID tenantId, UUID reservationId) {
        return jdbc.query("SELECT c.reservation_companion_id, c.full_name, c.document_number, c.birth_date_or_age, "
                + "s.activity_requirements ->> 'envelope' AS requirements "
                + "FROM commercial_reservation.reservation_companion c LEFT JOIN "
                + "commercial_reservation.reservation_companion_sensitive_data s "
                + "ON s.reservation_companion_id = c.reservation_companion_id "
                + "WHERE c.tenant_id = ? AND c.reservation_id = ? ORDER BY c.document_number", (row, index) -> {
                    UUID id = row.getObject("reservation_companion_id", UUID.class);
                    String requirements = row.getString("requirements");
                    return new Companion(row.getString("full_name"), DocumentNumber.of(row.getString("document_number")),
                            birth(cipher.decrypt(row.getString("birth_date_or_age"), aad(tenantId, id, "birth_date_or_age"))),
                            requirements == null ? null
                                    : risk(cipher.decrypt(requirements, aad(tenantId, id, "activity_requirements"))));
                }, tenantId, reservationId);
    }

    private static ReservationSnapshot.State state(String status) {
        return switch (status) {
            case "PENDING_PAYMENT" -> ReservationSnapshot.State.PENDING_PAYMENT;
            case "CONFIRMED" -> ReservationSnapshot.State.CONFIRMED;
            case "CANCELLED" -> ReservationSnapshot.State.CANCELLED;
            default -> ReservationSnapshot.State.OTHER;
        };
    }

    private static String aad(UUID tenant, UUID companion, String column) {
        return tenant + "|" + companion + "|" + column;
    }

    private static BirthDateOrAge birth(String stored) {
        String body = stored.substring(2);
        return stored.startsWith("D:") ? new BirthDateOrAge(LocalDate.parse(body), null)
                : new BirthDateOrAge(null, Integer.valueOf(body));
    }

    private static HighRiskData risk(String stored) {
        try {
            JsonNode node = JSON.readTree(stored);
            return new HighRiskData(text(node, "bloodType"), text(node, "emergencyContact"),
                    text(node, "mobilityRestrictions"), node.path("informedConsentSigned").asBoolean(false));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a stored companion value is malformed");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }
}
