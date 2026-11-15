package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ClientNoticeContent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.NoticeType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-COM-015 api T-05, RN-05, RN-07, RN-10: job 4 reads the recipient and the variables of one notice at send time, from the tenant of the path only.
// Every value is invented.
class ReadClientNoticeContentTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTHER_TENANT = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID RESERVATION = UUID.randomUUID();
    private static final UUID EVENT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");
    private final Map<UUID, StoredNotice> notices = new HashMap<>();
    private final Map<UUID, Customer> customers = new HashMap<>();
    private final ClientNoticeReader reader = (tenant, id) -> Optional.ofNullable(notices.get(id)).filter(found -> found.tenantId().equals(tenant));
    private final CustomerProfileReader profiles = (tenant, id) -> Optional.ofNullable(customers.get(id)).filter(found -> found.tenantId().equals(tenant));
    private final ReadClientNoticeContentUseCase read = new ReadClientNoticeContentUseCase(reader, profiles);

    private static StoredNotice notice(NoticeType type) {
        return new StoredNotice(EVENT, TENANT, RESERVATION, CUSTOMER, type, "Caminata Ficticia", Instant.parse("2027-03-01T13:00:00Z"), 100_000L,
                224_000L, null);
    }

    private static Customer customer(ContactInfo contact) {
        return Customer.registerLead(CUSTOMER, TENANT, "CC1001", "Lead Person", BirthDateOrAge.ofAge(30), contact, true, NOW);
    }

    @Test
    void jobFourReadsTheRecipientAndTheVariablesOfTheNotice() {
        notices.put(EVENT, notice(NoticeType.PROOF_APPROVED));
        customers.put(CUSTOMER, customer(new ContactInfo("lead@example.com", "3000000000")));

        ClientNoticeContent content = read.read(TENANT, EVENT);

        assertThat(content.eventId()).isEqualTo(EVENT);
        assertThat(content.notificationType()).isEqualTo(NoticeType.PROOF_APPROVED);
        assertThat(content.recipientEmail()).isEqualTo("lead@example.com");
        assertThat(content.recipientName()).isEqualTo("Lead Person");
        assertThat(content.reservationId()).isEqualTo(RESERVATION);
        assertThat(content.tourName()).isEqualTo("Caminata Ficticia");
        assertThat(content.amountCents()).isEqualTo(100_000L);
        assertThat(content.pendingBalanceCents()).isEqualTo(224_000L);
    }

    @Test
    void aNoticeWithAnotherTenantInThePathAnswersNotFound() {
        notices.put(EVENT, notice(NoticeType.PROOF_APPROVED));
        customers.put(CUSTOMER, customer(new ContactInfo("lead@example.com", null)));

        assertThatThrownBy(() -> read.read(OTHER_TENANT, EVENT)).isInstanceOf(ClientNoticeNotFoundException.class);
        assertThatThrownBy(() -> read.read(TENANT, UUID.randomUUID())).isInstanceOf(ClientNoticeNotFoundException.class);
    }

    @Test
    void aCustomerWithoutEmailHasNoContentToSend() {
        notices.put(EVENT, notice(NoticeType.RESERVATION_CONFIRMED));
        customers.put(CUSTOMER, customer(new ContactInfo(null, "3000000000")));

        assertThatThrownBy(() -> read.read(TENANT, EVENT)).isInstanceOf(ClientNoticeNotFoundException.class);
    }
}
