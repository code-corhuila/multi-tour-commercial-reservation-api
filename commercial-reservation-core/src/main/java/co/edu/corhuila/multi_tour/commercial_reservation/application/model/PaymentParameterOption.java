package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import java.util.UUID;

// What the Final Customer may know of a payment parameter row before the reservation exists (PDR 1.9, AC-RS.4): the type, the service
// when the row has one and the two enabled methods. The author, the reason, the change time, the hours and the minimum stay hidden.
public record PaymentParameterOption(PaymentType paymentType, UUID serviceId, boolean transferEnabled, boolean cashEnabled) {}
