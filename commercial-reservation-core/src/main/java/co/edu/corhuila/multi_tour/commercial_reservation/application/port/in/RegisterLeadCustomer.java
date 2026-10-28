package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.time.LocalDate;
import java.util.UUID;

public interface RegisterLeadCustomer {
    Customer register(CustomerActor actor, Command command);

    // serviceId is the activity the lead is registered for; its high-risk mark is read from the catalog,
    // never typed by the caller (plan D-04). highRiskData may be null for an ordinary activity.
    record Command(UUID serviceId, String documentNumber, String fullName, LocalDate birthDate, Integer age,
            String email, String phone, boolean termsAccepted, HighRiskData highRiskData) {
    }
}
