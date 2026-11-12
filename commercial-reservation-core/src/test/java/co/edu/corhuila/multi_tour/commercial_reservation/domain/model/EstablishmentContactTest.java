package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// HU-COM-002 AC-02.12: a registration needs an email or a phone; the address is optional.
class EstablishmentContactTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Test void anEmailAloneOrAPhoneAloneIsEnough() {
        assertThat(register(EstablishmentContact.of("info@hotel.example", null, null)).contact().email())
                .isEqualTo("info@hotel.example");
        assertThat(register(EstablishmentContact.of(null, "+57 8 8700000", "Calle 1")).contact().phone())
                .isEqualTo("+57 8 8700000");
    }

    @Test void aBlankFieldIsAbsentAndTheValuesAreTrimmed() {
        var contact = EstablishmentContact.of("  info@hotel.example ", "   ", "");
        assertThat(contact).isEqualTo(new EstablishmentContact("info@hotel.example", null, null));
    }

    @Test void neitherEmailNorPhoneIsRefusedNamingTheField() {
        assertThatThrownBy(() -> register(EstablishmentContact.of(null, " ", "Calle 1")))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo("contactEmail"));
        assertThatThrownBy(() -> register(EstablishmentContact.NONE)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest @CsvSource({"contactEmail,254", "contactPhone,30", "contactAddress,255"})
    void aTooLongFieldIsRefusedNamingIt(String field, int max) {
        var long_ = "x".repeat(max + 1);
        var contact = switch (field) {
            case "contactEmail" -> EstablishmentContact.of(long_.substring(0, max - 12) + "@hotel.example", null, null);
            case "contactPhone" -> EstablishmentContact.of(null, long_, null);
            default -> EstablishmentContact.of("info@hotel.example", null, long_);
        };
        assertThatThrownBy(() -> register(contact)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo(field));
    }

    @Test void anEmailWithoutAnAtSignIsRefused() {
        assertThatThrownBy(() -> register(EstablishmentContact.of("not-an-email", null, null)))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo("contactEmail"));
    }

    @Test void theContactSurvivesRestoringTheAssociation() {
        var contact = EstablishmentContact.of("info@hotel.example", "+57 8 8700000", "Calle 1");
        var item = register(contact);
        var restored = AssociatedEstablishment.restore(ID, TENANT, EstablishmentType.HOTEL, "Hotel", "Info", "image",
                AssociationStatus.ACTIVE, NOW, NOW, item.contact());
        assertThat(restored.contact()).isEqualTo(contact);
        assertThat(AssociatedEstablishment.restore(ID, TENANT, EstablishmentType.HOTEL, "Hotel", "Info", "image",
                AssociationStatus.ACTIVE, NOW, NOW).contact()).isEqualTo(EstablishmentContact.NONE);
    }

    private AssociatedEstablishment register(EstablishmentContact contact) {
        return AssociatedEstablishment.register(ID, TENANT, EstablishmentType.HOTEL, "Hotel", "Info", "image",
                contact, ACTOR, NOW);
    }
}
