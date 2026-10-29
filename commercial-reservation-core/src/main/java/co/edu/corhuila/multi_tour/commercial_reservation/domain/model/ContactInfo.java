package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// Email and phone are the two channels 06-data names; its "related information" is NO DEFINIDO.
// TODO(DP-06 / 06-data): rule for "contact present" is assumed as at least one channel.
public record ContactInfo(String email, String phone) {
    public boolean isPresent() {
        return !isBlank(email) || !isBlank(phone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
