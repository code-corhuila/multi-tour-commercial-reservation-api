package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

// RN-CLI-002 / AC-05.4 as the reservation needs it: full name, document and birth date or age; the contact is not kept (DP-06).
class CompanionTest {

    @Test
    void aCompanionKeepsItsNormalizedDocument() {
        Companion companion = new Companion("Ana Gomez", DocumentNumber.of(" cc 1 "), BirthDateOrAge.ofAge(25), null);

        assertThat(companion.documentNumber()).isEqualTo(DocumentNumber.of("CC 1"));
        assertThat(companion.highRiskData()).isNull();
    }

    @Test
    void everyMissingDatumIsNamedAtOnce() {
        assertThatThrownBy(() -> new Companion(" ", null, null, null))
                .isInstanceOfSatisfying(MissingReservationDataException.class, e -> assertThat(e.fields())
                        .containsExactly("companion.fullName", "companion.documentNumber", "companion.birthDateOrAge"));
    }
}
