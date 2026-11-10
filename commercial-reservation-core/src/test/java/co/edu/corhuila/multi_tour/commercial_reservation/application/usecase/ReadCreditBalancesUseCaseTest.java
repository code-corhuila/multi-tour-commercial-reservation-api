package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditBalanceView;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreditSummary;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.PaymentActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.RefundPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CreditBalanceQueries;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditApplication;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditBalance;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CreditOrigin;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FinancialDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Audit M1 (HU-COM-017 AC-17.13, AC-17.16, DP-05) for ReadCreditBalancesUseCase, with two customers and two tenants and no database: a Final Customer is resolved from
// their account to their customer record and that record becomes the condition of every query, whatever customerId they send; an unlinked account sees an empty list and
// a missing credit; staff reads every customer of its own tenant only; the summary is for staff.
class ReadCreditBalancesUseCaseTest {

    private static final UUID TENANT = new UUID(17, 1), OTHER_TENANT = new UUID(17, 2), ACCOUNT_ONE = new UUID(17, 11), ACCOUNT_TWO = new UUID(17, 12),
            UNLINKED_ACCOUNT = new UUID(17, 13), CUSTOMER_ONE = new UUID(17, 21), CUSTOMER_TWO = new UUID(17, 22), FOREIGN_CUSTOMER = new UUID(17, 23),
            CREDIT_ONE = new UUID(17, 31), CREDIT_TWO = new UUID(17, 32), FOREIGN_CREDIT = new UUID(17, 33), STAFF = new UUID(17, 41);
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    // Same conditions as the SQL of the JDBC queries: the tenant always, the customer when it is given.
    private static final class Queries implements CreditBalanceQueries {
        final List<CreditBalanceView> rows = new ArrayList<>();
        final List<String> calls = new ArrayList<>();

        @Override public RefundPage<CreditBalanceView> page(UUID tenantId, UUID customerId, boolean availableOnly, int page, int limit) {
            calls.add("page:" + tenantId + ":" + customerId);
            var found = rows.stream().filter(v -> v.credit().tenantId().equals(tenantId) && (customerId == null || v.customerId().equals(customerId))).toList();
            return new RefundPage<>(found, found.size());
        }
        @Override public Optional<CreditBalanceView> find(UUID tenantId, UUID customerId, UUID creditBalanceId) {
            calls.add("find:" + tenantId + ":" + customerId);
            return rows.stream().filter(v -> v.credit().id().equals(creditBalanceId) && v.credit().tenantId().equals(tenantId)
                    && (customerId == null || v.customerId().equals(customerId))).findFirst();
        }
        @Override public List<CreditApplication> applications(UUID tenantId, UUID creditBalanceId) { return List.of(); }
        @Override public List<FinancialDecision> refundRequests(UUID tenantId, UUID creditBalanceId) { return List.of(); }
        @Override public CreditSummary summary(UUID tenantId) { calls.add("summary:" + tenantId); return new CreditSummary(1, 1, 0); }
    }

    private final Queries queries = new Queries();
    private final List<Customer> customers = new ArrayList<>();
    private final ReadCreditBalancesUseCase read = new ReadCreditBalancesUseCase(queries,
            (tenant, account) -> customers.stream().filter(c -> c.tenantId().equals(tenant) && account.equals(c.accountId())).findFirst());

    ReadCreditBalancesUseCaseTest() {
        customers.add(customer(CUSTOMER_ONE, TENANT, "CC1", ACCOUNT_ONE));
        customers.add(customer(CUSTOMER_TWO, TENANT, "CC2", ACCOUNT_TWO));
        // The same account number linked in another tenant: the tenant of the actor decides which record is resolved.
        customers.add(customer(FOREIGN_CUSTOMER, OTHER_TENANT, "CC3", ACCOUNT_ONE));
        queries.rows.add(view(CREDIT_ONE, TENANT, CUSTOMER_ONE));
        queries.rows.add(view(CREDIT_TWO, TENANT, CUSTOMER_TWO));
        queries.rows.add(view(FOREIGN_CREDIT, OTHER_TENANT, FOREIGN_CUSTOMER));
    }

    private static Customer customer(UUID id, UUID tenant, String document, UUID account) {
        return Customer.registerLead(id, tenant, document, "Persona " + document, BirthDateOrAge.ofAge(30), new ContactInfo(document + "@example.invalid", "3000000000"), true, NOW)
                .linkedTo(account);
    }

    private static CreditBalanceView view(UUID id, UUID tenant, UUID customer) {
        return new CreditBalanceView(new CreditBalance(id, tenant, new UUID(17, 90), CreditOrigin.CUSTOMER_CANCELLATION, new Cents(100_000), new UUID(17, 91), null, NOW, STAFF,
                new Cents(0), new Cents(0)), customer);
    }

    private static PaymentActor customerOf(UUID tenant, UUID account) { return new PaymentActor(tenant, account, Role.FINAL_CUSTOMER); }

    @Test
    void aFinalCustomerListsOnlyTheCreditOfTheirOwnCustomer() {
        var page = read.page(customerOf(TENANT, ACCOUNT_ONE), null, false, 1, 20);

        assertThat(page.items()).extracting(v -> v.credit().id()).containsExactly(CREDIT_ONE);
        assertThat(page.total()).isEqualTo(1);
        assertThat(queries.calls).containsExactly("page:" + TENANT + ":" + CUSTOMER_ONE);
    }

    @Test
    void theCustomerIdAFinalCustomerSendsIsIgnoredSoTheyCannotListTheCreditOfAnotherCustomer() {
        var page = read.page(customerOf(TENANT, ACCOUNT_ONE), CUSTOMER_TWO, false, 1, 20);

        assertThat(page.items()).extracting(v -> v.credit().id()).containsExactly(CREDIT_ONE);
        assertThat(queries.calls).containsExactly("page:" + TENANT + ":" + CUSTOMER_ONE);
    }

    @Test
    void anAccountWithoutACustomerRecordSeesAnEmptyListAndNothingIsQueried() {
        var page = read.page(customerOf(TENANT, UNLINKED_ACCOUNT), null, false, 1, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(queries.calls).isEmpty();
    }

    @Test
    void aFinalCustomerReadsTheDetailOfTheirOwnCredit() {
        var detail = read.detail(customerOf(TENANT, ACCOUNT_TWO), CREDIT_TWO);

        assertThat(detail.view().credit().id()).isEqualTo(CREDIT_TWO);
        assertThat(queries.calls).containsExactly("find:" + TENANT + ":" + CUSTOMER_TWO);
    }

    @Test
    void theCreditOfAnotherCustomerOfTheSameTenantLooksMissingToAFinalCustomer() {
        assertThatThrownBy(() -> read.detail(customerOf(TENANT, ACCOUNT_ONE), CREDIT_TWO)).isInstanceOf(CreditBalanceNotFoundException.class);
        assertThatThrownBy(() -> read.detail(customerOf(TENANT, ACCOUNT_TWO), CREDIT_ONE)).isInstanceOf(CreditBalanceNotFoundException.class);
    }

    @Test
    void theCreditOfAnotherTenantLooksMissingAndTheAccountIsResolvedInTheTenantOfTheActor() {
        // ACCOUNT_ONE is linked in both tenants; in OTHER_TENANT it resolves to FOREIGN_CUSTOMER, so CREDIT_ONE of TENANT stays hidden and the own one is shown.
        assertThatThrownBy(() -> read.detail(customerOf(OTHER_TENANT, ACCOUNT_ONE), CREDIT_ONE)).isInstanceOf(CreditBalanceNotFoundException.class);
        assertThat(read.detail(customerOf(OTHER_TENANT, ACCOUNT_ONE), FOREIGN_CREDIT).view().credit().id()).isEqualTo(FOREIGN_CREDIT);
        assertThatThrownBy(() -> read.detail(customerOf(TENANT, ACCOUNT_ONE), FOREIGN_CREDIT)).isInstanceOf(CreditBalanceNotFoundException.class);
    }

    @Test
    void aMissingCreditAndAnUnlinkedAccountAnswerTheSameWay() {
        assertThatThrownBy(() -> read.detail(customerOf(TENANT, UNLINKED_ACCOUNT), CREDIT_ONE)).isInstanceOf(CreditBalanceNotFoundException.class);
        assertThatThrownBy(() -> read.detail(customerOf(TENANT, ACCOUNT_ONE), new UUID(17, 99))).isInstanceOf(CreditBalanceNotFoundException.class);
        assertThat(queries.calls).containsExactly("find:" + TENANT + ":" + CUSTOMER_ONE);
    }

    @Test
    void staffReadsEveryCustomerOfItsOwnTenantAndNothingOfAnotherTenant() {
        PaymentActor staff = new PaymentActor(TENANT, STAFF, Role.TENANT_ADMINISTRATOR);

        assertThat(read.page(staff, null, false, 1, 20).items()).extracting(v -> v.credit().id()).containsExactlyInAnyOrder(CREDIT_ONE, CREDIT_TWO);
        assertThat(read.page(staff, CUSTOMER_TWO, false, 1, 20).items()).extracting(v -> v.credit().id()).containsExactly(CREDIT_TWO);
        assertThat(read.detail(staff, CREDIT_TWO).view().credit().id()).isEqualTo(CREDIT_TWO);
        assertThatThrownBy(() -> read.detail(staff, FOREIGN_CREDIT)).isInstanceOf(CreditBalanceNotFoundException.class);
    }

    @Test
    void theSummaryIsForStaffOfTheirTenantAndRefusedToAFinalCustomer() {
        assertThat(read.summary(new PaymentActor(TENANT, STAFF, Role.ACCOUNTANT)).availableCents()).isEqualTo(1);
        assertThat(queries.calls).containsExactly("summary:" + TENANT);

        assertThatThrownBy(() -> read.summary(customerOf(TENANT, ACCOUNT_ONE))).isInstanceOf(PaymentAccessDeniedException.class);
        assertThat(queries.calls).hasSize(1);
    }
}
