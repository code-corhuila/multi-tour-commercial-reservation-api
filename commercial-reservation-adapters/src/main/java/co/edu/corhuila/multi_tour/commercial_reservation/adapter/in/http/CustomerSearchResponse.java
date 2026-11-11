package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerSearchPage;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

// One page of the name search: ordinary data only, and next is always written (null on the last page).
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CustomerSearchResponse(List<CustomerResponse> data, String next) {
    public static CustomerSearchResponse from(CustomerSearchPage page) {
        return new CustomerSearchResponse(page.customers().stream().map(CustomerResponse::from).toList(), page.next());
    }
}
