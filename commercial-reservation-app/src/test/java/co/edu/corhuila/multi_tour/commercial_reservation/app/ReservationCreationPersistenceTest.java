package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCatalogServiceRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDepartureStateRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcDiscountRules;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationCreatedOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationReader;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcReservationRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.DiscountRuleActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.TenantRef;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.CreateReservationUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.EvaluateApplicableDiscountRulesUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.InactivateDiscountRuleUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.QuoteDiscountBreakdownUseCase;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservableDeparture;
import co.edu.corhuila.multi_tour.commercial_reservation.application.usecase.ReservationPricing;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureState;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DepartureStatus;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// HU-COM-007 creation run end to end over the real schema with the JDBC adapters that production wires (api tasks T-04, T-11):
// the creation reads the capacity row and never writes it (RN-06, RNF-07, AC-07.9), and the commercial context it fixes does not
// follow the discount rules afterwards (RN-04, AC-07.10; HU-COM-003 AC6). Only the transport fare and the payment options are doubles.
// Every value below is invented.
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ReservationCreationPersistenceTest {
    private static final String DEPARTURES = "SELECT * FROM commercial_reservation.salida_capacity WHERE tenant_id = ? AND salida_id = ?";
    private static final String LINES = "SELECT * FROM commercial_reservation.reservation_fixed_discount "
            + "WHERE reservation_id = ? ORDER BY line_order";

    private ReservationPersistenceFixture fixture;
    private JdbcDiscountRules rules;
    private CreateReservationUseCase create;
    private JdbcReservationReader reader;

    @BeforeEach
    void wireTheCreationWithTheJdbcAdapters() {
        org.junit.jupiter.api.Assumptions.assumeTrue(present("TEST_DATABASE_USER") && present("TEST_DATABASE_PASSWORD"),
                "All three TEST_DATABASE variables are required");
        fixture = new ReservationPersistenceFixture();
        Clock clock = Clock.fixed(ReservationPersistenceFixture.NOW, ZoneOffset.UTC);
        // The second rule chains on the running subtotal; a second rule on the original value cannot be fixed yet (see the report of this test).
        fixture.jdbc.update("UPDATE commercial_reservation.discount_rule SET calculation_base = 'PREVIOUS_SUBTOTAL' "
                + "WHERE discount_rule_id = ?", fixture.fixedRule);
        rules = new JdbcDiscountRules(fixture.jdbc, fixture.transactions);
        var evaluation = new EvaluateApplicableDiscountRulesUseCase(rules);
        var pricing = new ReservationPricing(new QuoteDiscountBreakdownUseCase(evaluation), evaluation,
                (tenant, tour, departure) -> Optional.empty());
        new JdbcDepartureStateRepository(fixture.jdbc).save(new DepartureState(fixture.salida, fixture.tenant, fixture.service,
                ReservationPersistenceFixture.NOW.plusSeconds(86_400), DepartureStatus.SCHEDULED, 10, 7));
        reader = new JdbcReservationReader(fixture.jdbc, fixture.cipher);
        create = new CreateReservationUseCase(identifier -> Optional.of(new TenantRef(fixture.tenant, true)),
                new JdbcCustomerRepository(fixture.jdbc, fixture.cipher),
                new JdbcCatalogServiceRepository(fixture.jdbc, fixture.transactions),
                new ReservableDeparture(new JdbcDepartureReader(fixture.jdbc), clock), pricing,
                (tenant, service, type, method) -> true,
                new JdbcReservationRepository(fixture.jdbc, fixture.cipher, fixture.transactions,
                        new JdbcReservationCreatedOutbox(fixture.jdbc)),
                UUID::randomUUID, clock);
    }

    @Test
    void aCreationReadsTheDepartureAndLeavesItsRowUntouched() {
        Map<String, Object> before = departure();
        assertThat(before.get("held_seats")).isEqualTo(7);

        Reservation created = create.create(command(3));

        assertThat(fixture.count("reservation", created.reservationId())).isEqualTo(1);
        assertThat(departure()).isEqualTo(before);
        assertThat(departure().get("held_seats")).isEqualTo(7);
    }

    @Test
    void notEnoughCapacityCreatesNothingAndLeavesHeldSeatsUntouched() {
        Map<String, Object> before = departure();
        int reservationsBefore = reservationsOfTheCustomer();

        assertThatThrownBy(() -> create.create(command(4))).isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("3 seats");

        assertThat(reservationsOfTheCustomer()).isEqualTo(reservationsBefore);
        assertThat(departure()).isEqualTo(before);
        assertThat(departure().get("held_seats")).isEqualTo(7);
    }

    @Test
    void aRuleChangedExpiredOrInactivatedAfterTheCreationDoesNotChangeTheReservation() {
        Reservation created = create.create(command(3));
        UUID id = created.reservationId();
        assertThat(created.fixedDiscounts()).hasSize(2);
        assertThat(created.finalValue().amount()).isLessThan(created.projectedValue().amount());
        List<Map<String, Object>> linesBefore = fixture.jdbc.queryForList(LINES, id);
        Map<String, Object> rowBefore = fixture.jdbc.queryForMap(
                "SELECT * FROM commercial_reservation.reservation WHERE reservation_id = ?", id);
        Reservation readBefore = reader.findAsCreated(fixture.tenant, id).orElseThrow();

        // The rule is edited and expires, and the other one is inactivated through the use case of HU-COM-003.
        fixture.jdbc.update("UPDATE commercial_reservation.discount_rule SET discount_percentage_bp = 2500, "
                + "ends_on = '2026-01-31' WHERE discount_rule_id = ?", fixture.percentageRule);
        new InactivateDiscountRuleUseCase(rules, rules, rules, Clock.fixed(ReservationPersistenceFixture.NOW.plusSeconds(60),
                ZoneOffset.UTC)).inactivate(new DiscountRuleActor(fixture.tenant, fixture.actor,
                DiscountRuleActor.Role.TENANT_ADMINISTRATOR), fixture.fixedRule);

        assertThat(fixture.jdbc.queryForObject("SELECT status FROM commercial_reservation.discount_rule "
                + "WHERE discount_rule_id = ?", String.class, fixture.fixedRule)).isEqualTo("INACTIVE");
        assertThat(fixture.jdbc.queryForList(LINES, id)).isEqualTo(linesBefore);
        assertThat(fixture.jdbc.queryForMap("SELECT * FROM commercial_reservation.reservation WHERE reservation_id = ?", id))
                .isEqualTo(rowBefore);
        Reservation readAfter = reader.findAsCreated(fixture.tenant, id).orElseThrow();
        assertThat(readAfter.fixedDiscounts()).isEqualTo(readBefore.fixedDiscounts());
        assertThat(readAfter.finalValue()).isEqualTo(readBefore.finalValue());
        assertThat(readAfter.projectedValue()).isEqualTo(readBefore.projectedValue());
        assertThat(reader.findByTenantAndId(fixture.tenant, id).orElseThrow().subtotal()).isEqualTo(created.finalValue());
    }

    private CreateReservationCommand command(int people) {
        return new CreateReservationCommand(fixture.tenant, fixture.actor, fixture.customer, fixture.service, fixture.salida,
                people, "DEPOSIT", "TRANSFER");
    }

    private Map<String, Object> departure() {
        return fixture.jdbc.queryForMap(DEPARTURES, fixture.tenant, fixture.salida);
    }

    private int reservationsOfTheCustomer() {
        return fixture.jdbc.queryForObject("SELECT count(*) FROM commercial_reservation.reservation WHERE tenant_id = ?",
                Integer.class, fixture.tenant);
    }

    private static boolean present(String name) {
        return System.getenv(name) != null;
    }
}
