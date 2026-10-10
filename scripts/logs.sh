#!/usr/bin/env bash
# Prod logları bir əmrlə (serverdə, /opt/tapstamp-da işlədilir).
#
#   ./scripts/logs.sh                 # backend, canlı (Ctrl+C — çıxış), son 100 sətirdən başlayaraq
#   ./scripts/logs.sh nginx           # başqa servis: frontend, nginx, postgres, certbot
#   ./scripts/logs.sh --errors        # backend-in son 24 saatdakı xətaları
#   ./scripts/logs.sh --trace ABC123  # istifadəçinin gördüyü xətanın (traceId) bütün sətirləri
#   ./scripts/logs.sh --status        # konteynerlərin vəziyyəti
#   ./scripts/logs.sh --dev ...       # lokal dev mühiti üçün
#
# Loglar JSON-dur; jq quraşdırılıbsa (apt install -y jq) qısa formada göstərilir: "vaxt səviyyə mesaj".
set -euo pipefail

cd "$(dirname "$0")/.."
FILES=(-f docker-compose.yml -f docker-compose.prod.yml)
if [[ "${1:-}" == "--dev" ]]; then
  FILES=(-f docker-compose.yml -f docker-compose.dev.yml)
  shift
fi
dc() { docker compose "${FILES[@]}" "$@"; }

# JSON sətirlərini oxunaqlı edir; JSON olmayan sətirlər (nginx, postgres) olduğu kimi qalır
pretty() {
  if command -v jq >/dev/null 2>&1; then
    jq -rR --unbuffered '. as $line | try (fromjson
      | (.["error.stack_trace"] // .error.stack_trace? // "") as $stack
      | "\(.["@timestamp"] // "") \(.["log.level"] // .log.level? // "") \(.message // "")\(if $stack != "" then "\n" + $stack else "" end)")
      catch $line'
  else
    cat
  fi
}

case "${1:-backend}" in
  --errors)
    dc logs --since 24h --no-log-prefix backend | grep -E '"(log\.)?level":"(ERROR|WARN)"' | pretty
    ;;
  --trace)
    [[ -n "${2:-}" ]] || { echo "İstifadə: $0 --trace <traceId>" >&2; exit 2; }
    dc logs --no-log-prefix backend | grep -F "$2" | pretty
    ;;
  --status)
    dc ps
    ;;
  *)
    dc logs -f --tail 100 --no-log-prefix "${1:-backend}" | pretty
    ;;
esac
