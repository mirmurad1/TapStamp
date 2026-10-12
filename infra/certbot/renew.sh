#!/bin/sh
# Prod: hər şirkət domeni və platforma domeni üçün Let's Encrypt sertifikatı (HTTP-01, webroot).
#  * Domen siyahısı backend-in daxili endpoint-indən gəlir (yeni şirkət/domen avtomatik görünür).
#  * Sertifikatı olmayan domen üçün alınır, mövcudlar vaxtı yaxınlaşanda yenilənir (certbot renew).
#  * Fayllar nginx-in oxuduğu yerə kopyalanır: /certs/<domen>/{fullchain,privkey}.pem; nginx onları
#    müntəzəm "reload" ilə götürür (docker-compose.prod.yml).
# Domen DNS-də bu serverə yönəlməyibsə, certbot uğursuz olur və növbəti dövrdə yenidən cəhd edilir.
set -u

: "${ACME_EMAIL:?ACME_EMAIL təyin olunmalıdır}"
: "${APP_INTERNAL_TOKEN:?APP_INTERNAL_TOKEN təyin olunmalıdır}"
INTERVAL="${CERTBOT_INTERVAL_SECONDS:-3600}"

while :; do
  domains=$(wget -qO- --header "X-Internal-Token: ${APP_INTERNAL_TOKEN}" http://backend:8080/internal/tls/domains || true)
  for domain in $domains; do
    case "$domain" in
      *[!a-z0-9.-]*) continue ;;  # yalnız düzgün formatlı adlar
    esac
    if [ ! -f "/etc/letsencrypt/live/${domain}/fullchain.pem" ]; then
      certbot certonly --webroot -w /var/www/certbot -d "$domain" --email "$ACME_EMAIL" \
        --agree-tos --non-interactive --keep-until-expiring --quiet || echo "certbot: ${domain} üçün sertifikat alınmadı"
    fi
  done
  certbot renew --quiet || echo "certbot: yeniləmə xətası"
  for domain in $domains; do
    live="/etc/letsencrypt/live/${domain}"
    if [ -f "${live}/fullchain.pem" ]; then
      mkdir -p "/certs/${domain}"
      cp -L "${live}/fullchain.pem" "/certs/${domain}/fullchain.pem.tmp" && mv "/certs/${domain}/fullchain.pem.tmp" "/certs/${domain}/fullchain.pem"
      cp -L "${live}/privkey.pem" "/certs/${domain}/privkey.pem.tmp" && mv "/certs/${domain}/privkey.pem.tmp" "/certs/${domain}/privkey.pem"
      # nginx sertifikatı hər bağlantıda ($ssl_server_name) worker prosesi ilə oxuyur — worker "nginx" istifadəçisidir
      # (rəsmi image-də gid 101): açar yalnız root və nginx qrupu üçün oxunandır
      chown 0:101 "/certs/${domain}/privkey.pem"
      chmod 640 "/certs/${domain}/privkey.pem"
    fi
  done
  sleep "$INTERVAL"
done
