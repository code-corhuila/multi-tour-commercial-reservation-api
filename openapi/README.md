# Contract reference copies

openapi.yaml is a reference copy of multi-tour-docs origin/main:07-api/contracts/openapi/commercial-reservation.yaml.
_shared.yaml is a reference copy of multi-tour-docs origin/main:07-api/contracts/openapi/_shared.yaml.
The source documents own the contract; these copies only add provenance headers.

Catalog HTTP tests validate requests and responses against these files.
The coverage gate requires every declared operation, including inactivation and reactivation, to be exercised.

Scope (updated 2026-11-04): openapi.yaml is a partial copy, on purpose. It holds only the operations that
the catalog and the associated-establishment tests validate against it: the health check, the catalog
services and the associated establishments. It does not mirror the contract operations of HU-COM-003 and
HU-COM-004 (`/discount-rules`, `/discount-quotes`, `/reservations/{reservationId}/discount-breakdown`,
`/reservations/{reservationId}/additional-discount` and its preview, `/additional-discount-reasons`) nor
`/catalog/settings` of HU-COM-001. Those operations are validated by their own tests and the contract in
multi-tour-docs is their only source. Adding them here would break `CatalogContract.assertCoverage`, which
requires the declared operations of this file to be exactly the ones the catalog tests exercise.
