#!/usr/bin/env bash
# Bütün bazaların (platforma + hər şirkət) ehtiyat nüsxəsi: pg_dump (custom format, sıxılmış).
# Serverdə cron ilə gündə bir dəfə işlədin (docs/deployment.md):
#   0 3 * * * cd /opt/cafe-loyalty && ./scripts/backup.sh >> /var/log/cafe-loyalty-backup.log 2>&1
#
# Nüsxələr BACKUP_DIR qovluğuna yazılır (standart ./backups), BACKUP_KEEP_DAYS gündən köhnələri silinir.
#
# VACİB: telefon nömrələri, çip açarları və TOTP sirləri bazada şifrəlidir. Bərpa üçün .env faylındakı açarlar
# (APP_CRYPTO_KEKS, APP_PHONE_HMAC_KEY və s.) da lazımdır — onları ayrıca, təhlükəsiz yerdə saxlayın.
# Açarlar olmadan nüsxədəki şifrəli məlumatı oxumaq mümkün deyil.
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.prod.yml)
BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
STAMP="$(date -u +%Y-%m-%dT%H%M%SZ)"
TARGET="${BACKUP_DIR}/${STAMP}"

umask 077
mkdir -p "$TARGET"

databases=$("${COMPOSE[@]}" exec -T postgres psql -U postgres -Atc \
  "SELECT datname FROM pg_database WHERE datname = 'loyalty_platform' OR datname LIKE 'loyalty_t_%' ORDER BY 1")

for db in $databases; do
  "${COMPOSE[@]}" exec -T postgres pg_dump -U postgres -Fc --no-owner "$db" > "${TARGET}/${db}.dump"
  echo "$(date -u +%FT%TZ) ${db}: $(du -h "${TARGET}/${db}.dump" | cut -f1)"
done
# Rolların (DB istifadəçiləri) strukturu, şifrələrsiz
"${COMPOSE[@]}" exec -T postgres pg_dumpall -U postgres --roles-only --no-role-passwords > "${TARGET}/roles.sql"

find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime "+${KEEP_DAYS}" -exec rm -rf {} +
echo "$(date -u +%FT%TZ) ehtiyat nüsxə hazırdır: ${TARGET}"

# Bərpa (bir baza üçün nümunə):
#   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
#     pg_restore -U postgres --clean --if-exists -d loyalty_t_<slug> < backups/<tarix>/loyalty_t_<slug>.dump
