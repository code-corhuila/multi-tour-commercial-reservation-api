package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssociatedEstablishmentReadsTest {
    private final UUID tenant = new UUID(0, 1), actor = new UUID(0, 2);
    private final Instant now = Instant.parse("2026-10-11T10:00:00Z");
    private final InMemoryAssociatedEstablishmentRepository repository = new InMemoryAssociatedEstablishmentRepository();

    @Test void inactivatedAssociationLeavesPromotionalConsultationsAndKeepsItsHistory() {
        var item = item(3, EstablishmentType.HOTEL, now); repository.save(item);
        assertThat(promotional().total()).isEqualTo(1);
        item.inactivate(actor, now.plusSeconds(1)); repository.save(item);
        assertThat(promotional().items()).isEmpty();
        var inactive = repository.findByTenantAndId(tenant, item.associationId()).orElseThrow();
        assertThat(inactive.associationStatus()).isEqualTo(AssociationStatus.INACTIVE);
        assertThat(inactive.commercialName()).isEqualTo(item.commercialName());
        assertThat(inactive.commercialInformation()).isEqualTo(item.commercialInformation());
        assertThat(inactive.imageReference()).isEqualTo(item.imageReference());
        assertThat(inactive.createdAt()).isEqualTo(now);
        assertThat(repository.outbox).hasSize(2);
    }

    @Test void reactivationReturnsItAndTheAuditRecordsWhoAndWhen() {
        var item = item(3, EstablishmentType.HOTEL, now); repository.save(item);
        item.inactivate(actor, now.plusSeconds(1)); repository.save(item);
        UUID reactivator = new UUID(0, 4);
        item.reactivate(reactivator, now.plusSeconds(2)); repository.save(item);
        assertThat(promotional().items()).extracting(AssociatedEstablishment::associationId).containsExactly(item.associationId());
        assertThat(repository.outbox.get(2)).isEqualTo(
                new EstablishmentAssociationReactivated(tenant, item.associationId(), reactivator, now.plusSeconds(2)));
    }

    @Test void managementListingFiltersTypeAndStatusAndPaginatesNewestFirst() {
        var oldest = item(3, EstablishmentType.HOTEL, now); repository.save(oldest);
        var newest = item(4, EstablishmentType.HOTEL, now.plusSeconds(1)); repository.save(newest);
        var restaurant = item(5, EstablishmentType.RESTAURANT, now.plusSeconds(2)); repository.save(restaurant);
        var first = repository.listByTenant(tenant, Optional.empty(), Optional.of(EstablishmentType.HOTEL), 1, 1);
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.items()).extracting(AssociatedEstablishment::associationId).containsExactly(newest.associationId());
        assertThat(repository.listByTenant(tenant, Optional.empty(), Optional.of(EstablishmentType.HOTEL), 2, 1).items())
                .extracting(AssociatedEstablishment::associationId).containsExactly(oldest.associationId());
        oldest.inactivate(actor, now.plusSeconds(3)); repository.save(oldest);
        assertThat(repository.listByTenant(tenant, Optional.of(AssociationStatus.INACTIVE), Optional.empty(), 1, 20).total()).isEqualTo(1);
    }

    private co.edu.corhuila.multi_tour.commercial_reservation.application.model.EstablishmentPage promotional() {
        return repository.listByTenant(tenant, Optional.of(AssociationStatus.ACTIVE), Optional.empty(), 1, 20);
    }
    private AssociatedEstablishment item(long id, EstablishmentType type, Instant time) {
        return AssociatedEstablishment.register(new UUID(0, id), tenant, type, "Sample establishment",
                "Contact details", "opaque-reference", actor, time);
    }
}
