#!/bin/sh
# PostgreSQL ilk dəfə işə düşəndə platforma bazasını və DB rollarını yaradır (minimal səlahiyyət prinsipi).
# Şifrələr psql dəyişənləri ilə ötürülür (:'dəyişən' sitatlanması) — SQL inyeksiyası mümkün deyil.
# Tenant bazaları və onların rolları sonradan provisioning servisi ilə yaradılır.
set -eu

: "${DB_PLATFORM_OWNER_PASSWORD:?DB_PLATFORM_OWNER_PASSWORD təyin olunmayıb}"
: "${DB_PLATFORM_APP_PASSWORD:?DB_PLATFORM_APP_PASSWORD təyin olunmayıb}"
: "${DB_PROVISIONER_PASSWORD:?DB_PROVISIONER_PASSWORD təyin olunmayıb}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v owner_password="$DB_PLATFORM_OWNER_PASSWORD" \
    -v app_password="$DB_PLATFORM_APP_PASSWORD" \
    -v provisioner_password="$DB_PROVISIONER_PASSWORD" <<'EOSQL'
-- Platforma sxeminin sahibi: Flyway miqrasiyaları bu rolla işləyir
CREATE ROLE loyalty_platform_owner LOGIN PASSWORD :'owner_password';
-- Tətbiqin platforma bazasındakı rolu: yalnız DML
CREATE ROLE loyalty_platform_app LOGIN PASSWORD :'app_password';
-- Yalnız yeni tenant bazası və rolu yaratmaq üçün (prod-da ayrıca, birdəfəlik əmrlə işlədilməsi tövsiyə olunur)
CREATE ROLE loyalty_provisioner LOGIN CREATEDB CREATEROLE PASSWORD :'provisioner_password';
-- PostgreSQL 16+: yaradılan rollara SET hüququ avtomatik verilsin ki, CREATE DATABASE ... OWNER <rol> işləsin.
-- INHERIT verilmir: provisioner tenant bazalarına qoşula və məlumatlarını oxuya bilməz.
ALTER ROLE loyalty_provisioner SET createrole_self_grant TO 'set';

CREATE DATABASE loyalty_platform OWNER loyalty_platform_owner ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE loyalty_platform FROM PUBLIC;
GRANT CONNECT ON DATABASE loyalty_platform TO loyalty_platform_app;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname loyalty_platform <<'EOSQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO loyalty_platform_owner;
GRANT USAGE ON SCHEMA public TO loyalty_platform_app;
ALTER DEFAULT PRIVILEGES FOR ROLE loyalty_platform_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO loyalty_platform_app;
ALTER DEFAULT PRIVILEGES FOR ROLE loyalty_platform_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO loyalty_platform_app;
EOSQL
