package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Email and phone are the two channels 06-data names; its "related information" is NO DEFINIDO.
// TODO(DP-06 / 06-data): rule for "contact present" is assumed as at least one channel.
public record ContactInfo(String email, String phone) {
    // The e-mail is stored trimmed and lower-cased (contract), so two spellings are one address.
    public ContactInfo {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isPresent() {
        return !isBlank(email) || !isBlank(phone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
