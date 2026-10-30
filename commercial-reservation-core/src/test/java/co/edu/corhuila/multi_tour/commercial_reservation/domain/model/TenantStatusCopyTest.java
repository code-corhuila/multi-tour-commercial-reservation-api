package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// api spec 021 T-01 (RN-TSC-03, -04, -05): the copy of a tenant moves only forward and each event sets its own part.
class TenantStatusCopyTest {

    private static final UUID TENANT = new UUID(1, 1);
    private static final Instant T0 = Instant.parse("2026-10-29T14:00:00Z");

    private static TenantEvent event(TenantEventType type, Instant at, String identifier, TenantStatus status,
            String timeZone, String country) {
        return new TenantEvent(UUID.randomUUID(), type, TENANT, at, identifier, status, timeZone, country);
    }

    private static TenantStatusCopy created() {
        return TenantStatusCopy.from(event(TenantEventType.TENANT_CREATED, T0, "agencia-ficticia", TenantStatus.ACTIVE,
                "America/Bogota", "CO"));
    }

    @Test
    void aTenantCopyNeverMovesBack() {
        TenantStatusCopy copy = created();

        assertThat(copy.apply(event(TenantEventType.TENANT_INACTIVATED, T0.minusSeconds(1), null, null, null, null))).isEmpty();
        assertThat(copy.apply(event(TenantEventType.TENANT_INACTIVATED, T0, null, null, null, null))).isEmpty();
        assertThat(copy.apply(event(TenantEventType.TENANT_INACTIVATED, T0.plusSeconds(1), null, null, null, null)))
                .hasValueSatisfying(next -> assertThat(next.lastEventAt()).isEqualTo(T0.plusSeconds(1)));
    }

    @Test
    void eachEventTypeSetsItsPartOfTheCopy() {
        TenantStatusCopy copy = created();

        TenantStatusCopy inactive = copy.apply(event(TenantEventType.TENANT_INACTIVATED, T0.plusSeconds(1),
                null, TenantStatus.ACTIVE, null, null)).orElseThrow();
        assertThat(inactive.status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(inactive.timeZone()).isEqualTo("America/Bogota");

        TenantStatusCopy active = inactive.apply(event(TenantEventType.TENANT_REACTIVATED, T0.plusSeconds(2),
                null, TenantStatus.INACTIVE, null, null)).orElseThrow();
        assertThat(active.status()).isEqualTo(TenantStatus.ACTIVE);

        TenantStatusCopy moved = active.apply(event(TenantEventType.TENANT_LOCALIZATION_CHANGED, T0.plusSeconds(3),
                null, null, "America/Mexico_City", "MX")).orElseThrow();
        assertThat(moved.timeZone()).isEqualTo("America/Mexico_City");
        assertThat(moved.country()).isEqualTo("MX");
        assertThat(moved.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void aCompletePayloadCreatesTheCopyOfAnyType() {
        for (TenantEventType type : TenantEventType.values()) {
            TenantStatusCopy copy = TenantStatusCopy.from(event(type, T0, "agencia-ficticia", TenantStatus.ACTIVE,
                    "America/Bogota", "CO"));
            assertThat(copy.tenantId()).isEqualTo(TENANT);
            assertThat(copy.identifier()).isEqualTo("agencia-ficticia");
            assertThat(copy.lastEventAt()).isEqualTo(T0);
        }
        assertThat(TenantStatusCopy.from(event(TenantEventType.TENANT_INACTIVATED, T0, "agencia-ficticia", null,
                "America/Bogota", "CO")).status()).isEqualTo(TenantStatus.INACTIVE);
        assertThatThrownBy(() -> TenantStatusCopy.from(event(TenantEventType.TENANT_REACTIVATED, T0, null, null, null, null)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantStatusCopy.from(event(TenantEventType.TENANT_CREATED, T0, "Not Kebab",
                TenantStatus.ACTIVE, "America/Bogota", "CO"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantStatusCopy.from(event(TenantEventType.TENANT_CREATED, T0, "agencia-ficticia",
                TenantStatus.ACTIVE, "Mars/Olympus", "CO"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> TenantStatusCopy.from(event(TenantEventType.TENANT_CREATED, T0, "agencia-ficticia",
                TenantStatus.ACTIVE, "America/Bogota", "col"))).isInstanceOf(DomainException.class);
    }

    @Test
    void theIdentifierNeverChanges() {
        TenantStatusCopy next = created().apply(event(TenantEventType.TENANT_CREATED, T0.plusSeconds(1), "otra-agencia",
                TenantStatus.ACTIVE, "America/Lima", "PE")).orElseThrow();

        assertThat(next.identifier()).isEqualTo("agencia-ficticia");
        assertThat(next.timeZone()).isEqualTo("America/Lima");
    }
}
