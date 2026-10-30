package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// HU-COM-005 companions over HTTP, through the creation of the reservation (api spec T-02, T-03; AC-05.4, AC-05.5, AC-05.6,
// AC-05.12, AC-05.13). The companions belong to the reservation (DP-02), so there is no route of their own in the contract.
class ReservationCompanionsHttpTest extends ReservationHttpBase {

    private static Map<String, Object> companion(String name, String document, Integer age) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (name != null) item.put("fullName", name);
        if (document != null) item.put("documentNumber", document);
        if (age != null) item.put("age", age);
        return item;
    }

    private Map<String, Object> bodyWith(Map<String, Object>... companions) {
        var body = createBody();
        body.put("companions", List.of(companions));
        return body;
    }

    private List<String> fieldsOf(int code, Map<String, Object> body, String key) throws Exception {
        var error = CatalogHttpTest.json(exchange(create(body, key, ADMIN), code));
        var fields = new ArrayList<String>();
        error.get("details").forEach(detail -> fields.add(detail.get("field").asText()));
        return fields;
    }

    private void aHighRiskTourWithACompleteLead() {
        catalog.save(CatalogService.register(tour, tenant, CatalogServiceType.TOUR, "Tour Ficticio",
                CommercialTerms.of(100_000L, 40_000L, 20, false, true), NOW));
        customers.save(Customer.registerLead(customer, tenant, "CC12345", "Persona Ficticia", BirthDateOrAge.ofAge(30),
                new ContactInfo("persona@example.invalid", "3000000000"), true, NOW, true,
                new HighRiskData("O+", "Contacto 3001112233", "Ninguna", true)));
    }

    @Test @SuppressWarnings("unchecked")
    void aCompanionWithTheMinimumDataIsStoredWithTheReservationAndTheAnswerCarriesNone() throws Exception {
        var answer = CatalogHttpTest.json(exchange(create(bodyWith(companion("Acompanante Uno", "cc 777", 25)), "companion-key-1", ADMIN), 201));
        var stored = store.reservations.values().iterator().next();
        assertThat(stored.companions()).hasSize(1);
        assertThat(stored.companions().get(0).documentNumber().value()).isEqualTo("CC 777");
        assertThat(answer.has("companions")).isFalse();
        assertThat(answer.toString()).doesNotContain("Acompanante Uno").doesNotContain("777");
    }

    @Test @SuppressWarnings("unchecked")
    void everyMissingDatumOfACompanionIsNamedWithItsPosition() throws Exception {
        var fields = fieldsOf(400, bodyWith(companion("Acompanante Uno", "CC1", 25), companion(null, null, null)), "companion-key-2");
        assertThat(fields).containsExactlyInAnyOrder("companions[1].fullName", "companions[1].documentNumber",
                "companions[1].birthDateOrAge");
        assertThat(store.reservations).isEmpty();
    }

    @Test @SuppressWarnings("unchecked")
    void aCompanionWithAFutureBirthDateIsRefusedNamingItsPosition() throws Exception {
        var item = companion("Acompanante Uno", "CC1", null);
        item.put("birthDate", LocalDate.now().plusDays(2).toString());
        var fields = fieldsOf(400, bodyWith(item), "companion-key-3");
        assertThat(fields).containsExactly("companions[0].birthDate");
        assertThat(store.reservations).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"CC12345", " cc12345 ", "cC12345"})
    @SuppressWarnings("unchecked")
    void theDocumentOfTheLeadTypedInAnotherWayIsRejected(String document) throws Exception {
        var error = CatalogHttpTest.json(exchange(create(bodyWith(companion("Acompanante Uno", document, 25)), "companion-key-4", ADMIN), 422));
        assertThat(error.get("error").asText()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(error.get("message").asText()).contains("document");
        assertThat(error.toString()).doesNotContain("12345");
        assertThat(store.reservations).isEmpty();
    }

    @Test @SuppressWarnings("unchecked")
    void twoCompanionsWithOneDocumentTypedTwoWaysAreRejectedAndNothingIsStored() throws Exception {
        var body = bodyWith(companion("Acompanante Uno", "CC9", 25), companion("Acompanante Dos", "  cc9 ", 30));
        exchange(create(body, "companion-key-5", ADMIN), 422);
        assertThat(store.reservations).isEmpty();
    }

    @Test @SuppressWarnings("unchecked")
    void moreCompanionsThanThePeopleLeftByTheLeadAreRefused() throws Exception {
        var body = bodyWith(companion("Uno", "CC1", 25), companion("Dos", "CC2", 25), companion("Tres", "CC3", 25));
        assertThat(fieldsOf(400, body, "companion-key-6")).containsExactly("companions");
        assertThat(store.reservations).isEmpty();
    }

    @Test @SuppressWarnings("unchecked")
    void aHighRiskTourRefusesACompanionWithoutTheExtraDataNamingItsPositionAndFields() throws Exception {
        aHighRiskTourWithACompleteLead();
        var complete = companion("Acompanante Uno", "CC1", 25);
        complete.put("informedConsentSigned", true);
        complete.put("emergencyInfo", Map.of("bloodType", "A+", "emergencyContact", "Contacto 3002223344", "mobilityRestrictions", "Ninguna"));
        var partial = companion("Acompanante Dos", "CC2", 30);
        partial.put("emergencyInfo", Map.of("bloodType", "B+"));
        var fields = fieldsOf(422, bodyWith(complete, partial), "companion-key-7");
        assertThat(fields).containsExactly("companions[1].emergencyContact", "companions[1].mobilityRestrictions",
                "companions[1].informedConsentSigned");
        assertThat(store.reservations).isEmpty();
    }

    @Test @SuppressWarnings("unchecked")
    void aHighRiskTourAcceptsACompanionWithTheExtraDataAndKeepsIt() throws Exception {
        aHighRiskTourWithACompleteLead();
        var complete = companion("Acompanante Uno", "CC1", 25);
        complete.put("informedConsentSigned", true);
        complete.put("emergencyInfo", Map.of("bloodType", "A+", "emergencyContact", "Contacto 3002223344", "mobilityRestrictions", "Ninguna"));
        exchange(create(bodyWith(complete), "companion-key-8", ADMIN), 201);
        assertThat(store.reservations.values().iterator().next().companions().get(0).highRiskData()).isNotNull();
    }

    @Test @SuppressWarnings("unchecked")
    void anOrdinaryTourDoesNotRequireTheExtraDataOfTheCompanion() throws Exception {
        exchange(create(bodyWith(companion("Acompanante Uno", "CC1", 25)), "companion-key-9", ADMIN), 201);
        assertThat(store.reservations).hasSize(1);
    }
}
