package co.edu.corhuila.multi_tour.commercial_reservation.adapter.out.persistence;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

// The text literal of a uuid array, bound as one parameter and read as "= ANY (?::uuid[])": a batch read costs one statement
// whatever the number of identifiers. Only UUIDs are written into it, so it carries no injected text.
final class SqlUuidArray {
    private SqlUuidArray() {
    }

    static String of(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }
}
