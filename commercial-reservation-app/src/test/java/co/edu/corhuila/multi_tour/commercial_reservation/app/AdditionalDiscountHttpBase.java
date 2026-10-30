package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Cents;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountBase;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DiscountType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.FixedDiscount;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentMethod;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.PaymentType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Reservation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ReservedService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Shared by the HTTP tests of the additional discount (HU-COM-004): reservations planted in the reservation fake, over the
// same tokens, tenant and tour as the reservation tests.
@Import({CatalogHttpFixture.class, CustomerHttpFixture.class, DiscountRuleHttpFixture.class, ReservationHttpFixture.class,
        AdditionalDiscountHttpFixture.class})
abstract class AdditionalDiscountHttpBase extends ReservationHttpBase {
    static final UUID TEN_PERCENT_RULE = new UUID(2, 1);
    static final String ADDITIONAL = "/additional-discount";
    @Autowired AdditionalDiscountHttpFixture.MemoryAdditionalDiscounts extras;
    @Autowired AdditionalDiscountHttpFixture.MemoryReasons reasons;

    @BeforeEach void clearTheAdditionalDiscountFakes() { extras.clear(); reasons.reasons.clear(); }

    // 300,000 with one fixed line of 10 %: base 300,000, takes 30,000, leaves 270,000.
    UUID reservationWithTenPercentOff(UUID owner) {
        var line = new FixedDiscount(1, TEN_PERCENT_RULE, "Early booking", DiscountType.PERCENTAGE, 1_000, null,
                DiscountBase.ORIGINAL_VALUE, new Cents(300_000), new Cents(30_000), new Cents(270_000), null);
        return plant(owner, new Cents(270_000), List.of(line));
    }

    UUID reservationWithTenPercentOff() { return reservationWithTenPercentOff(tenant); }

    UUID reservationWithoutDiscounts() { return plant(tenant, new Cents(300_000), List.of()); }

    private UUID plant(UUID owner, Cents finalValue, List<FixedDiscount> lines) {
        UUID id = UUID.randomUUID();
        var service = new ReservedService(tour, new Cents(100_000), null);
        store.reservations.put(id, Reservation.create(id, owner, customer, departure, new UUID(3, 3), 3, service,
                new Cents(300_000), finalValue, lines, PaymentType.DEPOSIT, PaymentMethod.TRANSFER, NOW));
        return id;
    }

    MockHttpServletRequestBuilder preview(UUID reservation, String basisPoints, String role) throws Exception {
        return withToken(get(BASE + "/reservations/" + reservation + ADDITIONAL + "/preview"
                + (basisPoints == null ? "" : "?basisPoints=" + basisPoints)), role);
    }
}
