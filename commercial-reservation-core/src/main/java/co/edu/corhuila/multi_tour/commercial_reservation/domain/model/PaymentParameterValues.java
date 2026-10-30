package co.edu.corhuila.multi_tour.commercial_reservation.domain.model;

import java.util.UUID;

// One row of payment parameters, the columns of payment_condition (PROPOSED, DP-04): the waits in hours (1 to 720), the minimum
// deposit in basis points for a deposit only, the balance advance and the two enabled methods. serviceId null is the tenant
// level; the precedence of a service value is NO DEFINIDO. There is no penalty field (DP-09).
public record PaymentParameterValues(PaymentType paymentType, UUID serviceId, int initialWaitHours, int additionalWaitHours,
        Integer minimumDepositBp, Integer balanceDeadlineAdvanceHours, boolean transferEnabled, boolean cashEnabled) {

    private static final int MAX_HOURS = 720;

    public PaymentParameterValues {
        if (paymentType == null) throw new DomainException("paymentType", "paymentType is required");
        requireHours("initialWaitHours", initialWaitHours);
        requireHours("additionalWaitHours", additionalWaitHours);
        if (balanceDeadlineAdvanceHours != null) requireHours("balanceDeadlineAdvanceHours", balanceDeadlineAdvanceHours);
        boolean deposit = paymentType == PaymentType.DEPOSIT;
        if (deposit && (minimumDepositBp == null || minimumDepositBp < 1 || minimumDepositBp > BasisPoints.FULL))
            throw new DomainException("minimumDepositBp", "a deposit needs a minimum between 1 and 10000 basis points");
        if (!deposit && minimumDepositBp != null)
            throw new DomainException("minimumDepositBp", "a full payment has no minimum deposit");
    }

    private static void requireHours(String field, int hours) {
        if (hours < 1 || hours > MAX_HOURS) throw new DomainException(field, field + " must be between 1 and " + MAX_HOURS);
    }
}
