package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The general read of a person: ordinary data only, never the risk data (plan D-08), by role (RN-09, AC-05.11).
class GetCustomerUseCaseTest {
    private static final UUID CUSTOMER = new UUID(0, 0xc1);
    private final CustomerFakes store = new CustomerFakes();
    private GetCustomerUseCase useCase;

    @BeforeEach
    void setUp() {
        var data = new HighRiskData("O+", "Ana 3001112233", "Ninguna", true);
        store.save(Customer.registerLead(CUSTOMER, TENANT, "CC12345", "Lead Person", BirthDateOrAge.ofAge(30),
                new ContactInfo("lead@example.com", "3000000000"), true, NOW, true, data));
        useCase = new GetCustomerUseCase(store);
    }

    private static CustomerActor actor(Role role) {
        return new CustomerActor(TENANT, ACTOR, role);
    }

    @Test
    void administratorCollaboratorManagerAndAnalystReadTheOrdinaryData() {
        for (Role role : EnumSet.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ANALYST)) {
            var found = useCase.get(actor(role), CUSTOMER);
            assertEquals("Lead Person", found.fullName());
            assertEquals("CC12345", found.documentNumber().value());
        }
    }

    @Test
    void theGeneralReadNeverCarriesTheRiskDataAndNeverAsksForIt() {
        var found = useCase.get(actor(Role.TENANT_ADMINISTRATOR), CUSTOMER);
        assertNull(found.highRiskData());
        assertEquals(0, store.fullReads);
        assertEquals(1, store.profileReads);
    }

    @Test
    void accountantAndFinalCustomerAreRefusedBeforeAnyRead() {
        for (Role role : EnumSet.of(Role.ACCOUNTANT, Role.FINAL_CUSTOMER)) {
            assertThrows(CustomerAccessDeniedException.class, () -> useCase.get(actor(role), CUSTOMER));
        }
        assertEquals(0, store.profileReads);
    }

    @Test
    void aClaimDoesNotOpenTheGeneralReadToTheAccountant() {
        var forged = new CustomerActor(TENANT, ACTOR, Role.ACCOUNTANT, Set.of(CustomerAuthorization.SENSITIVE_DATA_PERMISSION));
        assertThrows(CustomerAccessDeniedException.class, () -> useCase.get(forged, CUSTOMER));
    }

    @Test
    void aCustomerOfAnotherTenantAndAnUnknownOneLookMissing() {
        var foreign = new CustomerActor(OTHER_TENANT, ACTOR, Role.TENANT_ADMINISTRATOR);
        assertThrows(CustomerNotFoundException.class, () -> useCase.get(foreign, CUSTOMER));
        assertThrows(CustomerNotFoundException.class, () -> useCase.get(actor(Role.TENANT_ADMINISTRATOR), UUID.randomUUID()));
    }
}
