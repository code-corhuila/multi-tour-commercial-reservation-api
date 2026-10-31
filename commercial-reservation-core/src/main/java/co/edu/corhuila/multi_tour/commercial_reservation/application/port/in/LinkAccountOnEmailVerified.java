package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountLinkOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountEmailVerified;

// Links the account to the customer record of the tenant with the same normalized e-mail and no account, once per event id
// (api spec 008 plan D-04; spec 005 DP-05; AC-05.7, AC-08.14).
public interface LinkAccountOnEmailVerified {
    AccountLinkOutcome link(AccountEmailVerified event);
}
