package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerAuthorization.SENSITIVE_DATA_PERMISSION;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.SensitiveDataAccessed;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// AC-05.9, AC-05.10 · RN-07, RN-08 · CA-030 (T-04).
class ReadSensitiveCustomerDataUseCaseTest {
    private static final UUID CUSTOMER = new UUID(0, 0xc1);
    private static final HighRiskData DATA = new HighRiskData("O+", "Ana 3001112233", "Ninguna", true);

    private final CustomerFakes store = new CustomerFakes();
    private final List<SensitiveDataAccessed> audit = new ArrayList<>();
    private RuntimeException failOnAudit;
    private ReadSensitiveCustomerDataUseCase useCase;

    @BeforeEach
    void setUp() {
        store.save(customer(TENANT, CUSTOMER, DATA));
        useCase = new ReadSensitiveCustomerDataUseCase(store, event -> {
            if (failOnAudit != null) throw failOnAudit;
            audit.add(event);
        }, store, store.clock);
    }

    private static Customer customer(UUID tenant, UUID id, HighRiskData data) {
        return Customer.registerLead(id, tenant, "CC12345", "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo("lead@example.com", "3000000000"), true, NOW, data != null, data);
    }

    private static CustomerActor holder(Role role) {
        return new CustomerActor(TENANT, ACTOR, role, Set.of(SENSITIVE_DATA_PERMISSION));
    }

    @Test
    void sensitiveDataIsDeniedWithoutThePermission() {
        for (Role role : Role.values()) {
            var noClaim = new CustomerActor(TENANT, ACTOR, role, Set.of());
            assertThrows(CustomerAccessDeniedException.class, () -> useCase.read(noClaim, CUSTOMER, null), role.name());
        }
        assertThrows(CustomerAccessDeniedException.class,
                () -> useCase.read(new CustomerActor(TENANT, ACTOR, Role.TENANT_ADMINISTRATOR, Set.of("OTHER")),
                        CUSTOMER, null));
        assertTrue(audit.isEmpty(), "a denied consultation writes no event");
    }

    @Test
    void managerAccountantAndAnalystAreDeniedEvenWithAForgedClaim() {
        for (Role role : EnumSet.of(Role.MANAGER, Role.ACCOUNTANT, Role.ANALYST, Role.FINAL_CUSTOMER)) {
            assertThrows(CustomerAccessDeniedException.class, () -> useCase.read(holder(role), CUSTOMER, null),
                    role.name());
        }
        assertTrue(audit.isEmpty());
    }

    @Test
    void anActorWithoutIdentityIsDenied() {
        assertThrows(CustomerAccessDeniedException.class, () -> useCase.read(null, CUSTOMER, null));
        var noTenant = new CustomerActor(null, ACTOR, Role.TENANT_ADMINISTRATOR, Set.of(SENSITIVE_DATA_PERMISSION));
        assertThrows(CustomerAccessDeniedException.class, () -> useCase.read(noTenant, CUSTOMER, null));
    }

    @Test
    void anAuthorizedConsultationReturnsTheHighRiskData() {
        assertEquals(DATA, useCase.read(holder(Role.TENANT_ADMINISTRATOR), CUSTOMER, null).orElseThrow());
        assertEquals(DATA, useCase.read(holder(Role.OPERATIONAL_COLLABORATOR), CUSTOMER, null).orElseThrow());
    }

    @Test
    void everyAuthorizedConsultationOfSensitiveDataIsAudited() {
        useCase.read(holder(Role.TENANT_ADMINISTRATOR), CUSTOMER, null);
        useCase.read(holder(Role.OPERATIONAL_COLLABORATOR), CUSTOMER, "medical check");

        assertEquals(2, audit.size());
        var first = audit.get(0);
        assertEquals(new SensitiveDataAccessed(TENANT, CUSTOMER, ACTOR, null, NOW), first);
        assertNull(first.reason(), "the reason is optional (DP-11)");
        assertEquals("medical check", audit.get(1).reason());
    }

    @Test
    void theEventCarriesNoSensitiveData() {
        useCase.read(holder(Role.TENANT_ADMINISTRATOR), CUSTOMER, null);
        var text = audit.get(0).toString();
        assertFalse(text.contains("O+") || text.contains("3001112233") || text.contains("Ninguna"), text);
    }

    @Test
    void ifTheAuditFailsNoDataIsReturned() {
        failOnAudit = new IllegalStateException("outbox down");
        assertThrows(IllegalStateException.class,
                () -> useCase.read(holder(Role.TENANT_ADMINISTRATOR), CUSTOMER, null));
        assertTrue(audit.isEmpty());
    }

    @Test
    void aCustomerOfAnotherTenantLooksMissingAndLeavesNoEvent() {
        var foreign = new UUID(0, 0xc2);
        store.save(customer(OTHER_TENANT, foreign, DATA));
        assertThrows(CustomerNotFoundException.class,
                () -> useCase.read(holder(Role.TENANT_ADMINISTRATOR), foreign, null));
        assertThrows(CustomerNotFoundException.class,
                () -> useCase.read(holder(Role.TENANT_ADMINISTRATOR), new UUID(0, 0x99), null));
        assertTrue(audit.isEmpty());
    }

    @Test
    void aCustomerWithoutHighRiskDataIsStillAuditedAndReturnsEmpty() {
        var ordinary = new UUID(0, 0xc3);
        store.save(customer(TENANT, ordinary, null));
        assertTrue(useCase.read(holder(Role.TENANT_ADMINISTRATOR), ordinary, null).isEmpty());
        assertEquals(1, audit.size());
    }
}
