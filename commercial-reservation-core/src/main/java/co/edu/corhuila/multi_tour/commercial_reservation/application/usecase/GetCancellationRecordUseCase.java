package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.GetCancellationRecord;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CancellationRecords;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import java.util.UUID;

// AC-13.10, AC-13.9: the record whole, for the staff roles; a reservation not cancelled by this route, or of another tenant, is missing.
public class GetCancellationRecordUseCase implements GetCancellationRecord {

    private final CancellationRecords records;

    public GetCancellationRecordUseCase(CancellationRecords records) {
        this.records = records;
    }

    @Override
    public CancellationRecord get(PaymentActor actor, UUID reservationId) {
        CancellationAuthorization.requireRead(actor);
        return records.find(actor.tenantId(), reservationId).orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }
}
