package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DepartureReservationPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentActor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// HU-SEC-007 G-SEC9-1 (Project Lead, 2026-11-11, option A): the actor of an exceptional or emergency access is PLATFORM_SUPPORT, read only. Only the read checks accept it; every write check refuses it,
// and the sensitive read does not ask for the permission claim (the scope SENSITIVE of the token is what the gate checks).
class PlatformSupportReadTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final CustomerActor SUPPORT = new CustomerActor(TENANT, ACTOR, CustomerActor.Role.PLATFORM_SUPPORT);

    @Test
    void thePlatformSupportActorReadsCustomersAndReservations() {
        CustomerAuthorization.requireRead(SUPPORT);
        CustomerAuthorization.requireSensitiveRead(SUPPORT);
        ReservationAuthorization.requireRead(SUPPORT);
        ReservationAuthorization.requireDepartures(SUPPORT);
    }

    @Test
    void thePlatformSupportActorNeverWrites() {
        assertThatThrownBy(() -> CustomerAuthorization.requireWrite(SUPPORT)).isInstanceOf(CustomerAccessDeniedException.class);
        assertThatThrownBy(() -> ReservationAuthorization.requireWrite(SUPPORT)).isInstanceOf(ReservationAccessDeniedException.class);
        assertThatThrownBy(() -> ReservationAuthorization.requireQuote(SUPPORT)).isInstanceOf(ReservationAccessDeniedException.class);
        assertThatThrownBy(() -> ReservationAuthorization.requireOwnQuote(SUPPORT)).isInstanceOf(ReservationAccessDeniedException.class);
    }

    @Test
    void thePlatformSupportActorReadsTheCatalogRulesAndEstablishmentsButWritesNone() {
        var rules = new DiscountRuleActor(TENANT, ACTOR, DiscountRuleActor.Role.PLATFORM_SUPPORT);
        DiscountRuleAuthorization.requireRead(rules);
        assertThatThrownBy(() -> DiscountRuleAuthorization.requireWrite(rules)).isInstanceOf(DiscountRuleAccessDeniedException.class);
        var establishments = new EstablishmentActor(TENANT, ACTOR, EstablishmentActor.Role.PLATFORM_SUPPORT);
        EstablishmentAuthorization.require(establishments, false);
        assertThatThrownBy(() -> EstablishmentAuthorization.require(establishments, true)).isInstanceOf(EstablishmentAccessDeniedException.class);
    }

    @Test
    void thePlatformSupportActorListsTheReservationsOfADeparture() {
        var page = new DepartureReservationPage(java.util.List.of(), 0);
        var list = new ListDepartureReservationsUseCase((tenant, service, salida, number, limit) -> Optional.of(page));

        assertThat(list.list(SUPPORT, UUID.randomUUID(), UUID.randomUUID(), 1, 20)).isSameAs(page);
    }
}
