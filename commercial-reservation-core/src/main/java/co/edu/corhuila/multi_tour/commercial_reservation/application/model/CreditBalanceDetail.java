package co.edu.corhuila.multi_tour.commercial_reservation.application.model;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.util.List;

// HU-COM-017 AC-17.9: a credit balance with the history of its applications and of its refund requests (the decisions of kind credit balance refund request).
public record CreditBalanceDetail(CreditBalanceView view, List<CreditApplication> applications, List<FinancialDecision> refundRequests) {
}
