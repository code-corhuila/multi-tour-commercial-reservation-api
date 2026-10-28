package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.time.Instant;
import java.util.UUID;

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
            BirthDateOrAge birth, ContactInfo contact, boolean termsAccepted, Instant now) {
        return new Customer(id, tenant, DocumentNumber.of(document), fullName, birth, contact, termsAccepted, now);
    }

    public UUID customerId() { return customerId; }
    public UUID tenantId() { return tenantId; }
    public DocumentNumber documentNumber() { return documentNumber; }
    public String fullName() { return fullName; }
    public BirthDateOrAge birthDateOrAge() { return birthDateOrAge; }
    public ContactInfo contact() { return contact; }
    public boolean termsAccepted() { return termsAccepted; }
    public Instant createdAt() { return createdAt; }
}
