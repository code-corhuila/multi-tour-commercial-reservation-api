package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.List;
import java.util.UUID;

// The author and the time come from the token and the clock; any other field is refused by the strict mapper (AC-09.17).
public record ChangePaymentParametersRequest(String reason, List<Row> parameters) {

    public record Row(String paymentType, UUID serviceId, Integer initialWaitHours, Integer additionalWaitHours, Integer minimumDepositBp,
            Integer balanceDeadlineAdvanceHours, Boolean transferEnabled, Boolean cashEnabled) {
        PaymentParameterValues toValues() {
            return new PaymentParameterValues(type(), serviceId, required("initialWaitHours", initialWaitHours),
                    required("additionalWaitHours", additionalWaitHours), minimumDepositBp, balanceDeadlineAdvanceHours,
                    required("transferEnabled", transferEnabled), required("cashEnabled", cashEnabled));
        }

        private PaymentType type() {
            if (paymentType == null) throw new DomainException("paymentType", "paymentType is required");
            try {
                return PaymentType.valueOf(paymentType);
            } catch (IllegalArgumentException invalid) {
                throw new DomainException("paymentType", "paymentType must be FULL_PAYMENT or DEPOSIT");
            }
        }

        private static <T> T required(String field, T value) {
            if (value == null) throw new DomainException(field, field + " is required");
            return value;
        }
    }

    List<PaymentParameterValues> values() {
        return parameters == null ? null : parameters.stream().map(Row::toValues).toList();
    }
}
