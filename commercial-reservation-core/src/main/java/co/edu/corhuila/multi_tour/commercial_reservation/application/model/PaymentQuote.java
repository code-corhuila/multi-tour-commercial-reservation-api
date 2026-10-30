package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.time.Instant;
import java.util.List;

// What the reservation can pay now, computed by the service. The lists are empty when the tenant has no parameter row
// (DP-11); the amount, the minimum and the deadline are null when they do not apply.
public record PaymentQuote(List<PaymentType> enabledTypes, List<PaymentMethod> enabledMethods, PaymentType paymentType,
        Cents amountToPay, Cents minimumDeposit, Cents pendingBalance, Instant paymentDeadline) {
}
