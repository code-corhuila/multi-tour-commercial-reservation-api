# multi-tour-commercial-reservation-api

## What it is, and what it is not

The HTTP API of the commercial-reservation domain of Multitour (Java 21, Spring Boot 3.5.x),
organised as three Maven modules: `commercial-reservation-core` (domain and use cases, no
framework), `commercial-reservation-adapters` (HTTP and persistence) and
`commercial-reservation-app` (composition root and configuration).

It is not a database owner: the schema and its migrations live in
`multi-tour-commercial-reservation-db`, and nothing here creates tables.

## How to start it

Requires Java 21 and Maven 3.9.

```bash
cp .env.example .env     # then fill in the values; secrets have no default
mvn -B -pl commercial-reservation-app -am package
java -jar commercial-reservation-app/target/commercial-reservation-app-0.1.0-SNAPSHOT.jar
curl http://localhost:8080/health
```

Export the variables of `.env` in your shell first; the application does not read the file itself.
Set `DB_URL` to a PostgreSQL JDBC URL and provide `DB_USER` and `DB_PASSWORD` for the domain user.
Set `JWT_PUBLIC_KEY` (PEM with literal `\n`) or `JWT_PUBLIC_KEY_FILE` (an RSA public-key file).
Startup requires a valid key; the inline value takes precedence when both variables are set.

## Run with Docker

`deploy/compose.yml` is the platform one (no database, `expose` 8083, network `platform`), included by `multi-tour-infra-postgres`; the file below is the standalone stand-in.

Clone `multi-tour-commercial-reservation-db` next to this repository (its Liquibase runner applies the
schema). Needs Docker and openssl; nothing else is installed.

```bash
cp .env.example .env     # fill in: passwords, JWT_PUBLIC_KEY='<PEM with literal \n>', field keys
openssl genrsa -out dev-private.pem 2048 && openssl rsa -in dev-private.pem -pubout -out dev-public.pem
# FIELD_ENCRYPTION_CURRENT_KEY_ID=k1   FIELD_ENCRYPTION_KEYS=k1=$(openssl rand -base64 32)
docker compose --env-file .env -f deploy/compose.standalone.yml up -d --build   # postgres 18, db runner, api
curl http://localhost:8083/health                                    # 200 {"status":"ok",...}
docker compose --env-file .env -f deploy/compose.standalone.yml down -v
```

The image is `multi-tour-commercial-reservation-api:dev`: multi-stage (Maven, then a JRE 21 Alpine), runs as
user 10001, healthcheck on `GET /health`, configured only by environment variables. In the platform
`multi-tour-infra-postgres` replaces the local `postgres` service.

## Where its data is

In the shared PostgreSQL instance of `multi-tour-infra-postgres`, in this domain's own schema,
created by the `-db` repository. This service connects with its domain user, never an administrator.
The JDBC adapter stores catalog items, status events in the outbox and tenant-scoped idempotency keys.

## How to test it

The contract reference copies and complete operation coverage are documented in [openapi/README.md](openapi/README.md).

```bash
mvn -B verify
```

Without Maven installed: `docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify`.
Startup tests use a fictitious JDBC URL and never open a connection. Integration tests require a real
PostgreSQL database with the schema migrated by `multi-tour-commercial-reservation-db`.
Export `TEST_DATABASE_URL` (a JDBC URL), `TEST_DATABASE_USER` (`commercial_reservation_app`) and
`TEST_DATABASE_PASSWORD`, then run `mvn -B verify`. Integration tests are skipped if any variable is missing.
When using Docker, pass all three variables with `-e` and use a URL reachable from its network.
The application user cannot delete catalog items; the schema separately allows technical-table cleanup.

## Development tokens

`scripts/dev-token.sh` prints an RS256 JWT with the claims the API verifies (`sub`, `tenantId`, `roles`,
optional `permissions`). **Development only**: never use it outside a local machine. The repository holds no key.

```bash
openssl genrsa -out dev-private.pem 2048                       # *.pem is git-ignored
openssl rsa -in dev-private.pem -pubout -out dev-public.pem
export JWT_PUBLIC_KEY_FILE=$PWD/dev-public.pem                 # then start the API
TOKEN=$(scripts/dev-token.sh --key dev-private.pem --tenant 00000000-0000-0000-0000-000000000123 \
  --sub 00000000-0000-0000-0000-000000000456 --roles TENANT_ADMINISTRATOR --permissions catalog:read --ttl 3600)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/commercial-reservation/catalog/services
```

Requires bash and openssl. `DevTokenScriptTest` runs it against the repository verifier.

## What is missing

The HTTP foundation provides token validation, correlation and a common error envelope.
Catalog routes provide idempotent creation, reading by identifier, paginated management listing and partial updates.
GET /api/v1/commercial-reservation/catalog/services accepts page, limit, catalogStatus and serviceType; PATCH /api/v1/commercial-reservation/catalog/services/{serviceId} is administrator-only.
Publishing outbox events belongs to the worker and future internal API routes.
