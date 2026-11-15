#!/bin/sh
# Local stand-in for what multi-tour-infra-postgres provides (Annex J.7): the pgcrypto extension and
# the application user that the db runner expects. The runner (admin user) creates the schema and grants.
# Runs once, on the first start of an empty data volume. Credentials come from the environment.
set -eu
psql -v ON_ERROR_STOP=1 -v app_password="$COMMERCIAL_RESERVATION_APP_PASSWORD" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE ROLE commercial_reservation_app LOGIN PASSWORD :'app_password';
SQL
