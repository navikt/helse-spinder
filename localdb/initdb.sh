#!/bin/bash
set -e

export POSTGRES_USER=${POSTGRES_USER:-postgres}

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE "helse-spinder";
	CREATE USER spinder PASSWORD '${POSTGRES_PASSWORD_SPINDER:-spinder}';
	GRANT ALL PRIVILEGES ON DATABASE "helse-spinder" TO spinder;
EOSQL
