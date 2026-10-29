package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.FieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

// Sensitive values are encrypted here, before they reach the SQL, and decrypted only when a row is read. The
// associated data binds each envelope to its tenant, customer and column, so it cannot be moved to another row.
// It writes inside the transaction of the caller (JdbcCustomerTransaction) and holds no value in any message.
public final class JdbcCustomerRepository implements CustomerRepository, CustomerProfileReader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String STATUS = "ACTIVE"; // DP-D5-6 placeholder: the status catalogue is not decided
    private final JdbcTemplate jdbc;
    private final FieldCipher cipher;

    public JdbcCustomerRepository(JdbcTemplate jdbc, FieldCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    // Registration only: a customer id that exists, in any tenant, is refused by the primary key.
    @Override
    public void save(Customer customer) {
        UUID tenant = customer.tenantId();
        UUID id = customer.customerId();
        jdbc.update("INSERT INTO commercial_reservation.customer (customer_id, tenant_id, full_name, created_at, "
                + "document_number, birth_date_or_age, contact_info, status) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)",
                id, tenant, customer.fullName(), timestamp(customer.createdAt()),
                seal(customer.documentNumber().value(), tenant, id, "document_number"),
                seal(birth(customer.birthDateOrAge()), tenant, id, "birth_date_or_age"),
                envelopeJson(seal(contact(customer.contact()), tenant, id, "contact_info")), STATUS);
        if (customer.highRiskData() != null) {
            jdbc.update("INSERT INTO commercial_reservation.customer_sensitive_data (customer_id, tenant_id, "
                    + "emergency_info) VALUES (?, ?, ?::jsonb)", id, tenant,
                    envelopeJson(seal(risk(customer.highRiskData()), tenant, id, "emergency_info")));
        }
    }

    @Override
    public Optional<Customer> findByTenantAndId(UUID tenantId, UUID customerId) {
        return jdbc.query("SELECT c.full_name, c.created_at, c.document_number, c.birth_date_or_age, "
                + "c.contact_info ->> 'envelope' AS contact_envelope, s.emergency_info ->> 'envelope' AS emergency_envelope "
                + "FROM commercial_reservation.customer c LEFT JOIN commercial_reservation.customer_sensitive_data s "
                + "ON s.tenant_id = c.tenant_id AND s.customer_id = c.customer_id "
                + "WHERE c.tenant_id = ? AND c.customer_id = ?", (row, index) -> {
                    String emergency = row.getString("emergency_envelope");
                    return Customer.restore(customerId, tenantId,
                            DocumentNumber.of(open(row.getString("document_number"), tenantId, customerId, "document_number")),
                            row.getString("full_name"),
                            birth(open(row.getString("birth_date_or_age"), tenantId, customerId, "birth_date_or_age")),
                            contact(open(row.getString("contact_envelope"), tenantId, customerId, "contact_info")),
                            row.getObject("created_at", OffsetDateTime.class).toInstant(),
                            emergency == null ? null : risk(open(emergency, tenantId, customerId, "emergency_info")));
                }, tenantId, customerId).stream().findFirst();
    }

    // The general read: the sensitive table is neither joined nor decrypted, so no risk data can leave through it.
    @Override
    public Optional<Customer> findOrdinary(UUID tenantId, UUID customerId) {
        return jdbc.query("SELECT full_name, created_at, document_number, birth_date_or_age, "
                + "contact_info ->> 'envelope' AS contact_envelope FROM commercial_reservation.customer "
                + "WHERE tenant_id = ? AND customer_id = ?", (row, index) -> Customer.restore(customerId, tenantId,
                        DocumentNumber.of(open(row.getString("document_number"), tenantId, customerId, "document_number")),
                        row.getString("full_name"),
                        birth(open(row.getString("birth_date_or_age"), tenantId, customerId, "birth_date_or_age")),
                        contact(open(row.getString("contact_envelope"), tenantId, customerId, "contact_info")),
                        row.getObject("created_at", OffsetDateTime.class).toInstant(), null),
                tenantId, customerId).stream().findFirst();
    }

    private String seal(String plain, UUID tenant, UUID customer, String column) {
        return cipher.encrypt(plain, aad(tenant, customer, column));
    }

    private String open(String envelope, UUID tenant, UUID customer, String column) {
        return cipher.decrypt(envelope, aad(tenant, customer, column));
    }

    private static String aad(UUID tenant, UUID customer, String column) {
        return tenant + "|" + customer + "|" + column;
    }

    // The envelope alphabet is base64, ':' and the key id, so it needs no JSON escaping.
    private static String envelopeJson(String envelope) {
        return "{\"envelope\":\"" + envelope + "\"}";
    }

    private static String birth(BirthDateOrAge value) {
        return value.birthDate() != null ? "D:" + value.birthDate() : "A:" + value.age();
    }

    private static BirthDateOrAge birth(String stored) {
        String body = stored.substring(2);
        return stored.startsWith("D:") ? new BirthDateOrAge(LocalDate.parse(body), null)
                : new BirthDateOrAge(null, Integer.valueOf(body));
    }

    private static String contact(ContactInfo value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", value.email());
        body.put("phone", value.phone());
        return write(body);
    }

    private static ContactInfo contact(String stored) {
        JsonNode node = read(stored);
        return new ContactInfo(text(node, "email"), text(node, "phone"));
    }

    private static String risk(HighRiskData value) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bloodType", value.bloodType());
        body.put("emergencyContact", value.emergencyContact());
        body.put("mobilityRestrictions", value.mobilityRestrictions());
        body.put("informedConsentSigned", value.informedConsentSigned());
        return write(body);
    }

    private static HighRiskData risk(String stored) {
        JsonNode node = read(stored);
        return new HighRiskData(text(node, "bloodType"), text(node, "emergencyContact"),
                text(node, "mobilityRestrictions"), node.path("informedConsentSigned").asBoolean(false));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static String write(Map<String, Object> body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a customer value could not be serialized");
        }
    }

    private static JsonNode read(String stored) {
        try {
            return JSON.readTree(stored);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("a stored customer value is malformed");
        }
    }

    private static OffsetDateTime timestamp(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
