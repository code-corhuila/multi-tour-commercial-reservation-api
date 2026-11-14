package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateCommercialTerms;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.UpdateDescriptiveInformation;
import co.edu.corhuila.multi_tour.commercial_reservation.application.port.out.RequestTransaction;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CatalogService;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.CommercialTermsChange;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import java.util.UUID;

public final class TransactionalCatalogUpdate {
    private final UpdateDescriptiveInformation description;
    private final UpdateCommercialTerms terms;
    private final RequestTransaction transactions;

    public TransactionalCatalogUpdate(UpdateDescriptiveInformation description, UpdateCommercialTerms terms,
            RequestTransaction transactions) {
        this.description = description;
        this.terms = terms;
        this.transactions = transactions;
    }

    public CatalogService update(UUID tenantId, UUID serviceId, UpdateCatalogServiceRequest request) {
        if (request.commercialName == null && !request.hasTerms()) {
            throw new DomainException("body", "at least one field is required");
        }
        // Both intents must roll back together when the aggregate rejects the second change.
        return transactions.inTransaction(() -> {
            CatalogService result = null;
            if (request.commercialName != null) {
                result = description.update(tenantId, serviceId, request.commercialName);
            }
            if (request.hasTerms()) {
                result = terms.update(tenantId, serviceId, new CommercialTermsChange(request.baseRateCents,
                        request.baseOperationalCostCents, request.configuredCapacity,
                        request.transportRequired, request.highRisk, request.transportRatePerPersonCents));
            }
            return result;
        });
    }
}
