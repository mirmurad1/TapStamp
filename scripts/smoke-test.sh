#!/usr/bin/env bash
# docker compose ilə qaldırılmış mühitin tez yoxlanması: TLS, domenə görə tenant seçimi, təhlükəsizlik başlıqları.
# İstifadə: bash scripts/smoke-test.sh   (HTTPS_PORT standart olaraq 443)
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CA="$ROOT/infra/nginx/certs/dev-ca.pem"
PORT="${HTTPS_PORT:-443}"
FAILED=0

# Windows curl (Schannel) lokal CA üçün CRL tapa bilmir; ləğv yoxlaması yalnız bu dev CA üçün söndürülür
CURL_OPTS=(--cacert "$CA")
curl --version | grep -q Schannel && CURL_OPTS+=(--ssl-no-revoke)

url() { echo "https://$1:$PORT$2"; }
pass() { echo "  ✔ $1"; }
fail() { echo "  ✘ $1"; FAILED=1; }

check_status() { # ad host yol gözlənilən_status
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' "${CURL_OPTS[@]}" "$(url "$2" "$3")")
  [[ "$code" == "$4" ]] && pass "$1 ($code)" || fail "$1: gözlənilən $4, alınan $code"
}

check_body() { # ad host yol mətn
  local body
  body=$(curl -s "${CURL_OPTS[@]}" "$(url "$2" "$3")")
  [[ "$body" == *"$4"* ]] && pass "$1" || fail "$1: cavabda '$4' yoxdur"
}

echo "Sağlamlıq və marşrutlar"
check_body "health UP" mockcoffee.localhost /actuator/health '"UP"'
check_status "digər actuator endpoint-ləri bağlıdır" mockcoffee.localhost /actuator/env 404
check_body "müştəri tətbiqi HTML" mockcoffee.localhost / '<div id="root">'
check_body "admin paneli (dərin link)" mockcoffee.localhost /admin/customers '<div id="root">'

echo "Domenə görə tenant seçimi"
# Şirkətin adını sahib Parametrlərdən dəyişə bilər, ona görə hər domenin öz cavabı və bir-birindən fərqi yoxlanılır
MOCK=$(curl -s "${CURL_OPTS[@]}" "$(url mockcoffee.localhost /api/v1/public/company)")
CAY=$(curl -s "${CURL_OPTS[@]}" "$(url demo-cay.localhost /api/v1/public/company)")
[[ "$MOCK" == *'"branches"'* && "$MOCK" != *'Demo Çay Evi'* ]] && pass "mockcoffee öz məlumatını görür" \
  || fail "mockcoffee öz məlumatını görmür"
[[ "$CAY" == *'Demo Çay Evi'* && "$CAY" != "$MOCK" ]] && pass "Demo Çay Evi öz məlumatını görür" \
  || fail "Demo Çay Evi öz məlumatını görmür"
check_body "platforma domeni tenant deyil" platform.localhost /api/v1/context '"PLATFORM"'
check_status "platforma domenində şirkət məlumatı yoxdur" platform.localhost /api/v1/public/company 404

echo "Admin girişi"
check_body "sessiyasız vəziyyət NONE-dur" mockcoffee.localhost /api/v1/admin/auth/session '"NONE"'
check_status "admin API sessiyasız bağlıdır" mockcoffee.localhost /api/v1/admin/me 401
check_status "platforma API şirkət domenində mövcud deyil" mockcoffee.localhost /api/v1/platform/tenants 404
check_status "platforma API sessiyasız bağlıdır" platform.localhost /api/v1/platform/tenants 401
check_status "şirkət API-si platforma domenində mövcud deyil" platform.localhost /api/v1/admin/me 404

echo "Müştəri girişi"
check_body "müştəri sessiyası yoxdur" mockcoffee.localhost /api/v1/auth/session '"NONE"'
check_status "müştəri profili sessiyasız bağlıdır" mockcoffee.localhost /api/v1/me 401
check_body "hüquqi sənədlər açıqdır" mockcoffee.localhost /api/v1/public/legal '"PRIVACY_POLICY"'
check_status "müştəri API-si platforma domenində mövcud deyil" platform.localhost /api/v1/auth/session 404

echo "NFC toxunuşu"
check_location() { # ad host yol gözlənilən_location
  local location
  location=$(curl -s -o /dev/null -w '%{redirect_url}' "${CURL_OPTS[@]}" "$(url "$2" "$3")")
  [[ "$location" == *"$4" ]] && pass "$1" || fail "$1: gözlənilən $4, alınan $location"
}
check_location "yanlış formatlı toxunuş xəta səhifəsinə yönəlir" mockcoffee.localhost "/t/04A1B2C3D4E5F1?picc=00&cmac=00" "/tap/error?code=TAP_MALFORMED"
check_location "saxta imza ümumi kodla rədd edilir" mockcoffee.localhost \
  "/t/04A1B2C3D4E5F2?picc=00000000000000000000000000000000&cmac=0000000000000000" "/tap/error?code=TAP_SIGNATURE_INVALID"
check_location "başqa şirkətin çipi tanınmır" demo-cay.localhost \
  "/t/04A1B2C3D4E5F2?picc=00000000000000000000000000000000&cmac=0000000000000000" "/tap/error?code=TAG_UNKNOWN"
headers=$(curl -s -D - -o /dev/null "${CURL_OPTS[@]}" "$(url mockcoffee.localhost '/t/04A1B2C3D4E5F1?picc=00&cmac=00')")
grep -qi "^referrer-policy: no-referrer" <<<"$headers" && pass "toxunuş yönləndirməsi: no-referrer" || fail "toxunuş yönləndirməsində no-referrer yoxdur"
grep -qi "^cache-control: no-store" <<<"$headers" && pass "toxunuş yönləndirməsi: no-store" || fail "toxunuş yönləndirməsində no-store yoxdur"
check_status "naməlum toxunuş tokeni" mockcoffee.localhost /api/v1/taps/unknown-token 410
check_status "möhür sessiyasız alınmır" mockcoffee.localhost /api/v1/me/gift-cards 401
check_body "toxunuş səhifəsi (SPA)" mockcoffee.localhost /tap/error '<div id="root">'

echo "TLS qatı"
code=$(curl -s -o /dev/null -w '%{http_code}' "${CURL_OPTS[@]}" -H "Host: demo-cay.localhost" "$(url mockcoffee.localhost /api/v1/public/company)")
[[ "$code" == "421" ]] && pass "Host və SNI uyğunsuzluğu rədd edilir (421)" || fail "Host/SNI uyğunsuzluğu: alınan $code"
if curl -s -o /dev/null "${CURL_OPTS[@]}" --connect-to "unknown.localhost:$PORT:127.0.0.1:$PORT" "$(url unknown.localhost /)"; then
  fail "tanınmayan domenlə TLS bağlantısı qurulmamalı idi"
else
  pass "tanınmayan domenlə TLS bağlantısı qurulmur"
fi
code=$(curl -s -o /dev/null -w '%{http_code}' "http://mockcoffee.localhost:${HTTP_PORT:-80}/")
[[ "$code" == "301" ]] && pass "HTTP → HTTPS yönləndirmə" || fail "HTTP yönləndirmə: alınan $code"

echo "Təhlükəsizlik başlıqları (HTML)"
headers=$(curl -s -D - -o /dev/null "${CURL_OPTS[@]}" "$(url mockcoffee.localhost /)")
for header in "content-security-policy: default-src 'self'" "x-content-type-options: nosniff" \
              "x-frame-options: DENY" "referrer-policy: strict-origin-when-cross-origin" \
              "permissions-policy: camera=()" "cross-origin-opener-policy: same-origin"; do
  grep -qi "^$header" <<<"$headers" && pass "$header" || fail "başlıq yoxdur: $header"
done
grep -qi "^server: nginx/" <<<"$headers" && fail "nginx versiyası açıqlanır" || pass "server versiyası gizlədilib"

echo
if [[ $FAILED -eq 0 ]]; then echo "Bütün yoxlamalar keçdi."; else echo "Bəzi yoxlamalar uğursuz oldu."; fi
exit $FAILED
