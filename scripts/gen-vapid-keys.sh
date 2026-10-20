#!/usr/bin/env bash
# Web push üçün VAPID açar cütü (P-256) yaradır və .env-ə əlavə ediləcək iki sətri çap edir.
# İstifadə: scripts/gen-vapid-keys.sh   (açarı dəyişmək bütün abunələri etibarsız edir — bir dəfə yaradın)
set -euo pipefail

b64url() { base64 | tr '+/' '-_' | tr -d '=\n'; }

PEM="$(openssl ecparam -name prime256v1 -genkey -noout)"
# SEC1 DER: 30 77 02 01 01 04 20 <32 bayt şəxsi açar> ...; SPKI DER-in sonu — 65 baytlıq sıxılmamış nöqtə
PRIVATE="$(printf '%s\n' "$PEM" | openssl ec -outform DER 2>/dev/null | tail -c +8 | head -c 32 | b64url)"
PUBLIC="$(printf '%s\n' "$PEM" | openssl ec -pubout -outform DER 2>/dev/null | tail -c 65 | b64url)"

echo "APP_PUSH_VAPID_PUBLIC_KEY=$PUBLIC"
echo "APP_PUSH_VAPID_PRIVATE_KEY=$PRIVATE"
