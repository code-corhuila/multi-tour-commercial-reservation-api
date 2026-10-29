package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-005 T-02, RN-02: the aggregate checks the extra data when the activity is high risk.
class CustomerHighRiskTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2);
    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final BirthDateOrAge BIRTH = BirthDateOrAge.ofBirthDate(LocalDate.of(1990, 5, 17),
            LocalDate.of(2026, 10, 27));
    private static final ContactInfo CONTACT = new ContactInfo("ana@example.com", "3001234567");
    private static final HighRiskData COMPLETE = new HighRiskData("O+", "Luis 3009876543", "Ninguna", true);

    private static Customer register(boolean highRisk, HighRiskData data) {
        return Customer.registerLead(ID, TENANT, "CC-1", "Ana Perez", BIRTH, CONTACT, true, NOW, highRisk, data);
    }

    @Test
    void highRiskActivityRefusesTheLeadWithoutTheExtraData() {
        assertThatThrownBy(() -> register(true, null))
                .isInstanceOfSatisfying(MissingHighRiskDataException.class, e -> assertThat(e.fields())
                        .containsExactlyInAnyOrder("bloodType", "emergencyContact", "mobilityRestrictions",
                                "informedConsentSigned"));
    }

    @Test
    void eachMissingFieldIsNamedAndOnlyThoseMissing() {
        var partial = new HighRiskData("O+", " ", "Ninguna", false);

        assertThatThrownBy(() -> register(true, partial))
                .isInstanceOfSatisfying(MissingHighRiskDataException.class, e -> assertThat(e.fields())
                        .containsExactlyInAnyOrder("emergencyContact", "informedConsentSigned"))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankRestrictionsAnswerIsMissingBecauseNoneMustBeSaidAsNinguna(String blank) {
        var data = new HighRiskData("O+", "Luis 3009876543", blank, true);

        assertThat(data.missingFields()).containsExactly("mobilityRestrictions");
    }

    @Test
    void highRiskActivityAcceptsTheLeadWithCompleteDataAndKeepsIt() {
        var lead = register(true, COMPLETE);

        assertThat(lead.highRiskData()).isEqualTo(COMPLETE);
    }

    @Test
    void ordinaryActivityDoesNotRequireTheExtraData() {
        var lead = register(false, null);

        assertThat(lead.customerId()).isEqualTo(ID);
        assertThat(lead.highRiskData()).isNull();
        assertThat(COMPLETE.missingFields()).isEqualTo(List.of());
    }

    @Test
    void theExtraDataNeverPrintsItsValues() {
        assertThat(COMPLETE.toString()).doesNotContain("O+", "Luis", "3009876543");
    }
}
