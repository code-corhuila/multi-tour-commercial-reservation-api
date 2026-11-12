package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.cash;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CashIncomeRecorder;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CashUnavailableException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import java.time.Duration;
import java.util.UUID;

// Stub of the red commit: the real adapter follows in the green commit.
public final class CashReportingIncome implements CashIncomeRecorder {

    public CashReportingIncome(String baseUrl, String serviceToken, CashReportingSessionStatus day, Duration timeout) {}

    @Override
    public UUID record(UUID tenantId, UUID reservationId, String intentKey, Cents amount, UUID registeredBy) {
        throw new CashUnavailableException("not implemented");
    }

    @Override
    public void reverse(UUID tenantId, UUID movementId) {
        throw new CashUnavailableException("not implemented");
    }
}
