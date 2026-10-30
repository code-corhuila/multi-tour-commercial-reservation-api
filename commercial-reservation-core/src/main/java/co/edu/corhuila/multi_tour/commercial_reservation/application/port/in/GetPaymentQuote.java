package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuote;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentQuoteQuery;

// Read only: nothing is stored. Another tenant's reservation answers as a missing one.
public interface GetPaymentQuote {

    PaymentQuote quote(PaymentQuoteQuery query);
}
