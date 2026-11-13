package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcClientNoticeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcOutboxDeliveryStore;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentEventOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParameterRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentParametersChangeOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcPaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxEventView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.OutboxStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.RelayOutboxDeliveryUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameter;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParameterValues;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentParametersChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-015 api T-06 over the real schema (AC-15.6, AC-15.7, RN-03, RN-08): job 7 reads the pending events of the outbox, marks one published and records
// failed attempts. The engine counts, truncates and freezes; the routes write the outbox row and nothing else. Every value is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OutboxDeliveryPersistenceTest {
    private static final Cents MINIMUM = new Cents(97_200);
    private ReservationPersistenceFixture fixture;
    private JdbcReservationRepository repository;
    private JdbcPaymentEventOutbox events;
    private JdbcPaymentTransaction transaction;
    private RelayOutboxDeliveryUseCase relay;

    @BeforeEach
    void connectAsTheApplicationUser() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("TEST_DATABASE_USER") != null && System.getenv("TEST_DATABASE_PASSWORD") != null,
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        repository = new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions, new JdbcReservationCreatedOutbox(fixture.jdbc));
        events = new JdbcPaymentEventOutbox(fixture.jdbc, new JdbcClientNoticeOutbox(fixture.jdbc));
        transaction = new JdbcPaymentTransaction(fixture.transactions);
        relay = new RelayOutboxDeliveryUseCase(new JdbcOutboxDeliveryStore(fixture.jdbc), Clock.systemUTC());
    }

    private void persist(Reservation reservation) {
        transaction.inTransaction(() -> {
            repository.save(reservation);
            reservation.pullEvents().forEach(events::append);
            return null;
        });
    }

    private UUID approvedReservation() {
        UUID id = UUID.randomUUID();
        repository.save(fixture.reservation(id, true));
        Reservation reservation = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        UUID attempt = UUID.randomUUID();
        reservation.registerTransfer(attempt, new Cents(100_000), MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        reservation.allocateSeat(ReservationPersistenceFixture.NOW);
        persist(reservation);
        Reservation loaded = repository.findByTenantAndId(fixture.tenant, id).orElseThrow();
        loaded.approve(attempt, new Cents(100_000), null, MINIMUM, fixture.actor, ReservationPersistenceFixture.NOW);
        persist(loaded);
        return id;
    }

    private UUID noticeOf(UUID reservation) {
        return fixture.jdbc.queryForObject("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE reservation_id = ? "
                + "AND event_type = 'ClientNotified' AND payload ->> 'notificationType' = 'PROOF_APPROVED'", UUID.class, reservation);
    }

    private String businessState(UUID reservation) {
        return fixture.jdbc.queryForObject("SELECT (SELECT md5(string_agg(r::text, '|')) FROM commercial_reservation.reservation r WHERE reservation_id = ?) "
                + "|| (SELECT md5(string_agg(p::text, '|' ORDER BY payment_attempt_id)) FROM commercial_reservation.payment_attempt p WHERE reservation_id = ?) "
                + "|| (SELECT count(*) FROM commercial_reservation.capacity_allocation WHERE reservation_id = ?)::text", String.class, reservation, reservation,
                reservation);
    }

    // The database is shared with other tests, so the list is walked page by page until it ends.
    private List<OutboxEventView> pendingEvents() {
        List<OutboxEventView> all = new java.util.ArrayList<>();
        for (int page = 1; ; page++) {
            var items = relay.pending(page, 100).items();
            if (items.isEmpty()) return all;
            all.addAll(items);
        }
    }

    private List<UUID> pendingIds(int ignored) {
        return pendingEvents().stream().map(OutboxEventView::eventId).toList();
    }

    @Test
    void thePendingListHoldsTheEventsOfTheDomainWithTheEnvelopeAndNothingPersonal() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation);

        OutboxEventView view = pendingEvents().stream().filter(found -> found.eventId().equals(notice)).findFirst().orElseThrow();

        assertThat(view.eventType()).isEqualTo("ClientNotified");
        assertThat(view.eventVersion()).isEqualTo(1);
        assertThat(view.tenantId()).isEqualTo(fixture.tenant);
        assertThat(view.aggregateId()).isEqualTo(reservation);
        assertThat(view.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(view.attempts()).isZero();
        assertThat(view.payload()).containsOnlyKeys("customerId", "notificationType", "triggeringEventType", "triggeringEventId");
        assertThat(view.payload().toString()).doesNotContain("@");
        assertThat(relay.pending(1, 100).total()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void anAttemptRecordsTheErrorAndTheNextWaitAndChangesNoReservation() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation);
        String before = businessState(reservation);

        OutboxEventView after = relay.recordAttempt(notice, "The mail provider answered 503");

        assertThat(after.attempts()).isEqualTo(1);
        assertThat(after.lastError()).isEqualTo("The mail provider answered 503");
        assertThat(after.nextAttemptAt()).isAfter(Instant.now());
        assertThat(after.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(pendingIds(100)).doesNotContain(notice);
        assertThat(businessState(reservation)).isEqualTo(before);
    }

    @Test
    void theTwentiethAttemptMarksTheNoticeFailedAndKeepsItVisible() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation);
        String before = businessState(reservation);
        OutboxEventView last = null;
        for (int attempt = 1; attempt <= 20; attempt++) last = relay.recordAttempt(notice, "The mail provider answered 503");

        assertThat(last.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(last.attempts()).isEqualTo(20);
        assertThat(last.nextAttemptAt()).isNull();
        assertThat(fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.outbox_event WHERE outbox_event_id = ? AND failed_at IS NOT NULL",
                Integer.class, notice)).isEqualTo(1);
        assertThat(relay.recordAttempt(notice, "late").attempts()).isEqualTo(20);
        assertThat(businessState(reservation)).isEqualTo(before);
    }

    @Test
    void markingPublishedLeavesThePendingListAndARepeatChangesNothing() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation);

        OutboxEventView first = relay.markPublished(notice);
        OutboxEventView second = relay.markPublished(notice);

        assertThat(first.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(first.publishedAt()).isNotNull();
        assertThat(second.publishedAt()).isEqualTo(first.publishedAt());
        assertThat(pendingIds(100)).doesNotContain(notice);
        assertThat(relay.recordAttempt(notice, "late").attempts()).isZero();
    }

    @Test
    void anErrorIsTruncatedAndKeepsNoAddress() {
        UUID reservation = approvedReservation();
        UUID notice = noticeOf(reservation);

        OutboxEventView after = relay.recordAttempt(notice, "Rejected for ana.perez@example.com " + "x".repeat(900));

        assertThat(after.lastError()).hasSizeLessThanOrEqualTo(500).doesNotContain("@").doesNotContain("ana.perez");
        assertThat(ZoneOffset.UTC).isNotNull();
    }

    // The delivery names the one aggregate each event carries, whichever of the seven reference columns of the table holds it (the CHECK of migration 044 admits exactly one).
    private UUID deliveredAggregateOf(UUID eventId) {
        return new JdbcOutboxDeliveryStore(fixture.jdbc).find(eventId).orElseThrow().aggregateId();
    }

    private UUID insertEvent(String type, String column, UUID aggregate, String extraColumns, String extraValues, String payload) {
        UUID eventId = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.outbox_event (outbox_event_id, tenant_id, event_type, " + column + ", actor_id, occurred_at, payload"
                + extraColumns + ") VALUES (?, ?, ?, ?, ?, now(), ?::jsonb" + extraValues + ")", eventId, fixture.tenant, type, aggregate, fixture.actor, payload);
        return eventId;
    }

    @Test
    void thePaymentParametersChangedEventIsDeliveredWithThePaymentConditionAsAggregate() {
        new JdbcPaymentParameterRepository(fixture.jdbc).save(fixture.tenant, new PaymentParameter(new PaymentParameterValues(PaymentType.DEPOSIT, fixture.service, 24, 48, 3_000, 72,
                true, false), fixture.actor, "Reason", ReservationPersistenceFixture.NOW));
        new JdbcPaymentParametersChangeOutbox(fixture.jdbc).append(new PaymentParametersChange(fixture.tenant, fixture.actor, PaymentType.DEPOSIT, fixture.service,
                ReservationPersistenceFixture.NOW));
        UUID condition = fixture.jdbc.queryForObject("SELECT payment_condition_id FROM commercial_reservation.payment_condition WHERE tenant_id = ? AND service_id = ?", UUID.class,
                fixture.tenant, fixture.service);
        UUID event = fixture.jdbc.queryForObject("SELECT outbox_event_id FROM commercial_reservation.outbox_event WHERE payment_condition_id = ?", UUID.class, condition);

        assertThat(deliveredAggregateOf(event)).isEqualTo(condition);
        assertThat(pendingEvents()).filteredOn(found -> found.eventId().equals(event)).singleElement().extracting(OutboxEventView::aggregateId).isEqualTo(condition);
    }

    @Test
    void theCatalogServiceEventIsDeliveredWithTheServiceAsAggregate() {
        UUID event = insertEvent("CatalogServiceInactivated", "service_id", fixture.service, ", previous_catalog_status, new_catalog_status", ", 'ACTIVE', 'INACTIVE'", null);

        assertThat(deliveredAggregateOf(event)).isEqualTo(fixture.service);
    }

    @Test
    void theEstablishmentEventIsDeliveredWithTheAssociationAsAggregate() {
        UUID association = UUID.randomUUID();
        fixture.jdbc.update("INSERT INTO commercial_reservation.associated_establishment (association_id, tenant_id, establishment_type, commercial_name, commercial_information, "
                + "association_status, created_at, updated_at) VALUES (?, ?, 'HOTEL', 'Hotel Ficticio', 'Informacion ficticia', 'ACTIVE', now(), now())", association, fixture.tenant);
        UUID event = insertEvent("EstablishmentAssociationInactivated", "association_id", association, ", new_catalog_status", ", 'INACTIVE'", null);

        assertThat(deliveredAggregateOf(event)).isEqualTo(association);
    }

    @Test
    void theDiscountRuleEventIsDeliveredWithTheRuleAsAggregate() {
        UUID event = insertEvent("DiscountRuleChanged", "discount_rule_id", fixture.percentageRule, ", new_discount_rule_state", ", '{\"status\": \"ACTIVE\"}'::jsonb", null);

        assertThat(deliveredAggregateOf(event)).isEqualTo(fixture.percentageRule);
    }

    @Test
    void theCustomerEventIsDeliveredWithTheCustomerAsAggregate() {
        UUID event = insertEvent("CustomerRegistered", "customer_id", fixture.customer, "", "", "{\"tenantId\": \"" + fixture.tenant + "\", \"customerId\": \"" + fixture.customer
                + "\", \"actorId\": \"" + fixture.actor + "\", \"occurredAt\": \"2026-11-12T10:00:00Z\"}");

        assertThat(deliveredAggregateOf(event)).isEqualTo(fixture.customer);
    }

    @Test
    void theReservationEventIsDeliveredWithTheReservationAsAggregate() {
        UUID reservation = approvedReservation();

        assertThat(deliveredAggregateOf(noticeOf(reservation))).isEqualTo(reservation);
    }

    @Test
    void thePlatformExceptionalAccessEventIsDeliveredWithTheAuthorizationAsAggregate() {
        UUID authorization = UUID.randomUUID();
        UUID event = insertEvent("PlatformExceptionalAccessUsed", "authorization_id", authorization, "", "", "{\"authorizationId\": \"" + authorization + "\", "
                + "\"platformAdministratorId\": \"" + fixture.actor + "\", \"tenantId\": \"" + fixture.tenant + "\", \"method\": \"GET\", \"route\": \"/x\", \"area\": \"a\", "
                + "\"usedAt\": \"2026-11-12T10:00:00Z\", \"correlationId\": \"c\"}");

        assertThat(deliveredAggregateOf(event)).isEqualTo(authorization);
    }
}
