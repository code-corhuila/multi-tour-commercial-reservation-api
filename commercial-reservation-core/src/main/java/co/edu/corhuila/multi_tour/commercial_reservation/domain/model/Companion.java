package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.ArrayList;

// A person travelling with the lead customer; it belongs to the reservation (RN-RES-005, DP-02), not to a reusable party.
// Full name, document and birth date or age are required (RN-CLI-002); the contact is not kept (DP-06). The extra data of a
// high-risk tour is optional here: the tour decides whether it is needed.
public record Companion(String fullName, DocumentNumber documentNumber, BirthDateOrAge birthDateOrAge, HighRiskData highRiskData) {

    public Companion {
        var missing = new ArrayList<String>();
        if (fullName == null || fullName.isBlank()) missing.add("companion.fullName");
        if (documentNumber == null) missing.add("companion.documentNumber");
        if (birthDateOrAge == null) missing.add("companion.birthDateOrAge");
        if (!missing.isEmpty()) throw new MissingReservationDataException(missing);
    }
}
