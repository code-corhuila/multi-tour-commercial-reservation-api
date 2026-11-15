package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DeletionResult.Outcome;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RetentionFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.PaymentTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ReservationNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionDeletions;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RetentionFactsReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.AnchorBasis;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationFacts;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservationStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataDeletion;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// T-03 and T-04 of HU-COM-019 (-api): RN-01, RN-05 to RN-07, RN-12; AC-19.1, AC-19.6, AC-19.7, AC-19.9, AC-19.10, AC-19.14, AC-19.16. The service rechecks the year at the call,
// deletes the risk rows of the companions of the reservation and, only when no other reservation of the customer still waits, the risk row of the customer; it writes the
// record in the same transaction and a second call changes nothing. The ports have in-memory doubles here; the schema is checked in the persistence test.
class DeleteSensitiveDataUseCaseTest {

    private static final UUID TENANT = new UUID(19, 1), CUSTOMER = new UUID(19, 2), FIRST = new UUID(19, 3), SECOND = new UUID(19, 4), THIRD = new UUID(19, 5);
    private static final Instant NOW = Instant.parse("2027-11-12T12:00:00Z"), LONG_AGO = NOW.minusSeconds(400L * 86_400), RECENT = NOW.minusSeconds(30L * 86_400);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final Map<UUID, RetentionFacts> facts = new HashMap<>();
    private final Map<UUID, List<UUID>> reservationsOfCustomer = new HashMap<>();
    private final Set<UUID> deleted = new HashSet<>(), customersWithData = new HashSet<>(Set.of(CUSTOMER));
    private final Map<UUID, Integer> companionRows = new HashMap<>();
    private final List<SensitiveDataDeletion> records = new ArrayList<>(), events = new ArrayList<>();
    private int transactions, locks;

    private final RetentionDeletions deletions = new RetentionDeletions() {
        @Override public boolean isDeleted(UUID tenantId, UUID reservationId) { return deleted.contains(reservationId); }
        @Override public List<UUID> otherReservationsOf(UUID tenantId, UUID customerId, UUID reservationId) {
            return reservationsOfCustomer.getOrDefault(customerId, List.of()).stream().filter(id -> !id.equals(reservationId)).toList();
        }
        @Override public void lockCustomer(UUID tenantId, UUID customerId) { locks++; }
        @Override public int deleteCompanionRiskData(UUID tenantId, UUID reservationId) { return companionRows.getOrDefault(reservationId, 0); }
        @Override public void deleteCustomerRiskData(UUID tenantId, UUID customerId) { customersWithData.remove(customerId); }
        @Override public void record(SensitiveDataDeletion deletion) { records.add(deletion); deleted.add(deletion.reservationId()); }
    };
    private final PaymentTransaction transaction = new PaymentTransaction() {
        @Override public <T> T inTransaction(Supplier<T> work) { transactions++; return work.get(); }
    };
    private final RetentionFactsReader readerOfTheFacts = new RetentionFactsReader() {
        @Override public Optional<RetentionFacts> factsOf(UUID tenantId, UUID reservationId) { return Optional.ofNullable(facts.get(reservationId)); }
        @Override public List<UUID> dueCandidates(UUID tenantId, Instant now, int limit, UUID after) { return List.of(); }
    };
    private final DeleteSensitiveDataUseCase useCase = new DeleteSensitiveDataUseCase(
            readerOfTheFacts, (tenant, id) -> Optional.empty(), deletions, events::add, transaction, Clock.fixed(NOW, ZoneOffset.UTC));

    private void reservation(UUID id, Instant serviceEnd) {
        facts.put(id, new RetentionFacts(CUSTOMER, BOGOTA, serviceEnd == null ? new DepartureFacts(DepartureStatus.SCHEDULED, null, null)
                : new DepartureFacts(DepartureStatus.FINALIZED, serviceEnd, serviceEnd), new ReservationFacts(ReservationStatus.COMPLETED, null, false)));
        reservationsOfCustomer.computeIfAbsent(CUSTOMER, key -> new ArrayList<>()).add(id);
    }

    @Test
    void theDeletionRemovesTheRiskRowsOfTheCompanionsAndOfTheCustomerAndWritesTheRecord() {
        reservation(FIRST, LONG_AGO);
        companionRows.put(FIRST, 3);

        DeletionResult result = useCase.delete(TENANT, FIRST);

        assertThat(result).isEqualTo(new DeletionResult(Outcome.DELETED, 3, true));
        assertThat(customersWithData).isEmpty();
        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.reservationId()).isEqualTo(FIRST);
            assertThat(record.customerId()).isEqualTo(CUSTOMER);
            assertThat(record.anchorAt()).isEqualTo(LONG_AGO);
            assertThat(record.basis()).isEqualTo(AnchorBasis.SERVICE_END);
            assertThat(record.deletedAt()).isEqualTo(NOW);
            assertThat(record.customerDataDeleted()).isTrue();
            assertThat(record.companionRowsDeleted()).isEqualTo(3);
        });
        assertThat(transactions).isEqualTo(1);
        assertThat(locks).isEqualTo(1);
    }

    @Test
    void theServiceRechecksTheYearAtTheCallAndNothingIsDeletedBeforeIt() {
        reservation(FIRST, RECENT);
        reservation(SECOND, null);

        assertThat(useCase.delete(TENANT, FIRST).outcome()).isEqualTo(Outcome.NOT_DUE);
        assertThat(useCase.delete(TENANT, SECOND).outcome()).isEqualTo(Outcome.NO_ANCHOR);

        assertThat(records).isEmpty();
        assertThat(customersWithData).containsExactly(CUSTOMER);
        assertThat(transactions).isZero();
    }

    @Test
    void aSecondDeletionChangesNothingAndEmitsNothing() {
        reservation(FIRST, LONG_AGO);
        useCase.delete(TENANT, FIRST);

        DeletionResult again = useCase.delete(TENANT, FIRST);

        assertThat(again).isEqualTo(new DeletionResult(Outcome.ALREADY_DELETED, 0, false));
        assertThat(records).hasSize(1);
        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void customerDataWaitsForTheLastReservationOfTheCustomer() {
        reservation(FIRST, LONG_AGO);
        reservation(SECOND, RECENT);
        reservation(THIRD, null);

        DeletionResult first = useCase.delete(TENANT, FIRST);

        assertThat(first.customerDataDeleted()).isFalse();
        assertThat(customersWithData).containsExactly(CUSTOMER);
        assertThat(records.get(0).customerDataDeleted()).isFalse();

        facts.put(SECOND, facts.get(FIRST));
        assertThat(useCase.delete(TENANT, SECOND).customerDataDeleted()).isFalse();
        facts.put(THIRD, facts.get(FIRST));
        assertThat(useCase.delete(TENANT, THIRD).customerDataDeleted()).isTrue();
        assertThat(customersWithData).isEmpty();
    }

    // T-06 / AC-19.8, RN-11: one event per deletion, written in the same transaction, with the facts of the record and no value; nothing is emitted when nothing is deleted.
    @Test
    void theDeletionWritesTheEventOnceInTheSameTransaction() {
        reservation(FIRST, LONG_AGO);
        reservation(SECOND, RECENT);

        useCase.delete(TENANT, SECOND);
        useCase.delete(TENANT, FIRST);
        useCase.delete(TENANT, FIRST);

        assertThat(events).containsExactlyElementsOf(records);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.reservationId()).isEqualTo(FIRST);
            assertThat(event.basis()).isEqualTo(AnchorBasis.SERVICE_END);
            assertThat(event.companionRowsDeleted()).isZero();
        });
        assertThat(transactions).isEqualTo(1);
    }

    @Test
    void aReservationOfAnotherTenantOrMissingIsNotFound() {
        assertThatThrownBy(() -> useCase.delete(TENANT, UUID.randomUUID())).isInstanceOf(ReservationNotFoundException.class);
        assertThat(records).isEmpty();
    }
}
