package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// The closed payload that job 7 receives for the events of the catalog, the establishments and the discounts (domain-events.md: tenant, reference, prior and new condition or value, actor, time).
// The tenant, the reference and the time travel in the envelope; the actor, the two conditions or states and the type of the establishment travel in the payload, and no personal datum does.
// Runs against the real database only, like the other persistence tests. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OutboxDeliveryPayloadPersistenceTest {
    private ReservationPersistenceFixture fixture;
    private JdbcOutboxDeliveryStore store;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        store = new JdbcOutboxDeliveryStore(fixture.jdbc);
    }

    private OutboxEventView delivered(UUID eventId) {
        return store.find(eventId).orElseThrow();
    }

    @Test
    void aCatalogServiceEventCarriesTheActorAndBothConditions() {
        UUID event = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, service_id, previous_catalog_status, new_catalog_status, actor_id, "
                + "occurred_at) VALUES (?, ?, 'CatalogServiceInactivated', ?, 'ACTIVE', 'INACTIVE', ?, now())", event, fixture.tenant, fixture.service, fixture.actor);

        assertThat(delivered(event).payload()).containsOnlyKeys("previousCatalogStatus", "newCatalogStatus", "actorId")
                .containsEntry("previousCatalogStatus", "ACTIVE").containsEntry("newCatalogStatus", "INACTIVE").containsEntry("actorId", fixture.actor.toString());
    }

    @Test
    void anEstablishmentPublishedEventCarriesTheActorTheConditionAndTheTypeOfTheEstablishment() {
        UUID association = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.associated_establishment (association_id, tenant_id, establishment_type, commercial_name, commercial_information, "
                + "association_status, created_at, updated_at) VALUES (?, ?, 'RESTAURANT', 'Restaurante Ficticio', 'Informacion ficticia', 'ACTIVE', now(), now())", association, fixture.tenant);
        UUID event = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, association_id, new_catalog_status, actor_id, occurred_at) "
                + "VALUES (?, ?, 'EstablishmentAssociationPublished', ?, 'ACTIVE', ?, now())", event, fixture.tenant, association, fixture.actor);

        assertThat(delivered(event).payload()).containsOnlyKeys("newCatalogStatus", "actorId", "establishmentType")
                .containsEntry("establishmentType", "RESTAURANT").containsEntry("actorId", fixture.actor.toString());
    }

    @Test
    void aDiscountRuleEventCarriesTheActorAndBothStatesWithTheirNullFieldsIntact() {
        UUID event = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, discount_rule_id, previous_discount_rule_state, new_discount_rule_state, "
                + "actor_id, occurred_at) VALUES (?, ?, 'DiscountRuleChanged', ?, ?::jsonb, ?::jsonb, ?, now())", event, fixture.tenant, fixture.percentageRule,
                "{\"status\": \"ACTIVE\", \"capCents\": null}", "{\"status\": \"INACTIVE\", \"capCents\": null}", fixture.actor);

        assertThat(delivered(event).payload()).containsOnlyKeys("previousState", "newState", "actorId").containsEntry("actorId", fixture.actor.toString());
        assertThat(delivered(event).payload().get("previousState").toString()).contains("status=ACTIVE").contains("capCents=null");
        assertThat(delivered(event).payload().get("newState").toString()).contains("status=INACTIVE").contains("capCents=null");
    }

    @Test
    void aDiscountRuleCreationHasNoPreviousState() {
        UUID event = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, discount_rule_id, new_discount_rule_state, actor_id, occurred_at) "
                + "VALUES (?, ?, 'DiscountRuleChanged', ?, ?::jsonb, ?, now())", event, fixture.tenant, fixture.fixedRule, "{\"status\": \"ACTIVE\"}", fixture.actor);

        assertThat(delivered(event).payload()).containsOnlyKeys("newState", "actorId");
    }
}
