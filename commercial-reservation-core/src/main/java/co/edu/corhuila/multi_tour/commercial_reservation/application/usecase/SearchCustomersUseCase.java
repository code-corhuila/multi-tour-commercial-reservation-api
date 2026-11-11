package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerSearchPage;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.SearchCustomers;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerNameSearch;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// The name search of the picker of HU-COM-007 (api spec 005, F-18 amended 2026-11-09). Neither the text nor a name is logged here.
public class SearchCustomersUseCase implements SearchCustomers {
    static final int MIN_TEXT = 2, MAX_TEXT = 100, DEFAULT_LIMIT = 20, MAX_LIMIT = 50;
    private static final int UUID_LENGTH = 36;
    private final CustomerNameSearch directory;

    public SearchCustomersUseCase(CustomerNameSearch directory) {
        this.directory = directory;
    }

    @Override
    public CustomerSearchPage search(CustomerActor actor, String text, Integer limit, String cursor) {
        CustomerAuthorization.requireRead(actor);
        String prefix = text == null ? "" : text.trim();
        if (prefix.length() < MIN_TEXT || prefix.length() > MAX_TEXT) {
            throw new DomainException("search", "search must have " + MIN_TEXT + " to " + MAX_TEXT + " characters");
        }
        if (limit != null && limit < 1) throw new DomainException("limit", "limit must be at least 1");
        int size = Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT);
        // The tenant is the actor's, never the request's (RN-12). One more row tells whether a next page exists.
        List<Customer> rows = directory.findByNamePrefix(actor.tenantId(), prefix.toLowerCase(Locale.ROOT), after(cursor), size + 1);
        if (rows.size() <= size) return new CustomerSearchPage(rows, null);
        List<Customer> page = rows.subList(0, size);
        return new CustomerSearchPage(page, encode(page.get(size - 1)));
    }

    private static String encode(Customer last) {
        String plain = last.fullName().toLowerCase(Locale.ROOT) + "|" + last.customerId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    private static CustomerNameSearch.After after(String cursor) {
        if (cursor == null || cursor.isEmpty()) return null;
        try {
            String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int cut = plain.length() - UUID_LENGTH - 1;
            if (cut < 0 || plain.charAt(cut) != '|') throw new IllegalArgumentException();
            return new CustomerNameSearch.After(plain.substring(0, cut), UUID.fromString(plain.substring(cut + 1)));
        } catch (IllegalArgumentException malformed) {
            throw new DomainException("cursor", "cursor is not valid");
        }
    }
}
