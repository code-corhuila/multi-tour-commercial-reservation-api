package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import java.util.UUID;

// HU-COM-017 DP-05: a credit balance with the customer it belongs to, which is the customer of its source reservation.
public record CreditBalanceView(CreditBalance credit, UUID customerId) {
}
