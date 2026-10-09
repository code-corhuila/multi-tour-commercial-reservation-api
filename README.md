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
Until the persistence adapter exists, `DB_URL`, `DB_USER` and `DB_PASSWORD` are declared but unused.
Set `JWT_PUBLIC_KEY` (PEM with literal `\n`) or `JWT_PUBLIC_KEY_FILE` (an RSA public-key file).
Startup requires a valid key; the inline value takes precedence when both variables are set.

## Where its data is

In the shared PostgreSQL instance of `multi-tour-infra-postgres`, in this domain's own schema,
created by the `-db` repository. This service connects with its domain user, never an administrator.
No data is read or written yet.

## How to test it

```bash
mvn -B verify
```

Without Maven installed: `docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify`.
Integration tests that need PostgreSQL will read `TEST_DATABASE_URL` and be skipped when it is unset.

## What is missing

The HTTP foundation provides token validation, correlation and a common error envelope.
Catalog endpoints, persistence and the outbox are not implemented.
The pool, connection-wait and statement limits are declared but take effect only once a datasource exists.
