package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AssociatedEstablishment;

public record EstablishmentRegistration(AssociatedEstablishment association, boolean created) {}
