#!/bin/sh
# Upstream faylları (deploy skripti dəyişir); konteyner yenidən yarananda standart dəyərlərə qayıdır.
set -eu
mkdir -p /etc/nginx/upstreams
for service in backend frontend; do
  file="/etc/nginx/upstreams/$service.conf"
  if [ ! -s "$file" ]; then
    echo "server $service:8080;" > "$file"
  fi
done
