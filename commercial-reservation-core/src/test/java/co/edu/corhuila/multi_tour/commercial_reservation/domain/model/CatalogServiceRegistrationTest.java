package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogServiceRegistrationTest {

    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");

    static CatalogService tour(boolean highRisk) {
        return CatalogService.register(ID, TENANT, CatalogServiceType.TOUR, "Chicamocha Canyon",
                CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, highRisk), NOW);
    }

    @Test
    void registersItemWithAllItsFields() {
        CatalogService item = tour(false);

        assertThat(item.serviceId()).isEqualTo(ID);
        assertThat(item.tenantId()).isEqualTo(TENANT);
        assertThat(item.serviceType()).isEqualTo(CatalogServiceType.TOUR);
        assertThat(item.commercialName()).isEqualTo("Chicamocha Canyon");
        assertThat(item.catalogStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(item.terms().baseRate().amount()).isEqualTo(35_000_000L);
        assertThat(item.terms().baseOperationalCost().amount()).isEqualTo(21_000_000L);
        assertThat(item.terms().configuredCapacity()).isEqualTo(18);
        assertThat(item.terms().transportRequired()).isTrue();
        assertThat(item.terms().highRisk()).isFalse();
        assertThat(item.createdAt()).isEqualTo(NOW);
        assertThat(item.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsItemWithoutCommercialValue() {
        assertThatThrownBy(() -> CommercialTerms.of(null, 21_000_000L, 18, true, false))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("baseRateCents");
    }

    @Test
    void rejectsItemWithoutOperationalCost() {
        assertThatThrownBy(() -> CommercialTerms.of(35_000_000L, null, 18, true, false))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("baseOperationalCostCents");
    }

    @Test
    void rejectsANegativeAmount() {
        assertThatThrownBy(() -> Cents.of("baseRateCents", -1L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("baseRateCents");
        assertThatThrownBy(() -> new Cents(-1)).isInstanceOf(DomainException.class);
        assertThat(Cents.of("baseRateCents", 0L).amount()).isZero();
    }

    @Test
    void rejectsATourWithoutCapacity() {
        CommercialTerms noCapacity = CommercialTerms.of(35_000_000L, 21_000_000L, null, true, false);

        assertThatThrownBy(() -> CatalogService.register(ID, TENANT, CatalogServiceType.TOUR, "Tour", noCapacity, NOW))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("configuredCapacity");
        assertThatThrownBy(() -> CommercialTerms.of(35_000_000L, 21_000_000L, 0, true, false))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).field()).isEqualTo("configuredCapacity");
    }

    @Test
    void aTransportationItemKeepsItsBaseRateAsThePerPersonRate() {
        CatalogService transport = CatalogService.register(ID, TENANT, CatalogServiceType.TRANSPORTATION,
                "Bus to the canyon", CommercialTerms.of(12_000L, 8_000L, null, false, false), NOW);

        assertThat(transport.terms().baseRate().amount()).isEqualTo(12_000L);
        assertThat(transport.terms().configuredCapacity()).isNull();
        // RN-TRA-002 lives in baseRate (plan DD-20): a second rate field would be a second source of truth.
        assertThat(declaredFieldNames(CatalogService.class, CommercialTerms.class))
                .filteredOn(name -> name.toLowerCase().contains("rate")).containsExactly("baseRate");
    }

    @Test
    void aTransportationItemStoresTransportRequiredAsFalse() {
        CatalogService transport = CatalogService.register(ID, TENANT, CatalogServiceType.TRANSPORTATION,
                "Bus to the canyon", CommercialTerms.of(12_000L, 8_000L, null, true, false), NOW);

        assertThat(transport.terms().transportRequired()).isFalse();
    }

    @Test
    void moneyIsAnIntegerInMinorUnits() {
        assertThat(Cents.class.getRecordComponents()[0].getType()).isEqualTo(long.class);
        for (Class<?> type : new Class<?>[] {CatalogService.class, CommercialTerms.class, Cents.class}) {
            for (Field field : type.getDeclaredFields()) {
                assertThat(field.getType()).isNotIn(double.class, float.class, Double.class, Float.class,
                        BigDecimal.class);
            }
        }
    }

    private static List<String> declaredFieldNames(Class<?>... types) {
        return Arrays.stream(types).flatMap(t -> Arrays.stream(t.getDeclaredFields())).map(Field::getName).toList();
    }
}
