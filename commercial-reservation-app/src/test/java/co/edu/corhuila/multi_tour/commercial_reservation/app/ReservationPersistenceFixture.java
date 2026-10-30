package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.crypto.AesGcmFieldCipher;
import co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence.JdbcCustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Companion;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.HighRiskData;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

// The rows a reservation needs before it can be stored (HU-COM-007 db spec): a customer, a tour and two rules. All values are invented.
final class ReservationPersistenceFixture {
    static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    static final String BLOOD = "ZZ-NEG-FICTICIA";
    static final String COMPANION_NAME = "Acompanante Ficticio Uno";
    static final String COMPANION_DOCUMENT = "DOC-FICT-0002";

    final JdbcTemplate jdbc;
    final TransactionTemplate transactions;
    final AesGcmFieldCipher cipher;
    final UUID tenant = UUID.randomUUID();
    final UUID actor = UUID.randomUUID();
    final UUID customer = UUID.randomUUID();
    final UUID service = UUID.randomUUID();
    final UUID salida = UUID.randomUUID();
    final UUID percentageRule = UUID.randomUUID();
    final UUID fixedRule = UUID.randomUUID();

    ReservationPersistenceFixture() {
        var source = new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
                System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD"));
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("commercial_reservation_app");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        cipher = new AesGcmFieldCipher("test1", Map.of("test1", Base64.getEncoder().encodeToString(key)));
        insertCustomer();
        insertService();
        insertRule(percentageRule, "PERCENTAGE", 1_000, null);
        insertRule(fixedRule, "FIXED_AMOUNT", null, 5_000L);
    }

    // Three people with a tour of 100000 and a transport of 20000 each: 360000, minus 36000, then a rule that took nothing.
    Reservation reservation(UUID id, boolean withTransport, Companion... group) {
        var rates = new ReservedService(service, new Cents(100_000), withTransport ? new Cents(20_000) : null);
        long projected = withTransport ? 360_000 : 300_000;
        long taken = projected / 10;
        var first = new FixedDiscount(1, percentageRule, "Early booking", DiscountType.PERCENTAGE, 1_000, null,
                DiscountBase.ORIGINAL_VALUE, new Cents(projected), new Cents(taken), new Cents(projected - taken), 50_000L);
        var second = new FixedDiscount(2, fixedRule, "Zero amount rule", DiscountType.FIXED_AMOUNT, null, 5_000L,
                DiscountBase.PREVIOUS_SUBTOTAL, new Cents(projected - taken), new Cents(0), new Cents(projected - taken), null);
        return Reservation.create(id, tenant, customer, salida, actor, 3, rates, new Cents(projected),
                new Cents(projected - taken), List.of(first, second), PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW,
                List.of(group));
    }

    // The only line points to a rule that does not exist: the database refuses it after the reservation row was written.
    Reservation reservationWithUnknownRule(UUID id) {
        Reservation good = reservation(id, true);
        var line = good.fixedDiscounts().get(0);
        var unknown = new FixedDiscount(1, UUID.randomUUID(), line.ruleName(), line.type(), line.percentageBasisPoints(), null,
                line.calculationBase(), line.base(), line.amount(), line.subtotal(), line.capCents());
        return Reservation.create(id, tenant, customer, salida, actor, 3, good.service(), good.projectedValue(),
                good.finalValue(), List.of(unknown), PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW);
    }

    Companion plainCompanion() {
        return new Companion(COMPANION_NAME, DocumentNumber.of(COMPANION_DOCUMENT), BirthDateOrAge.ofAge(31), null);
    }

    Companion riskyCompanion() {
        return new Companion("Acompanante Ficticio Dos", DocumentNumber.of("DOC-FICT-0003"),
                BirthDateOrAge.ofBirthDate(LocalDate.parse("1990-05-17"), LocalDate.parse("2026-10-29")),
                new HighRiskData(BLOOD, "Contacto Ficticio 000-555-0199", "Ninguna", true));
    }

    int count(String table, UUID reservationId) {
        return jdbc.queryForObject("SELECT count(*) FROM commercial_reservation." + table + " WHERE reservation_id = ?",
                Integer.class, reservationId);
    }

    private void insertCustomer() {
        new JdbcCustomerRepository(jdbc, cipher).save(Customer.restore(customer, tenant, DocumentNumber.of("DOC-FICT-0001"),
                "Lead Ficticio", BirthDateOrAge.ofAge(40), new ContactInfo("lead@example.invalid", "+000-555-0100"), NOW, NOW,
                null));
    }

    private void insertService() {
        jdbc.update("INSERT INTO commercial_reservation.catalog_service (service_id, tenant_id, service_type, "
                + "commercial_name, catalog_status, base_rate_cents, base_operational_cost_cents, configured_capacity, "
                + "transport_required, high_risk, created_at, updated_at) VALUES (?, ?, 'TOUR', 'Servicio Ficticio', "
                + "'ACTIVE', 100000, 50, 10, false, false, ?, ?)", service, tenant,
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }

    private void insertRule(UUID id, String type, Integer basisPoints, Long amount) {
        jdbc.update("INSERT INTO commercial_reservation.discount_rule (discount_rule_id, tenant_id, name, starts_on, ends_on, "
                + "priority, calculation_base, discount_type, discount_percentage_bp, discount_amount_cents, status, "
                + "covers_all_services, cap_cents, created_at, updated_at) VALUES (?, ?, 'Regla ficticia', '2026-01-01', "
                + "'2026-12-31', ?, 'ORIGINAL_VALUE', ?, ?, ?, 'ACTIVE', true, null, ?, ?)", id, tenant,
                basisPoints != null ? 1 : 2, type, basisPoints, amount, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    }
}
