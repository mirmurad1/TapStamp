#!/usr/bin/env bash
# Sıfır fasiləli deploy (blue-green): sorğular itmir, istifadəçi 502 görmür.
#
#   ./scripts/deploy.sh                 # prod: yeni image-lər, backup, backend və frontend
#   ./scripts/deploy.sh --dev --build   # lokal sınaq: dev compose, image-lər yerində qurulur, backup-sız
#   ./scripts/deploy.sh --only nginx    # yalnız nginx konfiqurasiyası (infra/nginx/*) — fasiləsiz
#   ./scripts/deploy.sh --nginx-image   # əlavə olaraq nginx image-i yenilənir (yamaqlar): 1–3 san fasilə!
#
# Hər servis (backend, frontend) üçün:
#   1. köhnə konteyner işləyərkən yanında yeni versiya qalxır (docker compose --scale 2 --no-recreate);
#   2. yeni konteyner sağlam olana qədər gözlənilir (image-in HEALTHCHECK-i); olmasa — silinir, köhnə işləyir;
#   3. bütün servislər hazır olanda nginx BİR DƏFƏ yeni konteynerlərə keçirilir (reload açıq bağlantıları kəsmir);
#      eyni reload-da nginx konfiqurasiyası da yenilənir: fayllar (infra/nginx) konteynerə mount olunub, şablonlar
#      (templates/*.template) yenidən emal olunur; "nginx -t" uğursuzdursa, köhnə konfiqurasiya bərpa olunur;
#   4. köhnə konteyner yumşaq dayandırılır: yeni sorğu almır, başlanmış sorğuları bitirir (graceful shutdown).
# nginx-in compose ayarları (yeni env dəyişəni, mount) dəyişibsə, nginx əvvəlcə avtomatik yenidən yaradılır (1–3 san).
#
# Baza miqrasiyaları yeni backend başlayanda tətbiq olunur. Keçid zamanı iki versiya qısa müddət eyni bazada
# işlədiyi üçün miqrasiyalar geriyə uyğun olmalıdır (bax: docs/deployment.md, "Yeniləmə").
set -euo pipefail

cd "$(dirname "$0")/.."
# Git Bash (Windows) "/etc/..." kimi arqumentləri Windows yoluna çevirməsin
export MSYS_NO_PATHCONV=1

FILES=(-f docker-compose.yml -f docker-compose.prod.yml)
BUILD=false
BACKUP=true
SERVICES=(backend frontend)
NGINX_IMAGE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dev) FILES=(-f docker-compose.yml -f docker-compose.dev.yml); BACKUP=false ;;
    --build) BUILD=true ;;
    --no-backup) BACKUP=false ;;
    --only) if [[ "$2" == nginx ]]; then SERVICES=(); else SERVICES=("$2"); fi; shift ;;
    --nginx-image) NGINX_IMAGE=true ;;
    *) echo "Naməlum parametr: $1" >&2; exit 2 ;;
  esac
  shift
done

dc() { docker compose "${FILES[@]}" "$@"; }
log() { printf '[deploy %s] %s\n' "$(date +%H:%M:%S)" "$*"; }

wait_healthy() {
  local id="$1" status
  for _ in $(seq 1 90); do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")
    case "$status" in
      healthy) return 0 ;;
      unhealthy) return 1 ;;
    esac
    sleep 2
  done
  return 1
}

# Köhnədən başqa bütün konteynerləri silir (uğursuz deploy-dan sonra)
remove_extra() {
  local service="$1" old="$2" id
  for id in $(dc ps -a -q "$service"); do
    [[ "$id" == "$old" ]] || docker rm -f "$id" >/dev/null
  done
}

declare -A OLD=() NEW=()
rollback() {
  local service
  for service in "${!OLD[@]}"; do
    remove_extra "$service" "${OLD[$service]}"
  done
}

# Mərhələ 1: hər servisin yeni versiyası köhnənin yanında qalxır və sağlam olana qədər gözlənilir
prepare() {
  local service="$1" old new
  old=$(dc ps -q "$service")
  if [[ -z "$old" ]]; then
    log "$service işləmir — adi başladılır"
    dc up -d --no-deps "$service"
    return 0
  fi
  if [[ $(wc -w <<<"$old") -ne 1 ]]; then
    log "XƏTA: $service üçün bir neçə konteyner var (yarımçıq deploy?): $old" >&2
    return 1
  fi
  old=$(docker inspect --format '{{.Id}}' "$old")
  OLD[$service]="$old"

  log "$service: yeni versiya köhnənin yanında qaldırılır"
  dc up -d --no-deps --no-recreate --scale "$service=2" "$service" || return 1
  new=$(dc ps -q "$service" | awk -v old="$old" '$0 != old { print; exit }')
  [[ -n "$new" ]] || return 1
  NEW[$service]=$(docker inspect --format '{{.Name}}' "$new" | sed 's#^/##')

  log "$service: ${NEW[$service]} sağlam olana qədər gözlənilir"
  if ! wait_healthy "$new"; then
    docker logs --tail 50 "$new" >&2 || true
    return 1
  fi
}

if $BACKUP; then
  log "Bazanın ehtiyat nüsxəsi"
  ./scripts/backup.sh
fi

if [[ ${#SERVICES[@]} -gt 0 ]]; then
  if $BUILD; then
    log "Image-lər qurulur"
    dc build "${SERVICES[@]}"
  else
    log "Yeni image-lər endirilir"
    dc pull --ignore-buildable "${SERVICES[@]}" || true
  fi
fi

# Mərhələ 0: nginx-in compose ayarları (env dəyişənləri, mount-lar, portlar) dəyişibsə, reload kifayət etmir —
# yeni dəyişən köhnə konteynerdə yoxdur və şablon emalı uğursuz olur. Belə halda konteyner əvvəlcədən yenidən
# yaradılır (1–3 san fasilə); upstream-lər servis adına (hələ köhnə konteynerlərə) yönəlir, keçid aşağıda olur.
nginx_id=$(dc ps -q nginx)
if [[ -n "$nginx_id" ]]; then
  running_hash=$(docker inspect --format '{{index .Config.Labels "com.docker.compose.config-hash"}}' "$nginx_id")
  wanted_hash=$(dc config --hash nginx | awk '{ print $2 }')
  if [[ -n "$wanted_hash" && "$running_hash" != "$wanted_hash" ]]; then
    log "nginx ayarları dəyişib — konteyner yenidən yaradılır (qısa fasilə)"
    dc up -d --no-deps --force-recreate nginx
    for _ in $(seq 1 15); do
      if dc exec -T nginx nginx -t -q 2>/dev/null; then
        break
      fi
      sleep 1
    done
  fi
fi

for service in "${SERVICES[@]}"; do
  if ! prepare "$service"; then
    log "XƏTA: $service hazır olmadı — yeni konteynerlər silinir, köhnə versiya işləməyə davam edir" >&2
    rollback
    exit 1
  fi
done

# Mərhələ 2: nginx bir dəfə yenidən yüklənir — yeni konteynerlərə keçid və yenilənmiş konfiqurasiya (şablonlar
# yenidən emal olunur) birlikdə. Konfiqurasiya səhvdirsə (nginx -t), hər ikisi geri qaytarılır, reload edilmir və
# köhnə versiya işləməyə davam edir
if [[ ${#NEW[@]} -gt 0 ]]; then
  log "nginx yeni konteynerlərə keçirilir və konfiqurasiyası yenilənir: ${NEW[*]}"
else
  log "nginx konfiqurasiyası yenilənir"
fi
switch=""
for service in "${!NEW[@]}"; do
  switch+="echo 'server ${NEW[$service]}:8080;' > /etc/nginx/upstreams/$service.conf; "
done
if ! dc exec -T nginx sh -c "rm -rf /tmp/upstreams.bak /tmp/conf.d.bak \
    && cp -r /etc/nginx/upstreams /tmp/upstreams.bak && cp -r /etc/nginx/conf.d /tmp/conf.d.bak \
    && /docker-entrypoint.d/20-envsubst-on-templates.sh >/dev/null && $switch nginx -t -q && nginx -s reload \
    || { cp /tmp/upstreams.bak/* /etc/nginx/upstreams/; cp /tmp/conf.d.bak/* /etc/nginx/conf.d/; exit 1; }"; then
  log "XƏTA: nginx yenilənmədi (konfiqurasiya səhvi?) — köhnə versiya işləməyə davam edir" >&2
  rollback
  exit 1
fi
# Köhnə nginx worker-ləri başlanmış sorğuları köhnə konteynerlərdə bitirir
if [[ ${#NEW[@]} -gt 0 ]]; then
  sleep 3
fi

# Mərhələ 3: köhnə konteynerlər yumşaq dayandırılır (yeni sorğu almır, başlanmışları bitirir)
for service in "${!OLD[@]}"; do
  log "$service: köhnə konteyner yumşaq dayandırılır"
  docker stop "${OLD[$service]}" >/dev/null
  docker rm "${OLD[$service]}" >/dev/null
done
# Mərhələ 4 (yalnız --nginx-image): nginx-in öz image-i (Dockerfile, yamaqlar). İki nginx eyni portu tuta
# bilmədiyi üçün konteyner yenidən yaradılır — 1–3 saniyəlik fasilə. Upstream fayllarını başlanğıc skripti
# servis adı ilə yazır (köhnə konteynerlər artıq dayandırılıb).
if $NGINX_IMAGE; then
  if $BUILD; then dc build nginx; else dc pull nginx || true; fi
  log "nginx konteyneri yenidən yaradılır (qısa fasilə)"
  dc up -d --no-deps --force-recreate nginx
  for _ in $(seq 1 15); do
    if dc exec -T nginx nginx -t -q 2>/dev/null; then
      break
    fi
    sleep 1
  done
fi
log "Deploy tamamlandı"
