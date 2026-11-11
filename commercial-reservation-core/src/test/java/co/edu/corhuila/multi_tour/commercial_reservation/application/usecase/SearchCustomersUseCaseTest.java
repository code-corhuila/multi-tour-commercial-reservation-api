package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.ACTOR;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.NOW;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CustomerFakes.TENANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerNameSearch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The search of customers by name for the picker of HU-COM-007 (api spec 005, F-18 amended 2026-11-09). Every value is invented.
class SearchCustomersUseCaseTest {
    private final Directory directory = new Directory();
    private SearchCustomersUseCase useCase;

    // The port as the contract says it: the tenant and the lower-case prefix are given, the order is name then id.
    static final class Directory implements CustomerNameSearch {
        final List<Customer> all = new ArrayList<>();
        int reads, lastLimit;
        UUID lastTenant;
        String lastPrefix;

        @Override public List<Customer> findByNamePrefix(UUID tenantId, String lowerPrefix, After after, int limit) {
            reads++; lastTenant = tenantId; lastPrefix = lowerPrefix; lastLimit = limit;
            return all.stream().filter(found -> found.tenantId().equals(tenantId))
                    .filter(found -> found.fullName().toLowerCase().startsWith(lowerPrefix))
                    .sorted(Comparator.comparing((Customer found) -> found.fullName().toLowerCase()).thenComparing(Customer::customerId))
                    .filter(found -> after == null || found.fullName().toLowerCase().compareTo(after.lowerName()) > 0
                            || (found.fullName().toLowerCase().equals(after.lowerName()) && found.customerId().compareTo(after.customerId()) > 0))
                    .limit(limit).toList();
        }
    }

    private static Customer person(long id, UUID tenant, String name) {
        return Customer.registerLead(new UUID(0, id), tenant, "DOC-" + id, name, BirthDateOrAge.ofAge(30),
                new ContactInfo("p" + id + "@example.invalid", "3000000000"), true, NOW);
    }

    @BeforeEach
    void setUp() {
        directory.all.add(person(1, TENANT, "Persona Ficticia Uno"));
        directory.all.add(person(2, TENANT, "Persona Ficticia Dos"));
        directory.all.add(person(3, TENANT, "Persona Ficticia Tres"));
        directory.all.add(person(4, TENANT, "Otra Persona"));
        directory.all.add(person(5, OTHER_TENANT, "Persona De Otro Tenant"));
        useCase = new SearchCustomersUseCase(directory);
    }

    private static CustomerActor actor(Role role) {
        return new CustomerActor(TENANT, ACTOR, role);
    }

    @Test
    void administratorCollaboratorManagerAndAnalystSearch() {
        for (Role role : EnumSet.of(Role.TENANT_ADMINISTRATOR, Role.OPERATIONAL_COLLABORATOR, Role.MANAGER, Role.ANALYST)) {
            assertEquals(3, useCase.search(actor(role), "persona f", null, null).customers().size());
        }
    }

    @Test
    void accountantAndFinalCustomerAreRefusedBeforeAnyRead() {
        for (Role role : EnumSet.of(Role.ACCOUNTANT, Role.FINAL_CUSTOMER)) {
            assertThrows(CustomerAccessDeniedException.class, () -> useCase.search(actor(role), "persona", null, null));
        }
        assertThrows(CustomerAccessDeniedException.class, () -> useCase.search(null, "persona", null, null));
        assertEquals(0, directory.reads);
    }

    @Test
    void theSearchOfAnotherTenantNeverReachesTheCustomersOfThisOne() {
        var found = useCase.search(actor(Role.MANAGER), "persona", null, null).customers();
        assertTrue(found.stream().allMatch(customer -> customer.tenantId().equals(TENANT)));
        assertEquals(TENANT, directory.lastTenant);
        var other = useCase.search(new CustomerActor(OTHER_TENANT, ACTOR, Role.MANAGER), "persona", null, null).customers();
        assertEquals(1, other.size());
    }

    @Test
    void aTextOfFewerThanTwoCharactersAfterTrimmingIsRefusedNamingTheFieldAndNothingIsRead() {
        for (String text : new String[] {null, "", " ", "p", "  p  "}) {
            var refusal = assertThrows(DomainException.class, () -> useCase.search(actor(Role.MANAGER), text, null, null));
            assertEquals("search", refusal.field());
        }
        assertEquals(0, directory.reads);
    }

    @Test
    void aTextOfMoreThanOneHundredCharactersIsRefused() {
        var refusal = assertThrows(DomainException.class,
                () -> useCase.search(actor(Role.MANAGER), "a".repeat(101), null, null));
        assertEquals("search", refusal.field());
        assertEquals(0, directory.reads);
    }

    @Test
    void theTextIsTrimmedAndLowerCasedBeforeTheNameIsCompared() {
        var found = useCase.search(actor(Role.MANAGER), "  PERSONA fic ", null, null).customers();
        assertEquals("persona fic", directory.lastPrefix);
        assertEquals(3, found.size());
    }

    @Test
    void theLimitIsTwentyByDefaultCappedAtFiftyAndRefusedBelowOne() {
        useCase.search(actor(Role.MANAGER), "persona", null, null);
        assertEquals(21, directory.lastLimit); // one more than asked, to know whether a next page exists
        useCase.search(actor(Role.MANAGER), "persona", 500, null);
        assertEquals(51, directory.lastLimit);
        for (int bad : new int[] {0, -1}) {
            var refusal = assertThrows(DomainException.class, () -> useCase.search(actor(Role.MANAGER), "persona", bad, null));
            assertEquals("limit", refusal.field());
        }
    }

    @Test
    void theNextPageContinuesWithoutRepeatingAndTheLastOneHasNoCursor() {
        var first = useCase.search(actor(Role.MANAGER), "persona f", 2, null);
        assertEquals(2, first.customers().size());
        assertNotNull(first.next());
        var second = useCase.search(actor(Role.MANAGER), "persona f", 2, first.next());
        assertEquals(1, second.customers().size());
        assertNull(second.next());
        var seen = new ArrayList<UUID>();
        first.customers().forEach(customer -> seen.add(customer.customerId()));
        second.customers().forEach(customer -> seen.add(customer.customerId()));
        assertEquals(3, seen.stream().distinct().count());
    }

    @Test
    void aCursorNotIssuedByTheServiceIsRefusedNamingTheField() {
        for (String cursor : new String[] {"not-a-cursor", "%%%%", "AAAA"}) {
            var refusal = assertThrows(DomainException.class, () -> useCase.search(actor(Role.MANAGER), "persona", 5, cursor));
            assertEquals("cursor", refusal.field());
        }
    }
}
