# Contract reference copies

openapi.yaml is a reference copy of specs/Commercial and Reservations/commercial-reservation.yaml.
_shared.yaml is a reference copy of multi-tour-docs origin/main:07-api/contracts/openapi/_shared.yaml.
The source documents own the contract; these copies only add provenance headers.

Catalog HTTP tests validate requests and responses against these files.
The coverage gate explicitly defers listCatalogServices, updateCatalogService,
inactivateCatalogService and reactivateCatalogService to the following branches.
