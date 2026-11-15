package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AccountLinkFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AccountLinkFakes.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountLinkOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountEmailVerified;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// api spec 008 plan D-04 and spec 005 DP-05 at the core: AccountEmailVerified links the account to the record of the tenant with
// the same normalized e-mail and no account (ADR-004 D5, PDR E24 and L9, AC-05.7, AC-08.14), once per event id.
class LinkAccountOnEmailVerifiedTest {

    private static final UUID ACCOUNT = new UUID(0, 0x41);
    private static final Instant AT = Instant.parse("2026-10-30T10:00:00Z");

    private final AccountLinkFakes store = new AccountLinkFakes();
    private final LinkAccountOnEmailVerifiedUseCase link = new LinkAccountOnEmailVerifiedUseCase(store, store, store, store, store);

    private static AccountEmailVerified verified(UUID tenant, UUID account, String email) {
        return new AccountEmailVerified(UUID.randomUUID(), tenant, account, email, AT);
    }

    @Test
    void aNormalizedEmailOfAVerifiedAccountLinksTheCustomer() {
        UUID customer = store.addCustomer(TENANT, "ana@correo.co", null);

        AccountLinkOutcome outcome = link.link(verified(TENANT, ACCOUNT, "ana@correo.co"));

        assertThat(outcome).isEqualTo(AccountLinkOutcome.LINKED);
        assertThat(store.accountOf(customer)).isEqualTo(ACCOUNT);
        assertThat(store.count()).isEqualTo(1); // no second record
    }

    @Test
    void accountEmailVerifiedDeliveredTwiceLinksTheAccountOnce() {
        UUID customer = store.addCustomer(TENANT, "ana@correo.co", null);
        AccountEmailVerified event = verified(TENANT, ACCOUNT, "ana@correo.co");

        assertThat(link.link(event)).isEqualTo(AccountLinkOutcome.LINKED);
        assertThat(link.link(event)).isEqualTo(AccountLinkOutcome.ALREADY_PROCESSED);

        assertThat(store.accountOf(customer)).isEqualTo(ACCOUNT);
        assertThat(store.processed).containsExactly(event.eventId());
    }

    @Test
    void theEventSpellingOfTheEmailIsNormalizedLikeTheRecord() {
        UUID customer = store.addCustomer(TENANT, "ana@correo.co", null);

        assertThat(link.link(verified(TENANT, ACCOUNT, "  Ana@Correo.CO "))).isEqualTo(AccountLinkOutcome.LINKED);
        assertThat(store.accountOf(customer)).isEqualTo(ACCOUNT);
    }

    @Test
    void aRecordOfAnotherTenantIsNeverLinked() {
        UUID foreign = store.addCustomer(OTHER_TENANT, "ana@correo.co", null);

        assertThat(link.link(verified(TENANT, ACCOUNT, "ana@correo.co"))).isEqualTo(AccountLinkOutcome.NOTHING_TO_LINK);
        assertThat(store.accountOf(foreign)).isNull();
    }

    @Test
    void aRecordAlreadyLinkedToAnotherAccountIsLeftAlone() {
        UUID other = new UUID(0, 0x42);
        UUID customer = store.addCustomer(TENANT, "ana@correo.co", other);

        assertThat(link.link(verified(TENANT, ACCOUNT, "ana@correo.co"))).isEqualTo(AccountLinkOutcome.NOTHING_TO_LINK);
        assertThat(store.accountOf(customer)).isEqualTo(other);
    }

    @Test
    void anAccountThatAlreadyHasARecordLinksNothingElse() {
        store.addCustomer(TENANT, "first@correo.co", ACCOUNT);
        UUID second = store.addCustomer(TENANT, "ana@correo.co", null);

        assertThat(link.link(verified(TENANT, ACCOUNT, "ana@correo.co"))).isEqualTo(AccountLinkOutcome.NOTHING_TO_LINK);
        assertThat(store.accountOf(second)).isNull(); // one customer per account
    }

    @Test
    void withNoRecordTheLinkWaitsForTheFirstReservationButTheIdIsRecorded() {
        AccountEmailVerified event = verified(TENANT, ACCOUNT, "nobody@correo.co");

        assertThat(link.link(event)).isEqualTo(AccountLinkOutcome.NOTHING_TO_LINK);

        assertThat(store.count()).isZero(); // never creates a record
        assertThat(store.processed).containsExactly(event.eventId());
    }

    @Test
    void aFailureAfterTheLinkUndoesTheLinkAndTheEventId() {
        UUID customer = store.addCustomer(TENANT, "ana@correo.co", null);
        store.failAfterRecord = new IllegalStateException("db down");

        assertThatThrownBy(() -> link.link(verified(TENANT, ACCOUNT, "ana@correo.co"))).isInstanceOf(IllegalStateException.class);

        assertThat(store.accountOf(customer)).isNull();
        assertThat(store.processed).isEmpty();
    }

    @Test
    void anEventWithoutTenantAccountOrEmailIsRefused() {
        assertThatThrownBy(() -> verified(null, ACCOUNT, "ana@correo.co")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verified(TENANT, null, "ana@correo.co")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> verified(TENANT, ACCOUNT, " ")).isInstanceOf(DomainException.class);
    }
}
