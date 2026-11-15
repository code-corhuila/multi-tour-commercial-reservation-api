package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.OTHER_TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.TENANT;
import static co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.DiscountRuleFakes.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor.Role;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountRule;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ServiceCoverage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// HU-COM-004 quote by tour and date (contract quoteCurrentDiscounts): AC-04.1, AC-04.2, AC-04.10 and the roles of DP-08.
class QuoteTourDiscountsTest {
    private static final LocalDate REF = LocalDate.of(2026, 11, 15);
    private final DiscountRuleFakes fakes = new DiscountRuleFakes();
    private final InMemoryCatalogServiceRepository catalog = new InMemoryCatalogServiceRepository();
    private final QuoteTourDiscountsUseCase quote = new QuoteTourDiscountsUseCase(catalog,
            new QuoteDiscountBreakdownUseCase(new EvaluateApplicableDiscountRulesUseCase(fakes)));
    private final UUID service = new UUID(1, 1);

    private void tour(UUID tenant) {
        catalog.save(CatalogService.register(service, tenant, CatalogServiceType.TOUR, "Tour",
                CommercialTerms.of(35_000_000L, 21_000_000L, 18, true, false), Instant.parse("2026-10-01T00:00:00Z")));
    }

    private void tenPercentRule() {
        fakes.save(DiscountRule.create(new UUID(2, 1), TENANT, "Early", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), 1, DiscountBase.ORIGINAL_VALUE, 1000, null, ServiceCoverage.all(), null,
                Instant.parse("2026-10-01T00:00:00Z")));
    }

    @ParameterizedTest @EnumSource(value = Role.class, names = "OPERATIONAL_COLLABORATOR", mode = EnumSource.Mode.EXCLUDE)
    void everyRoleOfTheQuoteRowGetsTheBreakdown(Role role) {
        tour(TENANT); tenPercentRule();
        var result = quote.quote(actor(role), service, REF, new Cents(100_000));
        assertThat(result.baseValue()).isEqualTo(new Cents(100_000));
        assertThat(result.finalValue()).isEqualTo(new Cents(90_000));
        assertThat(result.lines()).hasSize(1);
    }

    @Test void withoutARuleInForceOnlyTheBaseValueComesBack() {
        tour(TENANT);
        var result = quote.quote(actor(Role.FINAL_CUSTOMER), service, REF, new Cents(100_000));
        assertThat(result.lines()).isEmpty();
        assertThat(result.finalValue()).isEqualTo(result.baseValue());
    }

    @Test void theOperationalCollaboratorIsRefusedAsAPermissionMatter() {
        tour(TENANT);
        assertThatThrownBy(() -> quote.quote(actor(Role.OPERATIONAL_COLLABORATOR), service, REF, new Cents(1)))
                .isInstanceOf(DiscountRuleAccessDeniedException.class);
    }

    @Test void aTourOfAnotherTenantAnswersAsAMissingOne() {
        tour(OTHER_TENANT);
        assertThatThrownBy(() -> quote.quote(actor(Role.TENANT_ADMINISTRATOR), service, REF, new Cents(1)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
        assertThatThrownBy(() -> quote.quote(actor(Role.TENANT_ADMINISTRATOR), UUID.randomUUID(), REF, new Cents(1)))
                .isInstanceOf(CatalogServiceNotFoundException.class);
    }
}
