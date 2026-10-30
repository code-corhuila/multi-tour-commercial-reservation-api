package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// HU-COM-008 over HTTP (api spec T-02, T-06, T-07; AC-08.1, AC-08.2, AC-08.7, AC-08.9, AC-08.15, AC-08.17): the Final Customer
// uses the same routes as the operator, with the customer of the account of the token and only their own reservations.
class OwnReservationHttpTest extends ReservationHttpBase {

    private static final UUID ACCOUNT = UUID.fromString(TestJwtKeys.ACCOUNT), OTHER_ACCOUNT = new UUID(0, 0x457),
            OTHER_CUSTOMER = new UUID(1, 78);

    @BeforeEach void linkTheCustomers() {
        customers.save(customers.customers.get(customer).linkedTo(ACCOUNT));
    }

    private static String tokenOf(UUID account) throws Exception {
        var claims = TestJwtKeys.claims();
        claims.put("sub", account.toString()); claims.put("roles", List.of(FINAL_CUSTOMER));
        return TestJwtKeys.token(Map.of("alg", "RS256"), claims, TestJwtKeys.TRUSTED);
    }

    private MockHttpServletRequestBuilder as(UUID account, MockHttpServletRequestBuilder request) throws Exception {
        return request.header("Authorization", "Bearer " + tokenOf(account)).header("X-Correlation-Id", TRACE);
    }

    private MockHttpServletRequestBuilder createAs(UUID account, String key) throws Exception {
        return as(account, post(BASE + "/reservations")).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(ownBody()));
    }

    private Map<String, Object> ownBody() {
        var body = createBody(); body.remove("customerId");
        return body;
    }

    @Test void aFinalCustomerCreatesAReservationForTheCustomerOfTheirAccount() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(ownBody(), "reservation-key-21", FINAL_CUSTOMER), 201));
        assertThat(created.get("reservationStatus").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(created.get("paymentStatus").asText()).isEqualTo("NO_PAYMENT");
        assertThat(created.get("seatHeld").asBoolean()).isFalse();
        assertThat(store.reservations.values()).singleElement().satisfies(found -> {
            assertThat(found.customerId()).isEqualTo(customer);
            assertThat(found.createdBy()).isEqualTo(ACCOUNT);
        });
    }

    @Test void aFinalCustomerQuotesWithTheSameBodyAsTheOperator() throws Exception {
        var quote = CatalogHttpTest.json(exchange(quote(quoteBody(2), FINAL_CUSTOMER), 200));
        assertThat(quote.isObject()).isTrue();
    }

    @Test void creatingWithoutATokenAnswers401AndStoresNothing() throws Exception {
        exchange(post401(), 401);
        assertThat(store.reservations).isEmpty();
    }

    private MockHttpServletRequestBuilder post401() throws Exception {
        return post(BASE + "/reservations")
                .header("Idempotency-Key", "reservation-key-22").header("X-Correlation-Id", TRACE).contentType(MediaType.APPLICATION_JSON)
                .content(TestJwtKeys.JSON.writeValueAsBytes(ownBody()));
    }

    @Test void aCustomerOrATenantInTheBodyOfAFinalCustomerIsRejectedNamingTheField() throws Exception {
        var withCustomer = ownBody(); withCustomer.put("customerId", OTHER_CUSTOMER.toString());
        CatalogHttpTest.assertField(exchange(create(withCustomer, "reservation-key-23", FINAL_CUSTOMER), 400), "customerId");
        var withTenant = ownBody(); withTenant.put("tenantId", otherTenant.toString());
        CatalogHttpTest.assertField(exchange(create(withTenant, "reservation-key-24", FINAL_CUSTOMER), 400), "tenantId");
        assertThat(store.reservations).isEmpty();
    }

    @Test void anAccountWithoutACustomerRecordIsAskedForTheHolderAndNothingIsStored() throws Exception {
        CatalogHttpTest.assertField(exchange(createAs(OTHER_ACCOUNT, "reservation-key-25"), 400), "holder");
        assertThat(store.reservations).isEmpty();
    }

    @Test void theSameKeyOfAnotherCustomerIsRefusedAndTheirReservationIsNeverReturned() throws Exception {
        exchange(create(ownBody(), "reservation-key-26", FINAL_CUSTOMER), 201);
        var other = new UUID(0, 0x458);
        customers.save(Customer.registerLead(OTHER_CUSTOMER, tenant,
                "CC777", "Otra Persona", BirthDateOrAge.ofAge(40),
                new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo("otra@example.invalid", "3000000001"),
                true, NOW).linkedTo(other));
        var error = CatalogHttpTest.json(exchange(createAs(other, "reservation-key-26"), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.toString()).doesNotContain(store.reservations.keySet().iterator().next().toString());
        assertThat(store.reservations).hasSize(1);
    }

    @Test void aFinalCustomerListsAndReadsOnlyTheirOwnReservations() throws Exception {
        var mine = CatalogHttpTest.json(exchange(create(ownBody(), "reservation-key-27", FINAL_CUSTOMER), 201));
        exchange(create(createBody(), "reservation-key-28", ADMIN), 201);
        customers.save(Customer.registerLead(OTHER_CUSTOMER, tenant,
                "CC778", "Otra Persona", BirthDateOrAge.ofAge(40),
                new co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo("otra@example.invalid", "3000000001"),
                true, NOW));
        var theirs = createBody(); theirs.put("customerId", OTHER_CUSTOMER.toString());
        var others = CatalogHttpTest.json(exchange(create(theirs, "reservation-key-29", ADMIN), 201));
        assertThat(store.reservations).hasSize(3);

        var page = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), FINAL_CUSTOMER), 200));
        assertThat(page.at("/meta/total").asInt()).isEqualTo(2);
        var all = CatalogHttpTest.json(exchange(withToken(get(BASE + "/reservations"), ADMIN), 200));
        assertThat(all.at("/meta/total").asInt()).isEqualTo(3);

        var read = CatalogHttpTest.json(exchange(
                withToken(get(BASE + "/reservations/" + mine.get("reservationId").asText()), FINAL_CUSTOMER), 200));
        assertThat(read).isEqualTo(mine);
        exchange(withToken(get(BASE + "/reservations/" + others.get("reservationId").asText()), FINAL_CUSTOMER), 404);
    }

    @Test void anAccountWithoutACustomerRecordSeesAnEmptyListAndEveryReadAnswersNotFound() throws Exception {
        var created = CatalogHttpTest.json(exchange(create(ownBody(), "reservation-key-30", FINAL_CUSTOMER), 201));
        var page = CatalogHttpTest.json(exchange(as(OTHER_ACCOUNT, get(BASE + "/reservations")), 200));
        assertThat(page.at("/meta/total").asInt()).isZero();
        exchange(as(OTHER_ACCOUNT, get(BASE + "/reservations/" + created.get("reservationId").asText())), 404);
    }
}
