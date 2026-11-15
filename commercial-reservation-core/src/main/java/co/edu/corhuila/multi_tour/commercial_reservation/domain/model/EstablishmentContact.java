package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.regex.Pattern;

// How a customer reaches the establishment (HU-COM-002, AC-02.12). Every field may be absent: a row that
// existed before the contact was stored has none. A registration needs at least an email or a phone.
public record EstablishmentContact(String email, String phone, String address) {
    public static final EstablishmentContact NONE = new EstablishmentContact(null, null, null);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // A blank field is an absent one; the rest is trimmed.
    public static EstablishmentContact of(String email, String phone, String address) {
        return new EstablishmentContact(blankToNull(email), blankToNull(phone), blankToNull(address));
    }

    void requireForRegistration() {
        if (email == null && phone == null)
            throw new DomainException("contactEmail", "contactEmail or contactPhone is required");
        bound(email, 254, "contactEmail");
        bound(phone, 30, "contactPhone");
        bound(address, 255, "contactAddress");
        if (email != null && !EMAIL.matcher(email).matches())
            throw new DomainException("contactEmail", "contactEmail must be an email address");
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static void bound(String value, int max, String field) {
        if (value != null && value.length() > max)
            throw new DomainException(field, field + " must have at most " + max + " characters");
    }
}
