# syntax=docker/dockerfile:1.7
# Frontend konteyneri: Vite build, statik faylları root olmayan nginx ilə verir.
# Təhlükəsizlik başlıqları (CSP, HSTS və s.) kənar nginx-də qoyulur.

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
