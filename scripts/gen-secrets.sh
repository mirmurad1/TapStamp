#!/usr/bin/env bash
# .env.example əsasında .env faylı yaradır və boş sirləri kriptoqrafik təsadüfi dəyərlərlə doldurur.
# İstifadə: scripts/gen-secrets.sh [--force]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXAMPLE="$ROOT/.env.example"
TARGET="$ROOT/.env"

if [[ -f "$TARGET" && "${1:-}" != "--force" ]]; then
  echo ".env artıq mövcuddur. Üzərinə yazmaq üçün --force istifadə edin (köhnə sirlər itəcək)." >&2
  exit 1
fi

key() { openssl rand -base64 32; }
password() { openssl rand -base64 24 | tr '+/' '-_' | tr -d '='; }

KEK_ID="kek-$(date +%Y-%m)"
declare -A GENERATED=(
  [POSTGRES_PASSWORD]="$(password)"
  [DB_PLATFORM_OWNER_PASSWORD]="$(password)"
  [DB_PLATFORM_APP_PASSWORD]="$(password)"
  [DB_PROVISIONER_PASSWORD]="$(password)"
  [APP_CRYPTO_KEKS]="${KEK_ID}:$(key)"
  [APP_CRYPTO_ACTIVE_KEK_ID]="$KEK_ID"
  [APP_PHONE_HMAC_KEY]="$(key)"
  [APP_OTP_PEPPER]="$(key)"
  [APP_REDEMPTION_CODE_KEY]="$(key)"
  [APP_DEV_SIMULATOR_KEY]="$(key)"
)

umask 077
: > "$TARGET"
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^([A-Z0-9_]+)=$ && -n "${GENERATED[${BASH_REMATCH[1]}]+x}" ]]; then
    printf '%s=%s\n' "${BASH_REMATCH[1]}" "${GENERATED[${BASH_REMATCH[1]}]}" >> "$TARGET"
  else
    printf '%s\n' "$line" >> "$TARGET"
  fi
done < "$EXAMPLE"

echo ".env yaradıldı: $TARGET"
echo "Bu faylı heç kimlə paylaşmayın və git-ə əlavə etməyin."
