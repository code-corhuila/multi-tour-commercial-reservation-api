package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Lead customer (RN-01). Only the 06-data fields: document, name, birth date or age, contact, terms.
// TODO: emergencyInfo/consents (T-02, DP-03/DP-04), companions (T-03, DP-02), events (T-08) are not here.
public final class Customer {
    private final UUID customerId, tenantId;
    private final DocumentNumber documentNumber;
    private final String fullName;
    private final BirthDateOrAge birthDateOrAge;
    private final ContactInfo contact;
    private final boolean termsAccepted;
    private final Instant createdAt;

    private Customer(UUID id, UUID tenant, DocumentNumber document, String name, BirthDateOrAge birth,
            ContactInfo contact, boolean terms, Instant created) {
        customerId = id; tenantId = tenant; documentNumber = document; fullName = name;
        birthDateOrAge = birth; this.contact = contact; termsAccepted = terms; createdAt = created;
    }

    public static Customer registerLead(UUID id, UUID tenant, String document, String fullName,
            BirthDateOrAge birth, ContactInfo contact, boolean termsAccepted, Instant now, boolean highRisk,
            HighRiskData highRiskData) {
        return registerLead(id, tenant, document, fullName, birth, contact, termsAccepted, now);
    }

    public static Customer registerLead(UUID id, UUID tenant, String document, String fullName,
            BirthDateOrAge birth, ContactInfo contact, boolean termsAccepted, Instant now) {
        required(id, "customerId"); required(tenant, "tenantId");
        var number = DocumentNumber.of(document);
        if (fullName == null || fullName.isBlank()) throw new DomainException("fullName", "fullName is required");
        required(birth, "birthDateOrAge");
        if (contact == null || !contact.isPresent()) throw new DomainException("contact", "contact is required");
        if (!termsAccepted) throw new DomainException("termsAccepted", "terms must be accepted");
        required(now, "occurredAt");
        return new Customer(id, tenant, number, fullName.strip(), birth, contact, true, now);
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw new DomainException(field, field + " is required");
        return value;
    }

    public UUID customerId() { return customerId; }
    public UUID tenantId() { return tenantId; }
    public DocumentNumber documentNumber() { return documentNumber; }
    public String fullName() { return fullName; }
    public BirthDateOrAge birthDateOrAge() { return birthDateOrAge; }
    public ContactInfo contact() { return contact; }
    public boolean termsAccepted() { return termsAccepted; }
    public Instant createdAt() { return createdAt; }
    public HighRiskData highRiskData() { return null; }
}
