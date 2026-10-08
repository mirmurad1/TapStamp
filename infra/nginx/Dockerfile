# nginx: rəsmi image + Alpine paketlərinin təhlükəsizlik yamaqları (Trivy, Mərhələ 9 — məs. libexpat, pcre2)
FROM nginx:stable-alpine
RUN apk upgrade --no-cache
# Backend/frontend upstream-ləri ayrıca fayllardadır: deploy skripti (scripts/deploy.sh) onları yeni konteynerə
# yönəldib nginx-i sıfır fasilə ilə yenidən yükləyir. Fayl yoxdursa, standart (servis adı) yazılır.
COPY 05-upstreams.sh /docker-entrypoint.d/05-upstreams.sh
RUN chmod +x /docker-entrypoint.d/05-upstreams.sh
