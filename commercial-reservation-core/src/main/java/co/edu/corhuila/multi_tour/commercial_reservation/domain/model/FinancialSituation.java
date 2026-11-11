package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

// RN-02: the situation is a pure function of validated values; nothing else moves it. The word "Pagado" never appears.
public final class FinancialSituation {

    private FinancialSituation() {
    }

    // Confirmation: the whole final value for a full payment, the minimum deposit for a deposit (RN-RES-004).
    public static boolean meetsConfirmation(Cents finalValue, Cents validated, PaymentType type, Cents minimumDeposit) {
        return validated.amount() >= requiredToConfirm(finalValue, type, minimumDeposit).amount();
    }

    public static PaymentStatus of(Cents finalValue, Cents validated, PaymentType type, Cents minimumDeposit, ReservationStatus status) {
        Cents required = requiredToConfirm(finalValue, type, minimumDeposit);
        if (validated.amount() <= 0) return PaymentStatus.NO_PAYMENT;
        if (validated.amount() >= finalValue.amount()) return PaymentStatus.FULLY_PAID;
        if (type == PaymentType.DEPOSIT) {
            return validated.amount() >= required.amount() ? PaymentStatus.DEPOSIT_RECEIVED : PaymentStatus.PARTIAL;
        }
        return status == ReservationStatus.CONFIRMED ? PaymentStatus.BALANCE_PENDING : PaymentStatus.PARTIAL;
    }

    private static Cents requiredToConfirm(Cents finalValue, PaymentType type, Cents minimumDeposit) {
        if (type == PaymentType.FULL_PAYMENT) return finalValue;
        if (minimumDeposit == null) throw new DomainException("minimumDeposit", "a deposit needs its minimum");
        return minimumDeposit;
    }
}
