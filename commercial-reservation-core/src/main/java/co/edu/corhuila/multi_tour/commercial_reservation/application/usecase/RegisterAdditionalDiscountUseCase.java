package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AuthorizableReasonRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.DiscountRuleTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ExcessTreatment;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationModifiedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AuthorizableReason;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountLine;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationModified;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class RegisterAdditionalDiscountUseCase implements RegisterAdditionalDiscount {
    private final ReservationReader reservations;
    private final AdditionalDiscountRepository discounts;
    private final AuthorizableReasonRepository reasons;
    private final ReservationModifiedOutbox outbox;
    private final ExcessTreatment excess;
    private final DiscountRuleTransaction transaction;
    private final Clock clock;

    public RegisterAdditionalDiscountUseCase(ReservationReader reservations, AdditionalDiscountRepository discounts,
            AuthorizableReasonRepository reasons, ReservationModifiedOutbox outbox, ExcessTreatment excess,
            DiscountRuleTransaction transaction, Clock clock) {
        this.reservations = reservations;
        this.discounts = discounts;
        this.reasons = reasons;
        this.outbox = outbox;
        this.excess = excess;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public DiscountLine preview(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints) {
        DiscountRuleAuthorization.requireWrite(actor);
        ReservationSnapshot reservation = load(actor, reservationId);
        return AdditionalDiscount.preview(reservation.subtotal(), rate(percentageBasisPoints));
    }

    @Override
    public Preview previewWithExcess(DiscountRuleActor actor, UUID reservationId, Integer percentageBasisPoints) {
        DiscountRuleAuthorization.requireWrite(actor);
        ReservationSnapshot reservation = load(actor, reservationId);
        DiscountLine line = AdditionalDiscount.preview(reservation.subtotal(), rate(percentageBasisPoints));
        return new Preview(line, excess(reservation, line));
    }

    @Override
    public AdditionalDiscount register(DiscountRuleActor actor, Command command) {
        DiscountRuleAuthorization.requireWrite(actor);
        ReservationSnapshot reservation = load(actor, command.reservationId());
        AuthorizableReason reason = command.reasonId() == null ? null
                : reasons.findByTenantAndId(actor.tenantId(), command.reasonId()).filter(AuthorizableReason::enabled)
                        .orElse(null);
        if (reason == null) {
            // Required is a validation matter (400); a reason that is not an enabled one is a business rule (422).
            if (command.reasonId() == null) throw new DomainException("reason", "reason is required");
            throw new BusinessRuleViolation("the reason must be one of the enabled reasons");
        }
        // The same function as the preview, so both always agree (AC-04.13).
        DiscountLine line = AdditionalDiscount.preview(reservation.subtotal(), rate(command.percentageBasisPoints()));
        Instant now = clock.instant();
        int order = reservation.appliedDiscounts() + 1;
        AdditionalDiscount discount = new AdditionalDiscount(actor.tenantId(), reservation.reservationId(),
                rate(command.percentageBasisPoints()), line, reason.text(), actor.actorId(), order, now);
        // The discount, its event and the excess treatment commit or roll back together (norm 5.3.11).
        return transaction.inTransaction(() -> {
            discounts.save(discount);
            outbox.append(new ReservationModified(actor.tenantId(), reservation.reservationId(),
                    reservation.subtotal(), line.subtotal(), reason.text(), actor.actorId(), order, now));
            Cents above = excess(reservation, line);
            if (above.amount() > 0) {
                excess.open(actor.tenantId(), reservation.reservationId(), above);
            }
            return discount;
        });
    }

    private ReservationSnapshot load(DiscountRuleActor actor, UUID reservationId) {
        ReservationSnapshot reservation = reservations.findByTenantAndId(actor.tenantId(), reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        if (!reservation.eligible()) {
            throw new BusinessRuleViolation("only a pending or confirmed reservation takes an additional discount");
        }
        if (discounts.existsByReservation(actor.tenantId(), reservationId)) {
            throw new BusinessRuleViolation("the reservation already has an additional discount");
        }
        return reservation;
    }

    // What was paid above the new value; zero when nothing was paid above it.
    private static Cents excess(ReservationSnapshot reservation, DiscountLine line) {
        return new Cents(Math.max(0, reservation.paid().amount() - line.subtotal().amount()));
    }

    private static BasisPoints rate(Integer basisPoints) {
        return BasisPoints.of("percentage", basisPoints == null ? null : basisPoints.longValue());
    }
}
