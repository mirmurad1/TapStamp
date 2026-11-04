#!/bin/sh
# Prod: hər şirkət domeni və platforma domeni üçün Let's Encrypt sertifikatı (HTTP-01, webroot).
#  * Domen siyahısı backend-in daxili endpoint-indən gəlir və tez-tez (POLL_SECONDS, standart 30 s) yoxlanılır:
#    panelə yeni domen əlavə ediləndə sertifikat bir dəqiqə ərzində alınır — restart lazım deyil.
#  * Paneldən silinmiş domenin sertifikatı avtomatik silinir (/certs və certbot): nginx sertifikatı olmayan domenlə
#    TLS bağlantısı qurmur, yəni köhnə domen dərhal bağlanır.
#  * Sertifikatı alınmayan domen (məs. DNS hələ yönəlməyib) RETRY_SECONDS-dan (standart 10 dəq) tez yenidən
#    sınanmır — Let's Encrypt-in uğursuz cəhd limitinə (saatda 5) düşməmək üçün.
#  * Mövcud sertifikatlar RENEW_SECONDS-da (standart 1 saat) bir dəfə "certbot renew" ilə yenilənir.
#  * Fayllar nginx-in oxuduğu yerə kopyalanır: /certs/<domen>/{fullchain,privkey}.pem. nginx onları hər bağlantıda
#    oxuyur ($ssl_server_name), reload lazım deyil.
#  * Vəziyyət backend-ə göndərilir (ISSUED / FAILED) — platforma panelində görünür.
set -u

: "${ACME_EMAIL:?ACME_EMAIL təyin olunmalıdır}"
: "${APP_INTERNAL_TOKEN:?APP_INTERNAL_TOKEN təyin olunmalıdır}"
POLL="${CERTBOT_POLL_SECONDS:-30}"
RETRY="${CERTBOT_RETRY_SECONDS:-600}"
RENEW="${CERTBOT_INTERVAL_SECONDS:-3600}"
STATE=/tmp/certbot-state
mkdir -p "$STATE/failed"

valid() {
  case "$1" in
    ""|*[!a-z0-9.-]*|.*|*..*) return 1 ;;
  esac
  return 0
}

copy_cert() {
  live="/etc/letsencrypt/live/$1"
  mkdir -p "/certs/$1"
  cp -L "${live}/fullchain.pem" "/certs/$1/fullchain.pem.tmp" && mv "/certs/$1/fullchain.pem.tmp" "/certs/$1/fullchain.pem"
  cp -L "${live}/privkey.pem" "/certs/$1/privkey.pem.tmp" && mv "/certs/$1/privkey.pem.tmp" "/certs/$1/privkey.pem"
  # nginx worker "nginx" istifadəçisidir (rəsmi image-də gid 101): açar yalnız root və nginx qrupu üçün oxunandır
  chown 0:101 "/certs/$1/privkey.pem"
  chmod 640 "/certs/$1/privkey.pem"
}

last_renew=0
last_report=""
while :; do
  now=$(date +%s)
  # Backend cavab vermirsə, heç nə silinmir (boş siyahı = "hamısını sil" demək deyil)
  if domains=$(wget -qO- --header "X-Internal-Token: ${APP_INTERNAL_TOKEN}" http://backend:8080/internal/tls/domains) \
      && [ -n "$(echo "$domains" | tr -d '[:space:]')" ]; then
    changed=0

    # 1) Yeni domenlər: sertifikatı yoxdursa alınır (uğursuz olanlar RETRY müddətində bir dəfə)
    for domain in $domains; do
      valid "$domain" || continue
      [ -f "/etc/letsencrypt/live/${domain}/fullchain.pem" ] && continue
      failed_at=$(cat "$STATE/failed/$domain" 2>/dev/null || echo 0)
      [ $((now - failed_at)) -lt "$RETRY" ] && continue
      if certbot certonly --webroot -w /var/www/certbot -d "$domain" --email "$ACME_EMAIL" \
          --agree-tos --non-interactive --keep-until-expiring --quiet; then
        rm -f "$STATE/failed/$domain"
      else
        echo "certbot: ${domain} üçün sertifikat alınmadı"
        echo "$now" > "$STATE/failed/$domain"
      fi
      changed=1
    done

    # 2) Mövcud sertifikatların yenilənməsi (saatda bir)
    if [ $((now - last_renew)) -ge "$RENEW" ]; then
      certbot renew --quiet || echo "certbot: yeniləmə xətası"
      last_renew=$now
      changed=1
    fi

    # 3) Paneldən silinmiş domenlər: sertifikat nginx-dən və certbot-dan silinir
    for dir in /certs/*/; do
      [ -d "$dir" ] || continue
      domain=$(basename "$dir")
      valid "$domain" || continue
      if ! echo "$domains" | grep -qxF "$domain"; then
        echo "certbot: ${domain} artıq siyahıda yoxdur — sertifikat silinir"
        rm -rf "/certs/${domain}"
        certbot delete --cert-name "$domain" --non-interactive --quiet || true
        rm -f "$STATE/failed/$domain"
      fi
    done

    # 4) Faylların kopyalanması və vəziyyətin backend-ə göndərilməsi (yalnız dəyişiklik olanda)
    report=""
    for domain in $domains; do
      valid "$domain" || continue
      if [ -f "/etc/letsencrypt/live/${domain}/fullchain.pem" ]; then
        [ "$changed" = 1 ] || [ ! -f "/certs/${domain}/fullchain.pem" ] && copy_cert "$domain"
        report="${report}${domain} ISSUED
"
      else
        report="${report}${domain} FAILED
"
      fi
    done
    if [ -n "$report" ] && [ "$report" != "$last_report" ]; then
      if wget -qO- --header "X-Internal-Token: ${APP_INTERNAL_TOKEN}" --header "Content-Type: text/plain" \
          --post-data "$report" http://backend:8080/internal/tls/status >/dev/null; then
        last_report=$report
      else
        echo "certbot: vəziyyət göndərilmədi"
      fi
    fi
  fi
  sleep "$POLL"
done
