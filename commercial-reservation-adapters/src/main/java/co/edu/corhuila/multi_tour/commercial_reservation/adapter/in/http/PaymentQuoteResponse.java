package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuote;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// What the reservation can pay now. The lists are empty when the tenant has no parameter row (DP-11); the amount, the minimum and the
// deadline are absent when they do not apply. No bank data is served (DP-06).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentQuoteResponse(List<String> enabledTypes, List<String> enabledMethods, String paymentType, Long amountToPayCents,
        Long minimumDepositCents, long pendingBalanceCents, String paymentDeadline) {

    static PaymentQuoteResponse of(PaymentQuote quote) {
        return new PaymentQuoteResponse(quote.enabledTypes().stream().map(Enum::name).toList(),
                quote.enabledMethods().stream().map(Enum::name).toList(), quote.paymentType().name(),
                quote.amountToPay() == null ? null : quote.amountToPay().amount(),
                quote.minimumDeposit() == null ? null : quote.minimumDeposit().amount(), quote.pendingBalance().amount(),
                quote.paymentDeadline() == null ? null : quote.paymentDeadline().toString());
    }
}
