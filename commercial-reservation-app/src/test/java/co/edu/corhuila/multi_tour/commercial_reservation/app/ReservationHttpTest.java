package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-007 over HTTP, the creation and the reads (api spec T-06, T-07, T-09; AC-07.1, AC-07.2, AC-07.14, AC-07.16, AC-07.17).
class ReservationHttpTest extends ReservationHttpBase {

    // ---- create (AC-07.1, AC-07.2, AC-07.14)

    @Test void aReservationIsCreatedPendingPaymentWithItsLocationAndNoSeatHeld() throws Exception {
        var result = exchange(create(createBody(), "reservation-key-1", ADMIN), 201);
        var reservation = CatalogHttpTest.json(result);
        assertThat(reservation.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(reservation.get("paymentStatus").asText()).isEqualTo("NO_PAYMENT");
        assertThat(reservation.get("finalValueCents").asLong()).isEqualTo(300_000L);
        assertThat(reservation.get("pendingBalanceCents").asLong()).isEqualTo(300_000L);
        assertThat(reservation.get("seatHeld").asBoolean()).isFalse();
        assertThat(reservation.get("paymentType").asText()).isEqualTo("DEPOSIT");
        assertThat(reservation.get("createdAt").asText()).endsWith("Z");
        assertThat(reservation.has("validatedValueCents")).isFalse();
        assertThat(reservation.has("tenantId")).isFalse();
        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo(BASE + "/reservations/" + reservation.get("reservationId").asText());
        assertThat(store.reservations).hasSize(1);
    }

    @Test void repeatingTheSameKeyAnswers200WithTheSameReservationAndNoSecondOne() throws Exception {
        var first = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-2", ADMIN), 201));
        var second = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-2", ADMIN), 200));
        assertThat(second).isEqualTo(first);
        assertThat(store.reservations).hasSize(1);
        assertThat(store.saves).isEqualTo(1);
    }

    @Test void theSameKeyForAnotherCustomerIsRefusedWith422() throws Exception {
        exchange(create(createBody(), "reservation-key-3", ADMIN), 201);
        var other = createBody(); other.put("customerId", new UUID(1, 77).toString());
        var error = CatalogHttpTest.json(exchange(create(other, "reservation-key-3", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(store.reservations).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"", "short"})
    void aMissingOrShortKeyIsRefusedNamingTheHeader(String key) throws Exception {
        CatalogHttpTest.assertField(exchange(create(createBody(), key.isEmpty() ? null : key, ADMIN), 400), "Idempotency-Key");
        assertThat(store.reservations).isEmpty();
    }

    @Test void everyMissingMandatoryFieldIsNamedAtOnce() throws Exception {
        var error = CatalogHttpTest.json(exchange(create(Map.of("people", 2), "reservation-key-4", ADMIN), 400));
        var fields = new ArrayList<String>();
        error.get("details").forEach(detail -> fields.add(detail.get("field").asText()));
        assertThat(fields).containsExactlyInAnyOrder("customerId", "salidaId", "serviceId", "paymentType", "paymentMethod");
        assertThat(store.reservations).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"finalValueCents", "tenantId", "reservationStatus", "createdBy"})
    void aValueATenantAStateOrAnOnBehalfOfInTheBodyIsRefused(String field) throws Exception {
        var body = createBody(); body.put(field, "x");
        CatalogHttpTest.assertField(exchange(create(body, "reservation-key-5", ADMIN), 400), field);
        assertThat(store.reservations).isEmpty();
    }

    @Test void notEnoughSeatsIsRefusedWith422CarryingTheSeatsAvailableAndStoresNothing() throws Exception {
        var body = createBody(); body.put("people", 4);
        var error = CatalogHttpTest.json(exchange(create(body, "reservation-key-6", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("3 seats");
        assertThat(store.reservations).isEmpty();
        assertThat(store.departures.get(departure).heldSeats()).isEqualTo(7);
    }

    @Test void aCustomerOrADepartureOfAnotherTenantAnswersNotFound() throws Exception {
        var foreignCustomer = createBody(); foreignCustomer.put("customerId", new UUID(1, 78).toString());
        exchange(create(foreignCustomer, "reservation-key-7", ADMIN), 404);
        var unknownDeparture = createBody(); unknownDeparture.put("salidaId", new UUID(1, 79).toString());
        exchange(create(unknownDeparture, "reservation-key-8", ADMIN), 404);
        assertThat(store.reservations).isEmpty();
    }

    @Test void aGroupWithARepeatedDocumentIsRefusedWith422() throws Exception {
        var body = createBody();
        body.put("companions", List.of(Map.of("fullName", "Acompanante Ficticio", "documentNumber", " cc12345 ", "age", 25)));
        var error = CatalogHttpTest.json(exchange(create(body, "reservation-key-9", ADMIN), 422));
        assertThat(error.get("message").asText()).contains("document");
        assertThat(store.reservations).isEmpty();
    }

    @Test void aHighRiskTourWithALeadWithoutTheExtraDataNamesTheFieldsWith422() throws Exception {
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, false, true), NOW));
        var error = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-10", ADMIN), 422));
        var fields = new ArrayList<String>();
        error.get("details").forEach(detail -> fields.add(detail.get("field").asText()));
        assertThat(fields).contains("bloodType", "informedConsentSigned");
        assertThat(store.reservations).isEmpty();
    }

    @Test void anInactiveTenantCreatesNothing() throws Exception {
        store.tenants.put(tenant, new TenantRef(tenant, false));
        exchange(create(createBody(), "reservation-key-11", ADMIN), 422);
        assertThat(store.reservations).isEmpty();
    }

    @Test void theRolesThatOnlyReadAndTheFinalCustomerCannotCreateAndNothingIsStored() throws Exception {
        for (String role : List.of(MANAGER, ACCOUNTANT, ANALYST, FINAL_CUSTOMER)) {
            exchange(create(createBody(), "reservation-key-12", role), 403);
        }
        exchange(create(createBody(), "reservation-key-13", COLLABORATOR), 201);
        assertThat(store.reservations).hasSize(1);
    }

    // ---- list and get (AC-07.16, AC-07.17)

    @Test void theListIsPagedAndHidesTheArchivedUnlessAllIsAsked() throws Exception {
        exchange(create(createBody(), "reservation-key-14", ADMIN), 201);
        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), MANAGER), 200));
        assertThat(store.lastIncludeArchived).isFalse();
        assertThat(page.at("/data/0/customerName").asText()).isEqualTo("Persona Ficticia");
        assertThat(page.at("/data/0/pendingBalanceCents").asLong()).isEqualTo(300_000L);
        assertThat(page.at("/data/0/reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(page.at("/meta/total").asInt()).isEqualTo(1);
        exchange(withToken(get(BASE + "/reservations").param("scope", "all"), ACCOUNTANT), 200);
        assertThat(store.lastIncludeArchived).isTrue();
        CatalogHttpTest.assertField(exchange(withToken(get(BASE + "/reservations").param("scope", "some"), ADMIN), 400), "scope");
        exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 403);
    }

    @Test void aReservationIsReadAsFixedAndOneOfAnotherTenantAnswersNotFound() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(createBody(), "reservation-key-15", ADMIN), 201));
        String id = created.get("reservationId").asText();
        var read = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations/" + id), ANALYST), 200));
        assertThat(read).isEqualTo(created);
        exchange(withToken(get(BASE + "/reservations/" + new UUID(1, 81)), ADMIN), 404);
        exchange(withToken(get(BASE + "/reservations/not-a-uuid"), ADMIN), 400);
        exchange(withToken(get(BASE + "/reservations/" + id), FINAL_CUSTOMER), 403);
    }
}
