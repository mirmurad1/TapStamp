# Yük testi

Skript: `scripts/load-test/load-test.mjs` (yalnız dev mühiti). Nəticə `scripts/load-test/last-result.json`-a yazılır.

```bash
node scripts/load-test/load-test.mjs --tenants 50 --customers 20 --duration 60
```

## Necə işləyir

- **Hazırlıq.** Platforma API-si ilə 50 test şirkəti yaradılır (`lt01` … `lt50`, dev bazasında qalır). Hər birinə filial, hədiyyə qaydası və 1 NFC çipi əlavə olunur. Çipin açarları provisioning paketindən götürülür. Hər şirkətə 20 müştəri SMS kodu ilə qeydiyyatdan keçir.
- **Müştəri axını.** Real NTAG 424 imzalı link (`GET /t/…`) → möhürün alınması (`POST claim`) → kartın açılması (`GET /api/v1/me`). Bir çipə toxunuşlar ardıcıldır (real çipin sayğacı kimi), 50 şirkət isə paralel işləyir.
- **Sorğuların yolu.** Sorğular nginx-siz birbaşa backend-ə gedir. Hər müştəri öz IP-si ilə gəlir (`X-Forwarded-For`), çünki nginx-in IP limitləri bir maşından gələn testi boğardı.

## Nəticə (2026-10-08)

**Mühit:**
- noutbuk, Intel i7-1355U;
- Docker-ə 12 nüvə və 16 GB RAM verilib;
- backend və PostgreSQL eyni maşındadır;
- `APP_TENANT_POOL_MAX=3`, `max_connections=300`.

| Ssenari | Möhür / san | Toxunuş p95 | Möhür p95 | Kart p95 | Xəta | DB bağlantısı | CPU (backend / PG) |
|---|---|---|---|---|---|---|---|
| Real pik: hər kafedə ~3 san-də bir müştəri | 15 | 56 ms | 48 ms | 18 ms | 0 / 2946 | 164 | ~0.3 / ~0.3 nüvə |
| Stress: gözləmə yoxdur | 176 | 246 ms | 198 ms | 115 ms | 0 / 31 785 | 164 | ~3.8 / ~4.2 nüvə |

**Yaddaş:** backend ~680 MB, PostgreSQL ~940 MB (stress zamanı).

## Şərh

- "Real pik" ssenarisi gerçəklikdən çox ağırdır: 50 kafedə dəqiqədə 900+ möhür. Real kafelərdə pik saatda dəqiqədə 50–100 möhür gözlənilir. Bu yükdə cavab müddəti 50 ms-dən azdır, CPU-nun böyük hissəsi boşdur.
- Stress ssenarisi dəqiqədə ~10 000 möhürdür (real pikin ~100 misli). Sistem bunu da xətasız emal edir.
- DB bağlantıları sabit 164-dür (53 şirkət × 3 + platforma). 300-lük limitdə ehtiyat qalır. Şirkət sayı artanda `POSTGRES_MAX_CONNECTIONS`-ı qaldırmaq lazımdır (bax: `.env.example`).
- **Prod üçün nəticə:** 50 şirkətə 4 vCPU və 8 GB RAM-lı VPS kifayətdir. Real yük stress ssenarisindən dəfələrlə azdır.
- Bu ölçmə nginx və TLS olmadan aparılıb, şəbəkə gecikməsi (mobil internet) də nəzərə alınmayıb. İstifadəçinin hiss etdiyi müddət şəbəkəyə görə əlavə 50–300 ms ola bilər.
