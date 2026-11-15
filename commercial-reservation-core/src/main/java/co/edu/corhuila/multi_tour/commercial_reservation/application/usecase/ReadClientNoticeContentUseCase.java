package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ClientNoticeContent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.StoredNotice;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ReadClientNoticeContent;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ClientNoticeReader;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerProfileReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.UUID;

// HU-COM-015 plan D-05: the content of one notice for job 4. The notice is looked up by tenant and event id, then the customer through the general read
// (ordinary data only, never the risk data). A customer with no e-mail on record has nothing to send to: the same answer as a missing notice (RN-07).
// Nothing returned is logged (RN-11).
public class ReadClientNoticeContentUseCase implements ReadClientNoticeContent {
    private final ClientNoticeReader notices;
    private final CustomerProfileReader customers;

    public ReadClientNoticeContentUseCase(ClientNoticeReader notices, CustomerProfileReader customers) {
        this.notices = notices;
        this.customers = customers;
    }

    @Override
    public ClientNoticeContent read(UUID tenantId, UUID eventId) {
        StoredNotice notice = notices.find(tenantId, eventId).orElseThrow(() -> new ClientNoticeNotFoundException(eventId));
        Customer customer = customers.findOrdinary(tenantId, notice.customerId()).orElseThrow(() -> new ClientNoticeNotFoundException(eventId));
        String email = customer.contact() == null ? null : customer.contact().email();
        if (email == null || email.isBlank()) throw new ClientNoticeNotFoundException(eventId);
        return new ClientNoticeContent(notice.eventId(), notice.type(), email, customer.fullName(), notice.reservationId(), notice.tourName(),
                notice.departureStartsAt(), notice.amountCents(), notice.pendingBalanceCents(), notice.causeCode());
    }
}
