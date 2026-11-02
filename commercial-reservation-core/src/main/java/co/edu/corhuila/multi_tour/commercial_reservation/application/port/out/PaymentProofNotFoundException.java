package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import java.util.UUID;

// Also the answer for a proof of another tenant (AC-10.17): it looks missing, never forbidden.
public class PaymentProofNotFoundException extends RuntimeException {
    public PaymentProofNotFoundException(UUID attemptId) { super("payment proof " + attemptId + " not found"); }
}
