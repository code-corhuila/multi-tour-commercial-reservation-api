package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerAccountLink;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByAccount;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ProcessedEvents;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.TenantEventTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DocumentNumber;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

// The customers of the tenants, their processed event ids and the transaction around both: a failure restores both.
final class AccountLinkFakes implements CustomerByAccount, CustomerByEmail, CustomerAccountLink, ProcessedEvents, TenantEventTransaction {
    static final UUID TENANT = new UUID(0, 0x31), OTHER_TENANT = new UUID(0, 0x32);

    // The use case reads only the tenant, the e-mail and the account of a record.
    private record Row(UUID tenant, UUID id, String email, UUID account) {}

    private Map<UUID, Row> rows = new HashMap<>();
    final Set<UUID> processed = new HashSet<>();
    RuntimeException failAfterRecord;

    UUID addCustomer(UUID tenant, String email, UUID account) {
        UUID id = UUID.randomUUID();
        rows.put(id, new Row(tenant, id, email, account));
        return id;
    }

    UUID accountOf(UUID customerId) { return rows.get(customerId).account(); }

    int count() { return rows.size(); }

    @Override public Optional<Customer> findByTenantAndAccount(UUID tenantId, UUID accountId) {
        return rows.values().stream().filter(r -> r.tenant().equals(tenantId) && accountId.equals(r.account())).findFirst()
                .map(r -> Customer.restore(r.id(), r.tenant(), DocumentNumber.of("1234567"), "Ana Perez", null,
                        new ContactInfo(r.email(), null), Instant.EPOCH, Instant.EPOCH, null, r.account()));
    }

    @Override public Optional<EmailMatch> findByTenantAndEmail(UUID tenantId, String normalizedEmail) {
        return rows.values().stream().filter(r -> r.tenant().equals(tenantId) && normalizedEmail.equals(r.email())).findFirst()
                .map(r -> new EmailMatch(r.id(), r.account() != null));
    }

    @Override public boolean linkIfUnlinked(UUID tenantId, UUID customerId, UUID accountId) {
        Row row = rows.get(customerId);
        if (row == null || !row.tenant().equals(tenantId) || row.account() != null) return false;
        rows.put(customerId, new Row(row.tenant(), row.id(), row.email(), accountId));
        return true;
    }

    @Override public boolean wasProcessed(UUID eventId) { return processed.contains(eventId); }

    @Override public void record(UUID eventId) {
        processed.add(eventId);
        if (failAfterRecord != null) throw failAfterRecord;
    }

    @Override public <T> T inTransaction(Supplier<T> work) {
        Map<UUID, Row> before = new HashMap<>(rows);
        Set<UUID> processedBefore = new HashSet<>(processed);
        try {
            return work.get();
        } catch (RuntimeException failure) {
            rows = before;
            processed.clear(); processed.addAll(processedBefore);
            throw failure;
        }
    }
}
