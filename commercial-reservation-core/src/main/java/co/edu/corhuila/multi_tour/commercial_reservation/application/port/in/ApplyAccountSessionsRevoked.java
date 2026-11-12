package co.edu.corhuila.multi_tour.commercial_reservation.application.port.in;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.AccountSessionOutcome;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AccountSessionsRevoked;

// Keeps the cutoff of an account, once per event id and never moving it back (api spec 022; ADR-004 D5).
public interface ApplyAccountSessionsRevoked {
    AccountSessionOutcome apply(AccountSessionsRevoked event);
}
