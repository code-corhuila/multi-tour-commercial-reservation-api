package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentParameterOption;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

// What the Final Customer reads of a payment parameter row (PDR 1.9, AC-RS.4): no author, reason, time, hours or minimum deposit.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentOptionResponse(String paymentType, UUID serviceId, boolean transferEnabled, boolean cashEnabled) {

    static PaymentOptionResponse of(PaymentParameterOption option) {
        return new PaymentOptionResponse(option.paymentType().name(), option.serviceId(), option.transferEnabled(), option.cashEnabled());
    }
}
