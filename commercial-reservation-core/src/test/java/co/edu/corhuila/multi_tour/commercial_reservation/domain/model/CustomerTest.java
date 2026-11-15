package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-005 T-01: RN-01 (lead data complete) and RN-10 (no future birth date).
class CustomerTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final BirthDateOrAge BIRTH = BirthDateOrAge.ofBirthDate(LocalDate.of(1990, 5, 17), TODAY);
    private static final ContactInfo CONTACT = new ContactInfo("ana@example.com", "3001234567");

    @Test
    void registersTheLeadCustomerWithCompleteData() {
        var lead = Customer.registerLead(ID, TENANT, "  cc-123ab ", "Ana Perez", BIRTH, CONTACT, true, NOW);

        assertThat(lead.customerId()).isEqualTo(ID);
        assertThat(lead.tenantId()).isEqualTo(TENANT);
        assertThat(lead.documentNumber().value()).isEqualTo("CC-123AB");
        assertThat(lead.fullName()).isEqualTo("Ana Perez");
        assertThat(lead.birthDateOrAge()).isEqualTo(BIRTH);
        assertThat(lead.contact()).isEqualTo(CONTACT);
        assertThat(lead.termsAccepted()).isTrue();
        assertThat(lead.createdAt()).isEqualTo(NOW);
        assertThat(lead.termsAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void aRestoredCustomerKeepsTheMomentItAcceptedTheTerms() {
        var accepted = NOW.minusSeconds(3600);
        var restored = Customer.restore(ID, TENANT, DocumentNumber.of("CC-123AB"), "Ana Perez", BIRTH, CONTACT,
                NOW, accepted, null);

        assertThat(restored.termsAccepted()).isTrue();
        assertThat(restored.termsAcceptedAt()).isEqualTo(accepted);
        assertThat(restored.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aFutureBirthDateIsRefused() {
        assertThatThrownBy(() -> BirthDateOrAge.ofBirthDate(TODAY.plusDays(1), TODAY))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.field()).isEqualTo("birthDate"));
    }

    @Test
    void aBirthDateTodayIsAccepted() {
        assertThat(BirthDateOrAge.ofBirthDate(TODAY, TODAY).birthDate()).isEqualTo(TODAY);
    }

    @Test
    void aBirthDateIsRequired() {
        assertThatThrownBy(() -> BirthDateOrAge.ofBirthDate(null, TODAY))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.field()).isEqualTo("birthDateOrAge"));
    }

    @Test
    void aPositiveAgeStandsInForTheBirthDate() {
        assertThat(BirthDateOrAge.ofAge(34).age()).isEqualTo(34);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void anAgeThatIsNotPositiveIsRefused(int age) {
        assertThatThrownBy(() -> BirthDateOrAge.ofAge(age))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.field()).isEqualTo("age"));
    }

    @ParameterizedTest
    @MethodSource("incompleteLead")
    void aLeadWithoutTheRequiredDataIsRefusedNamingTheField(String field, UUID id, UUID tenant, String document,
            String name, BirthDateOrAge birth, ContactInfo contact, boolean terms) {
        assertThatThrownBy(() -> Customer.registerLead(id, tenant, document, name, birth, contact, terms, NOW))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo(field));
    }

    static Stream<Arguments> incompleteLead() {
        return Stream.of(
                Arguments.of("customerId", null, TENANT, "CC1", "Ana", BIRTH, CONTACT, true),
                Arguments.of("tenantId", ID, null, "CC1", "Ana", BIRTH, CONTACT, true),
                Arguments.of("documentNumber", ID, TENANT, null, "Ana", BIRTH, CONTACT, true),
                Arguments.of("documentNumber", ID, TENANT, "   ", "Ana", BIRTH, CONTACT, true),
                Arguments.of("fullName", ID, TENANT, "CC1", " ", BIRTH, CONTACT, true),
                Arguments.of("birthDateOrAge", ID, TENANT, "CC1", "Ana", null, CONTACT, true),
                Arguments.of("contact", ID, TENANT, "CC1", "Ana", BIRTH, null, true),
                Arguments.of("contact", ID, TENANT, "CC1", "Ana", BIRTH, new ContactInfo(" ", null), true),
                Arguments.of("termsAccepted", ID, TENANT, "CC1", "Ana", BIRTH, CONTACT, false));
    }
}
