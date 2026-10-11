package co.edu.corhuila.multi_tour.commercial_reservation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogServiceType;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTerms;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = TestJwtKeys.class)
@Import(CatalogHttpFixture.class)
class CatalogCapacityHttpTest {
    @Autowired MockMvc mvc;
    @Autowired CatalogHttpFixture.MemoryCatalog catalog;

    @Test void capacityBelowHeldSeatsAnswers422AndLeavesTheItemIntact() throws Exception {
        UUID tenant = UUID.randomUUID();
        var item = CatalogService.register(UUID.randomUUID(), tenant, CatalogServiceType.TOUR, "HTTP capacity tour",
                CommercialTerms.of(100L, 50L, 18, false, false), TestJwtKeys.NOW);
        catalog.save(item);
        catalog.held.put(item.serviceId(), List.of(5, 12));
        String token = CatalogHttpTest.token(tenant.toString(), "TENANT_ADMINISTRATOR");
        String path = CatalogHttpTest.BASE + "/" + item.serviceId();
        var refused = mvc.perform(patch(path).header("Authorization", "Bearer " + token)
                .header("X-Correlation-Id", CatalogHttpTest.TRACE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuredCapacity\":10}")).andReturn();
        status().isUnprocessableEntity().match(refused);
        new CatalogContract().validate(refused, false);
        CatalogHttpTest.assertError(refused, "BUSINESS_RULE_VIOLATION",
                "The capacity cannot be lower than the seats already held in a scheduled departure");
        var unchanged = mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
        status().isOk().match(unchanged);
        new CatalogContract().validate(unchanged, false);
        assertThat(CatalogHttpTest.json(unchanged).get("configuredCapacity").intValue()).isEqualTo(18);
        assertThat(item.updatedAt()).isEqualTo(TestJwtKeys.NOW);
        assertThat(item.pullEvents()).isEmpty();
        var accepted = mvc.perform(patch(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"configuredCapacity\":12}")).andReturn();
        status().isOk().match(accepted);
        new CatalogContract().validate(accepted, false);
        assertThat(CatalogHttpTest.json(accepted).get("configuredCapacity").intValue()).isEqualTo(12);
        catalog.held.remove(item.serviceId());
    }
}
