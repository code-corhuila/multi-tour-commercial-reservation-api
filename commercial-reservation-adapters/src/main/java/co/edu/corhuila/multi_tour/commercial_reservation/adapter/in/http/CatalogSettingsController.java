package co.edu.corhuila.multi_tour.commercial_reservation.adapter.in.http;

import co.edu.corhuila.multi_tour.commercial_reservation.application.port.in.ManageCatalogSettings;
import co.edu.corhuila.multi_tour.commercial_reservation.domain.model.DomainException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// PROPOSED contract GET and PUT /catalog/settings (HU-COM-001 T-05, RF-004). Only the Tenant Administrator reaches it:
// CatalogAuthorization refuses every other role before this class runs.
@RestController
@RequestMapping("/api/v1/commercial-reservation/catalog/settings")
public final class CatalogSettingsController {
    private final ManageCatalogSettings settings;

    public CatalogSettingsController(ManageCatalogSettings settings) {
        this.settings = settings;
    }

    @GetMapping
    public CatalogSettingsResponse read(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal) {
        return CatalogSettingsResponse.from(settings.get(principal.tenantId()));
    }

    @PutMapping
    public CatalogSettingsResponse set(@RequestAttribute(AuthFilter.PRINCIPAL_ATTRIBUTE) AuthenticatedPrincipal principal,
            @RequestBody SetCatalogSettingsRequest request) {
        if (request.collaboratorEditing() == null) throw new DomainException("collaboratorEditing", "is required");
        return CatalogSettingsResponse.from(settings.setCollaboratorEditing(principal.tenantId(),
                CatalogController.actor(principal), request.collaboratorEditing()));
    }
}
