package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;

// HU-COM-017 plan D-04: the applications of a credit are appended in the transaction that applies them and never changed.
public interface CreditBalanceApplications {

    void save(CreditApplication application);
}
