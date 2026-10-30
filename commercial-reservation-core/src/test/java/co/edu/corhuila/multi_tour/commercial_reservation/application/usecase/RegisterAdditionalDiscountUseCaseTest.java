package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.OTHER_REASON;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.REASON;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.RESERVATION;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.AdditionalDiscountFakes.reservation;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.ReservationSnapshot.State;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterAdditionalDiscount.Command;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.AdditionalDiscountRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AdditionalDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BasisPoints;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// T-03: RN-04, RN-05, RN-06, RN-07, RN-11, RN-13 and AC-04.4, 04.6, 04.7, 04.8, 04.12, 04.13.
// TODO DP-03: there is no cap test; the cap of the additional discount is NO DEFINIDO.
class RegisterAdditionalDiscountUseCaseTest {
    private final AdditionalDiscountFakes fakes = new AdditionalDiscountFakes()
            .withReservation(reservation(State.PENDING_PAYMENT, 1_000_000, 0))
            .withReason(REASON, "Loyal customer", true)
            .withReason(OTHER_REASON, "Old reason", false);
    private final RegisterAdditionalDiscountUseCase useCase = new RegisterAdditionalDiscountUseCase(fakes, fakes,
            fakes.reasonStore, fakes, fakes, fakes, new DiscountRuleFakes().clock);
    private final Command command = new Command(RESERVATION, REASON, 500);

    @Test void theAdditionalDiscountComesLastWithItsReasonAndAuthor() { // AC-04.4, RN-04
        AdditionalDiscount discount = useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(discount.line().base()).isEqualTo(new Cents(1_000_000));
        assertThat(discount.line().amount()).isEqualTo(new Cents(50_000));
        assertThat(discount.line().subtotal()).isEqualTo(new Cents(950_000));
        assertThat(discount.reason()).isEqualTo("Loyal customer");
        assertThat(discount.authorizerId()).isEqualTo(ACTOR);
        assertThat(discount.order()).isEqualTo(3);
        assertThat(fakes.discounts).containsEntry(RESERVATION, discount);
    }

    @Test void theRegistrationPublishesReservationModifiedWithReasonAuthorizerAndOrder() {
        useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(fakes.events).singleElement().satisfies(event -> {
            assertThat(event.reservationId()).isEqualTo(RESERVATION);
            assertThat(event.previousSubtotal()).isEqualTo(new Cents(1_000_000));
            assertThat(event.newSubtotal()).isEqualTo(new Cents(950_000));
            assertThat(event.reason()).isEqualTo("Loyal customer");
            assertThat(event.authorizerId()).isEqualTo(ACTOR);
            assertThat(event.order()).isEqualTo(3);
        });
    }

    @Test void aFailingOutboxLeavesNoDiscount() {
        fakes.failOnAppend = new IllegalStateException("outbox down");
        assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR), command))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fakes.discounts).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {0, 99, -1}) // AC-04.6, RN-05: minimum 1 %, never below zero
    void aPercentageBelowOnePercentIsRefused(int basisPoints) {
        assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR),
                new Command(RESERVATION, REASON, basisPoints))).isInstanceOf(DomainException.class);
        assertThat(fakes.discounts).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void exactlyOnePercentIsAccepted() {
        var discount = useCase.register(actor(Role.TENANT_ADMINISTRATOR), new Command(RESERVATION, REASON, 100));
        assertThat(discount.line().amount()).isEqualTo(new Cents(10_000));
    }

    @Test void aSecondAdditionalDiscountIsRefused() { // AC-04.7, RN-06
        useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        fakes.events.clear();
        assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR), command))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.events).isEmpty();
    }

    @Test void aReservationInAnIneligibleStateIsRefused() { // AC-04.7
        fakes.withReservation(reservation(State.CANCELLED, 1_000_000, 0));
        assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR), command))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThat(fakes.discounts).isEmpty();
    }

    @Test void aConfirmedReservationIsEligible() {
        fakes.withReservation(reservation(State.CONFIRMED, 1_000_000, 0));
        assertThat(useCase.register(actor(Role.TENANT_ADMINISTRATOR), command)).isNotNull();
    }

    @Test void aReservationOfAnotherTenantIsNotFound() {
        assertThatThrownBy(() -> useCase.register(
                new co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor(
                        DiscountRuleFakes.OTHER_TENANT, ACTOR, Role.TENANT_ADMINISTRATOR), command))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test void aDisabledOrUnknownReasonIsABusinessRuleViolation() { // AC-04.8, RN-07: the contract answers 422
        for (UUID reason : new UUID[] {OTHER_REASON, new UUID(0, 0xe9)}) {
            assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR),
                    new Command(RESERVATION, reason, 500))).isInstanceOf(BusinessRuleViolation.class);
        }
        assertThat(fakes.discounts).isEmpty();
    }

    @Test void aMissingReasonIsAValidationError() { // the field is required: the contract answers 400
        assertThatThrownBy(() -> useCase.register(actor(Role.TENANT_ADMINISTRATOR),
                new Command(RESERVATION, null, 500))).isInstanceOf(DomainException.class);
        assertThat(fakes.discounts).isEmpty();
    }

    @Test void thePreviewReportsWhatWasPaidAboveTheNewValueAsTheExcess() { // AC-04.13, RN-13
        fakes.withReservation(reservation(State.CONFIRMED, 1_000_000, 980_000));
        var preview = useCase.previewWithExcess(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, 500);
        assertThat(preview.line().subtotal()).isEqualTo(new Cents(950_000));
        assertThat(preview.excess()).isEqualTo(new Cents(30_000));
        assertThat(fakes.excesses).isEmpty(); // a preview never opens the treatment
    }

    @Test void noExcessIsZeroInThePreview() {
        assertThat(useCase.previewWithExcess(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, 500).excess())
                .isEqualTo(new Cents(0));
    }

    @Test void thePreviewIsRefusedForAnIneligibleStateAsABusinessRule() { // contract: 422
        fakes.withReservation(reservation(State.CANCELLED, 1_000_000, 0));
        assertThatThrownBy(() -> useCase.previewWithExcess(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, 500))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test void thePreviewEqualsWhatTheRegistrationRecalculates() { // AC-04.13
        var preview = useCase.preview(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, 750);
        var discount = useCase.register(actor(Role.TENANT_ADMINISTRATOR), new Command(RESERVATION, REASON, 750));
        assertThat(preview).isEqualTo(discount.line());
        assertThat(preview.amount()).isEqualTo(new Cents(75_000));
    }

    @Test void thePreviewWritesNothing() {
        useCase.preview(actor(Role.TENANT_ADMINISTRATOR), RESERVATION, 500);
        assertThat(fakes.discounts).isEmpty();
        assertThat(fakes.events).isEmpty();
    }

    @Test void anExcessBelowWhatIsPaidOpensTheTreatment() { // AC-04.13, RN-13
        fakes.withReservation(reservation(State.CONFIRMED, 1_000_000, 980_000));
        useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(fakes.excesses).containsExactly(new Cents(30_000));
    }

    @Test void noExcessMeansNoTreatment() {
        fakes.withReservation(reservation(State.CONFIRMED, 1_000_000, 950_000));
        useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(fakes.excesses).isEmpty();
    }

    @Test void noPortRewritesTheFixedGeneralDiscounts() { // AC-04.12, RN-11
        assertThat(methodNames(ReservationReader.class)).containsExactly("findByTenantAndId");
        assertThat(methodNames(AdditionalDiscountRepository.class))
                .containsExactlyInAnyOrder("save", "existsByReservation", "findByReservation");
    }

    @Test void theRegisteredDiscountIsAnImmutableRecordOfBasisPoints() {
        var discount = useCase.register(actor(Role.TENANT_ADMINISTRATOR), command);
        assertThat(discount.rate()).isEqualTo(new BasisPoints(500));
        assertThat(AdditionalDiscount.class.isRecord()).isTrue();
    }

    private static Set<String> methodNames(Class<?> type) {
        return java.util.Arrays.stream(type.getMethods()).map(Method::getName).collect(java.util.stream.Collectors.toSet());
    }
}
