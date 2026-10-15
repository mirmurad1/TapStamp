#!/usr/bin/env bash
# GitHub Actions-dan gələn deploy (bax: .github/workflows/deploy.yml, docs/deployment.md "Avtomatik deploy").
#
# Deploy SSH açarı serverdə authorized_keys-də bu skriptə "məcburi əmr" kimi bağlanır: açarla yalnız bu skript
# işləyə bilər, shell, port yönləndirmə və s. yoxdur. Klientin göndərdiyi əmr ($SSH_ORIGINAL_COMMAND) yalnız
# deploy ediləcək commit-in SHA-sıdır; başqa heç nə qəbul olunmur.
#
# Addımlar: kilid (eyni anda bir deploy) → git fetch → SHA origin/main-də olmalıdır → fast-forward → deploy.sh --build.
# SSH bağlantısı kəsilsə də deploy yarımçıq qalmır (SIGHUP nəzərə alınmır). Log: /var/log/tapstamp-deploy.log
set -euo pipefail
trap '' HUP

cd "$(dirname "$(readlink -f "$0")")/.."
exec > >(tee -a /var/log/tapstamp-deploy.log) 2>&1

sha="${SSH_ORIGINAL_COMMAND:-}"
if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "[remote-deploy] XƏTA: yalnız 40 simvolluq commit SHA qəbul olunur" >&2
  exit 2
fi

exec 9>/var/lock/tapstamp-deploy.lock
if ! flock -w 1800 9; then
  echo "[remote-deploy] XƏTA: başqa deploy 30 dəqiqədən çoxdur davam edir" >&2
  exit 1
fi

echo "[remote-deploy $(date '+%F %T')] başlayır: $sha"
git fetch --quiet origin main
if ! git merge-base --is-ancestor "$sha" origin/main; then
  echo "[remote-deploy] XƏTA: $sha origin/main-də yoxdur" >&2
  exit 1
fi
# Serverdə əl ilə dəyişiklik varsa, merge dayanır (heç nə itmir)
git merge --ff-only --quiet "$sha"

./scripts/deploy.sh --build
echo "[remote-deploy $(date '+%F %T')] bitdi: $sha"
