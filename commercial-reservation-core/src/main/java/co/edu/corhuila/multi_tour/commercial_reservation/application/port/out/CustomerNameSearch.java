package co.edu.corhuila.multi_tour.commercial_reservation.application.port.out;

import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import java.util.List;
import java.util.UUID;

// The search of the customers of one tenant by the start of the name. The prefix arrives trimmed and in lower case; an
// implementation compares it as plain text (no wildcard), orders by lower-case name then id, and returns the ordinary
// data only: it must not load nor decrypt the risk data.
public interface CustomerNameSearch {
    // The rows that follow the cursor, at most limit; after is null for the first page.
    List<Customer> findByNamePrefix(UUID tenantId, String lowerPrefix, After after, int limit);

    // The position of the last row of the previous page.
    record After(String lowerName, UUID customerId) {}
}
