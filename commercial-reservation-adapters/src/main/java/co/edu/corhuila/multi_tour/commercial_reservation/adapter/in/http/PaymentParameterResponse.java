package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// A parameter row with its last change only (DP-04, DD-5). Absent values are not written: a full payment has no minimum.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentParameterResponse(String paymentType, UUID serviceId, int initialWaitHours, int additionalWaitHours,
        Integer minimumDepositBp, Integer balanceDeadlineAdvanceHours, boolean transferEnabled, boolean cashEnabled, UUID changedBy,
        String changeReason, String changedAt) {

    static PaymentParameterResponse of(PaymentParameter row) {
        var values = row.values();
        return new PaymentParameterResponse(values.paymentType().name(), values.serviceId(), values.initialWaitHours(),
                values.additionalWaitHours(), values.minimumDepositBp(), values.balanceDeadlineAdvanceHours(), values.transferEnabled(),
                values.cashEnabled(), row.changedBy(), row.changeReason(), row.changedAt().toString());
    }
}
