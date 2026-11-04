package co.edu.corhuila.multi_tour.commercial_reservation.application.usecase;

import co.edu.corhuila.multi_tour.commercial_reservation.application.model.CustomerActor;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.RegisterLeadCustomer;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CatalogServiceNotFoundException;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerByEmail;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRegisteredOutbox;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerRepository;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.CustomerTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.IdGenerator;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.ServiceRiskReader;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BirthDateOrAge;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.BusinessRuleViolation;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.ContactInfo;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.Customer;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CustomerRegistered;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public class RegisterLeadCustomerUseCase implements RegisterLeadCustomer {
    private final CustomerRepository repository;
    private final CustomerRegisteredOutbox outbox;
    private final CustomerTransaction transaction;
    private final ServiceRiskReader risk;
    private final CustomerByEmail emails;
    private final IdGenerator ids;
    private final Clock clock;

    public RegisterLeadCustomerUseCase(CustomerRepository repository, CustomerRegisteredOutbox outbox,
            CustomerTransaction transaction, ServiceRiskReader risk, CustomerByEmail emails, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.transaction = transaction;
        this.risk = risk;
        this.emails = emails;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Customer register(CustomerActor actor, Command command) {
        CustomerAuthorization.requireWrite(actor);
        // The tenant is the actor's, never the command's; a service of another tenant looks missing (RN-12).
        boolean highRisk = risk.isHighRisk(actor.tenantId(), command.serviceId())
                .orElseThrow(() -> new CatalogServiceNotFoundException(command.serviceId()));
        Instant now = clock.instant();
        // TODO(RN-10): the clock zone stands for the tenant's official time zone (PDR G14) until it is a port.
        LocalDate today = LocalDate.now(clock);
        Customer lead = Customer.registerLead(ids.newId(), actor.tenantId(), command.documentNumber(),
                command.fullName(), birth(command, today), new ContactInfo(command.email(), command.phone()),
                command.termsAccepted(), now, highRisk, command.highRiskData());
        // AC-05.7 and AC-05.8 (DD-7): an e-mail already in the tenant never creates a second record. The lookup scans and
        // decrypts the customers of the tenant until the -db decides a keyed hash (DP-D5-3); that cost is declared.
        String email = lead.contact().email();
        var match = email == null || email.isBlank() ? Optional.<CustomerByEmail.EmailMatch>empty()
                : emails.findByTenantAndEmail(actor.tenantId(), email);
        if (match.isPresent()) return linkedRecord(actor, email, match.get());
        // The customer and its event commit or roll back together (plan D-10, norm 5.3.11).
        return transaction.inTransaction(() -> {
            repository.save(lead);
            outbox.append(new CustomerRegistered(lead.tenantId(), lead.customerId(), actor.actorId(), now));
            return lead;
        });
    }

    // A record with a verified account is the customer (nothing is created); one without is left to the Tenant Administrator.
    private Customer linkedRecord(CustomerActor actor, String email, CustomerByEmail.EmailMatch match) {
        if (!match.verified()) {
            throw new BusinessRuleViolation("a customer with the e-mail " + email + " is already registered and cannot be linked automatically; "
                    + "the Tenant Administrator must resolve it");
        }
        return repository.findByTenantAndId(actor.tenantId(), match.customerId())
                .orElseThrow(() -> new CustomerNotFoundException(match.customerId()));
    }

    // A birth date wins over an age; with neither, the aggregate names birthDateOrAge as required.
    private static BirthDateOrAge birth(Command command, LocalDate today) {
        if (command.birthDate() != null) return BirthDateOrAge.ofBirthDate(command.birthDate(), today);
        if (command.age() != null) return BirthDateOrAge.ofAge(command.age());
        return null;
    }
}
