package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

// Lead customer (RN-01). Only the 06-data fields: document, name, birth date or age, contact, terms.
// Optional high-risk data (RN-02). TODO: companions (T-03, DP-02), events (T-08) are not here.
public final class Customer {
    private final UUID customerId, tenantId;
    private final DocumentNumber documentNumber;
    private final String fullName;
    private final BirthDateOrAge birthDateOrAge;
    private final ContactInfo contact;
    private final boolean termsAccepted;
    private final Instant createdAt, termsAcceptedAt;
    private final HighRiskData highRiskData;
    // Known only at registration (read from the catalog); a restored customer keeps no service, so it is null.
    private final Boolean highRisk;

    private Customer(UUID id, UUID tenant, DocumentNumber document, String name, BirthDateOrAge birth,
            ContactInfo contact, boolean terms, Instant created, Instant termsAt, HighRiskData highRiskData, Boolean highRisk) {
        customerId = id; tenantId = tenant; documentNumber = document; fullName = name;
        birthDateOrAge = birth; this.contact = contact; termsAccepted = terms; createdAt = created; termsAcceptedAt = termsAt;
        this.highRiskData = highRiskData; this.highRisk = highRisk;
    }

    public static Customer registerLead(UUID id, UUID tenant, String document, String fullName,
            BirthDateOrAge birth, ContactInfo contact, boolean termsAccepted, Instant now) {
        return registerLead(id, tenant, document, fullName, birth, contact, termsAccepted, now, false, null);
    }

    // RN-02: when the activity is high risk the extra data must be complete; each missing field is named.
    public static Customer registerLead(UUID id, UUID tenant, String document, String fullName,
            BirthDateOrAge birth, ContactInfo contact, boolean termsAccepted, Instant now, boolean highRisk,
            HighRiskData highRiskData) {
        required(id, "customerId"); required(tenant, "tenantId");
        var number = DocumentNumber.of(document);
        if (fullName == null || fullName.isBlank()) throw new DomainException("fullName", "fullName is required");
        required(birth, "birthDateOrAge");
        if (contact == null || !contact.isPresent()) throw new DomainException("contact", "contact is required");
        if (!termsAccepted) throw new DomainException("termsAccepted", "terms must be accepted");
        required(now, "occurredAt");
        if (highRisk) {
            var missing = highRiskData == null
                    ? new HighRiskData(null, null, null, false).missingFields() : highRiskData.missingFields();
            if (!missing.isEmpty()) throw new MissingHighRiskDataException(missing);
        }
        return new Customer(id, tenant, number, fullName.strip(), birth, contact, true, now, now, highRiskData, highRisk);
    }

    // Rebuilds a stored customer: its rules were checked when it was registered, so none is repeated here. The
    // terms are always accepted (the engine refuses a customer that did not); the moment is the stored one.
    public static Customer restore(UUID id, UUID tenant, DocumentNumber document, String fullName,
            BirthDateOrAge birth, ContactInfo contact, Instant createdAt, Instant termsAcceptedAt, HighRiskData highRiskData) {
        return new Customer(id, tenant, document, fullName, birth, contact, true, createdAt, termsAcceptedAt,
                highRiskData, null);
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
    public Instant termsAcceptedAt() { return termsAcceptedAt; }
    public HighRiskData highRiskData() { return highRiskData; }
    public Boolean highRisk() { return highRisk; }
}
