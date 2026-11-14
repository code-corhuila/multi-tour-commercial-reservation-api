# Contract reference copies

`openapi.yaml` is a reference copy of `multi-tour-docs` `origin/main:07-api/contracts/openapi/commercial-reservation.yaml`, with all the
operations of the service. `_shared.yaml` is a reference copy of `origin/main:07-api/contracts/openapi/_shared.yaml`.
The source documents own the contract; these copies only add a provenance comment on the second line. A change of the contract is made in
`multi-tour-docs` first and the copy is refreshed here (ADR-004 D7).

How the tests use them (`commercial-reservation-app/src/test`):
- `ContractConformance` validates the response of every exchange of the HTTP tests against this contract, and fails on a 2xx or 3xx answer of a
  route that the contract does not declare. `/test-support` and the 4xx answers of routes or methods the contract does not declare (the intentional
  negative tests) are not validated. The validator is set to ignore `additionalProperties`, because swagger-request-validator injects it into every
  branch of an `allOf` and rejects valid responses (a known limitation of the library).
- `ContractCoverageTest` runs after every other class and fails if an operation of the contract was not exercised. The two lists that leave an
  operation out, each with its reason, are `NOT_IMPLEMENTED` and `DATABASE_ONLY` (operations whose tests need PostgreSQL, which `ci.yml` runs in its
  own step).
- `CatalogContract` keeps the request gate of the catalog services and of the health check, and `EstablishmentHttpTest` the one of the establishments.
- To list every violation instead of failing, run the tests with `-Dcontract.report=<file>`.
