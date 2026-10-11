package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AssociatedEstablishmentTest {
    private static final UUID ID = new UUID(0, 1), TENANT = new UUID(0, 2), ACTOR = new UUID(0, 3);
    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @ParameterizedTest @EnumSource(EstablishmentType.class)
    void registersAndPublishesAnEstablishmentWithAnOpaqueImageReference(EstablishmentType type) {
        var item = AssociatedEstablishment.register(ID, TENANT, type, "Sample establishment",
                "Contact the establishment directly", "opaque-image-reference", ACTOR, NOW);
        assertThat(item.associationId()).isEqualTo(ID);
        assertThat(item.tenantId()).isEqualTo(TENANT);
        assertThat(item.establishmentType()).isEqualTo(type);
        assertThat(item.commercialName()).isEqualTo("Sample establishment");
        assertThat(item.commercialInformation()).isEqualTo("Contact the establishment directly");
        assertThat(item.imageReference()).isEqualTo("opaque-image-reference");
        assertThat(item.associationStatus()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(item.updatedAt()).isEqualTo(NOW);
        assertThat(item.pullEvents()).containsExactly(new EstablishmentAssociationPublished(TENANT, ID, ACTOR, NOW));
        assertThat(item.pullEvents()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"HOSTEL", "", "hotel"})
    void rejectsATypeOutsideHotelAndRestaurant(String type) {
        assertThatThrownBy(() -> EstablishmentType.parse(type)).isInstanceOfSatisfying(DomainException.class,
                error -> assertThat(error.field()).isEqualTo("establishmentType"));
    }

    @Test void theTypeAndStatusSetsAreClosed() {
        assertThat(EstablishmentType.values()).containsExactly(EstablishmentType.HOTEL, EstablishmentType.RESTAURANT);
        assertThat(AssociationStatus.values()).containsExactly(AssociationStatus.ACTIVE, AssociationStatus.INACTIVE);
    }

    @ParameterizedTest @MethodSource("invalidRegistration")
    void registrationNamesEveryMissingOrInvalidField(String field, Object invalid) {
        assertThatThrownBy(() -> AssociatedEstablishment.register(
                field.equals("associationId") ? (UUID) invalid : ID,
                field.equals("tenantId") ? (UUID) invalid : TENANT,
                field.equals("establishmentType") ? (EstablishmentType) invalid : EstablishmentType.HOTEL,
                field.equals("commercialName") ? (String) invalid : "Sample establishment",
                field.equals("commercialInformation") ? (String) invalid : "Contact details",
                field.equals("imageReference") ? (String) invalid : "opaque-reference",
                field.equals("actorId") ? (UUID) invalid : ACTOR,
                field.equals("occurredAt") ? (Instant) invalid : NOW))
                .isInstanceOfSatisfying(DomainException.class, error -> assertThat(error.field()).isEqualTo(field));
    }

    static Stream<Arguments> invalidRegistration() {
        return Stream.of(Arguments.of("associationId", null), Arguments.of("tenantId", null),
                Arguments.of("establishmentType", null), Arguments.of("commercialName", null),
                Arguments.of("commercialName", " "), Arguments.of("commercialName", "x".repeat(256)),
                Arguments.of("commercialInformation", null), Arguments.of("commercialInformation", " "),
                Arguments.of("imageReference", null), Arguments.of("imageReference", " "),
                Arguments.of("actorId", null), Arguments.of("occurredAt", null));
    }

    @Test void commercialNameAcceptsBothLengthBoundaries() {
        for (String name : new String[]{"x", "x".repeat(255)}) {
            assertThat(AssociatedEstablishment.register(ID, TENANT, EstablishmentType.HOTEL, name,
                    "Contact details", "opaque-reference", ACTOR, NOW).commercialName()).isEqualTo(name);
        }
    }

    @Test void repeatedTransitionsChangeNeitherTimeNorEvents() {
        var item = registered();
        item.pullEvents();
        item.inactivate(ACTOR, NOW.plusSeconds(1));
        assertThat(item.pullEvents()).containsExactly(
                new EstablishmentAssociationInactivated(TENANT, ID, ACTOR, NOW.plusSeconds(1)));
        item.inactivate(new UUID(0, 4), NOW.plusSeconds(2));
        assertThat(item.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(item.pullEvents()).isEmpty();
        item.reactivate(ACTOR, NOW.plusSeconds(3));
        assertThat(item.pullEvents()).containsExactly(
                new EstablishmentAssociationReactivated(TENANT, ID, ACTOR, NOW.plusSeconds(3)));
        item.reactivate(new UUID(0, 4), NOW.plusSeconds(4));
        assertThat(item.updatedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(item.pullEvents()).isEmpty();
        assertThat(item.createdAt()).isEqualTo(NOW);
    }

    @Test void invalidTransitionMetadataLeavesTheStateAndEventsUntouched() {
        var item = registered(); item.pullEvents();
        assertThatThrownBy(() -> item.inactivate(null, NOW)).isInstanceOf(DomainException.class);
        assertThat(item.associationStatus()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(item.pullEvents()).isEmpty();
        item.inactivate(ACTOR, NOW); item.pullEvents();
        assertThatThrownBy(() -> item.reactivate(ACTOR, null)).isInstanceOf(DomainException.class);
        assertThat(item.associationStatus()).isEqualTo(AssociationStatus.INACTIVE);
        assertThat(item.pullEvents()).isEmpty();
    }

    private AssociatedEstablishment registered() {
        return AssociatedEstablishment.register(ID, TENANT, EstablishmentType.HOTEL,
                "Sample establishment", "Contact details", "opaque-reference", ACTOR, NOW);
    }
}
