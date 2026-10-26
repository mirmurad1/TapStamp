#!/usr/bin/env bash
# Dev domenləri üçün lokal sertifikat mərkəzi (CA) və TLS sertifikatları yaradır.
# Windows-da Git Bash ilə işə salın: bash scripts/gen-dev-certs.sh
# Brauzer xəbərdarlığı olmasın deyə yaradılan dev-ca.pem faylını etibarlı kimi əlavə edin (README-yə bax).
# Yeni şirkətin dev domeni üçün: bash scripts/gen-dev-certs.sh yeni.localhost (yalnız göstərilən domenlər yaradılır)
# Alternativ: mkcert (https://github.com/FiloSottile/mkcert) — eyni qovluq strukturu ilə.
set -euo pipefail
trap 'echo "Xəta: sertifikat yaradıla bilmədi (sətir $LINENO). Ətraflı: bash -x scripts/gen-dev-certs.sh" >&2' ERR

# Git Bash (MSYS) "/CN=..." arqumentini fayl yoluna çevirir; "//CN=..." isə toxunulmadan "/CN=..." kimi ötürülür
case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) SUBJ_PREFIX="//" ;;
  *) SUBJ_PREFIX="/" ;;
esac

if [[ $# -gt 0 ]]; then
  DOMAINS=("$@")
else
  DOMAINS=("mockcoffee.localhost" "demo-cay.localhost" "platform.localhost" "site.localhost" "www.site.localhost")
fi
CERT_DIR="$(cd "$(dirname "$0")/.." && pwd)/infra/nginx/certs"
mkdir -p "$CERT_DIR"
umask 077

if [[ ! -f "$CERT_DIR/dev-ca.key" ]]; then
  openssl req -x509 -new -nodes -newkey rsa:2048 -sha256 -days 825 \
    -subj "${SUBJ_PREFIX}CN=TapStamp Dev CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -keyout "$CERT_DIR/dev-ca.key" -out "$CERT_DIR/dev-ca.pem" 2>/dev/null
  echo "Dev CA yaradıldı: $CERT_DIR/dev-ca.pem"
fi

for domain in "${DOMAINS[@]}"; do
  dir="$CERT_DIR/$domain"
  mkdir -p "$dir"
  ext="$dir/ext.cnf"
  printf 'subjectAltName=DNS:%s\nbasicConstraints=CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\n' "$domain" > "$ext"
  openssl req -new -nodes -newkey rsa:2048 -subj "${SUBJ_PREFIX}CN=$domain" \
    -keyout "$dir/privkey.pem" -out "$dir/request.csr" 2>/dev/null
  openssl x509 -req -in "$dir/request.csr" -CA "$CERT_DIR/dev-ca.pem" -CAkey "$CERT_DIR/dev-ca.key" \
    -CAcreateserial -days 397 -sha256 -extfile "$ext" -out "$dir/cert.pem" 2>/dev/null
  cat "$dir/cert.pem" "$CERT_DIR/dev-ca.pem" > "$dir/fullchain.pem"
  rm -f "$dir/request.csr" "$ext" "$dir/cert.pem"
  echo "Sertifikat yaradıldı: $domain"
done
