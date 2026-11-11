package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CreateReservationCommand;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// The inline holder of a reservation (contract CreateReservationRequest.holder): the lead is built and checked first, written
// only when the reservation is ready, so a refusal stores nothing. It runs inside the transaction of the caller, which also
// holds the reservation and its event; the tenant and the actor come from the command, never from the holder.
final class ReservationHolder {
    // The lead of the reservation and whether it still has to be written.
    record Lead(Customer customer, boolean isNew) {}

    private final CustomerRepository customers;
    private final CustomerRegisteredOutbox outbox;
    private final CustomerByEmail emails;
    private final Clock clock;

    ReservationHolder(CustomerRepository customers, CustomerRegisteredOutbox outbox, CustomerByEmail emails, Clock clock) {
        this.customers = customers;
        this.outbox = outbox;
        this.emails = emails;
        this.clock = clock;
    }

    // The risk mark is the tour's, read from the catalog by the caller (RN-17); the terms are a datum of the holder (RN-CLI-001).
    Lead prepare(CreateReservationCommand command, CatalogService tour, UUID newId) {
        RegisterLeadCustomer.Command holder = command.holder();
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Customer built = Customer.registerLead(newId, command.tenantId(), holder.documentNumber(), holder.fullName(),
                birth(holder, today), new ContactInfo(holder.email(), holder.phone()), holder.termsAccepted(), now,
                tour.terms().highRisk(), holder.highRiskData());
        String email = built.contact().email();
        var match = email == null || email.isBlank() ? java.util.Optional.<CustomerByEmail.EmailMatch>empty()
                : emails.findByTenantAndEmail(command.tenantId(), email);
        if (match.isEmpty()) return new Lead(command.ownerAccountId() == null ? built : built.linkedTo(command.ownerAccountId()), true);
        // AC-05.7: a verified account links the existing customer. AC-05.8 and DP-05 leave an unverified match undecided, so it is
        // refused; a Final Customer never takes the record of another e-mail owner.
        if (!match.get().verified() || command.ownerAccountId() != null) {
            throw new BusinessRuleViolation("a customer with the e-mail " + email + " is already registered and cannot be linked automatically; "
                    + "the Tenant Administrator must resolve it");
        }
        Customer linked = customers.findByTenantAndId(command.tenantId(), match.get().customerId())
                .orElseThrow(() -> new CustomerNotFoundException(match.get().customerId()));
        return new Lead(linked, false);
    }

    void write(Lead lead, UUID actorId) {
        if (!lead.isNew()) return;
        customers.save(lead.customer());
        outbox.append(new CustomerRegistered(lead.customer().tenantId(), lead.customer().customerId(), actorId, clock.instant()));
    }

    private static BirthDateOrAge birth(RegisterLeadCustomer.Command holder, LocalDate today) {
        if (holder.birthDate() != null) return BirthDateOrAge.ofBirthDate(holder.birthDate(), today);
        if (holder.age() != null) return BirthDateOrAge.ofAge(holder.age());
        return null;
    }
}
