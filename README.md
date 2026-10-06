# Kafe Loyallıq Platforması

NFC əsaslı kafe loyallıq sistemi. Müştəri telefonunu piştaxtadakı NFC çipinə toxundurur, qəhvə möhürü qazanır və kart dolanda hədiyyə qəhvə alır. Kafe sahibləri və işçiləri üçün idarəetmə paneli var. Platforma multi-tenant-dır: hər şirkətin öz domeni və ayrıca verilənlər bazası olur.

> **Vəziyyət:** Mərhələ 2 — multi-tenancy (hər şirkətə ayrıca baza), şifrələmə və test məlumatları. Tam plan: [docs/architecture-plan.md](docs/architecture-plan.md).

## Texnologiyalar

| Qat | Texnologiya |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security 7, PostgreSQL 16, Flyway |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, TanStack Query, react-i18next |
| İnfrastruktur | Docker Compose: postgres, backend, frontend, nginx (TLS) |
| Dillər | Azərbaycan (az), İngilis (en), Rus (ru) |

## Arxitektura (qısa)

```
Telefon / brauzer ──HTTPS──► nginx ──► /api, /t  → backend (Spring Boot) ──► PostgreSQL
                                   └─► /, /admin → frontend (statik SPA)       ├─ loyalty_platform (reyestr)
                                                                               └─ loyalty_t_<şirkət> (hər şirkətə ayrıca)
```

Ətraflı diaqramlar və bütün qərarlar plandadır: [docs/architecture-plan.md](docs/architecture-plan.md).

## Tələblər

- **Docker Desktop** (və ya Rancher Desktop / Podman Desktop) — `docker compose` və inteqrasiya testləri üçün
- **JDK 21** — yalnız backend-i Docker-siz işə salmaq və testləri lokal işlətmək üçün
- **Node.js 24** — frontend-i Docker-siz işə salmaq üçün
- **Git Bash** (və ya WSL) — `scripts/*.sh` skriptləri üçün

## Quraşdırma (Docker ilə)

1. Sirləri yaradın (`.env` faylı yaranır; onu heç vaxt git-ə əlavə etməyin):

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\gen-secrets.ps1
   ```

   Linux/macOS və ya Git Bash: `bash scripts/gen-secrets.sh`

2. Dev domenləri üçün TLS sertifikatlarını yaradın:

   ```bash
   bash scripts/gen-dev-certs.sh
   ```

   Skript `infra/nginx/certs/dev-ca.pem` lokal sertifikat mərkəzini yaradır. Brauzer xəbərdarlıq göstərməsin deyə onu **öz kompüterinizdə** etibarlı kimi əlavə edin. Windows-da Chrome və Edge üçün:

   ```powershell
   certutil -user -addstore Root infra\nginx\certs\dev-ca.pem
   ```

   Firefox-un öz sertifikat anbarı var: *Settings → Privacy & Security → Certificates → Import*. Bu CA yalnız lokal inkişaf üçündür. Açar faylını (`dev-ca.key`) heç kimlə paylaşmayın.

3. Mühiti işə salın:

   ```bash
   docker compose up --build
   ```

4. Ünvanlar (`*.localhost` ünvanları brauzerdə avtomatik `127.0.0.1`-ə yönləndirilir, `hosts` faylını dəyişmək lazım deyil):

   | Ünvan | Nə |
   |---|---|
   | https://mockcoffee.localhost | Mock Coffee House — müştəri tətbiqi |
   | https://mockcoffee.localhost/admin | Mock Coffee House — işçi paneli |
   | https://demo-cay.localhost | İkinci (demo) şirkət |
   | https://platform.localhost/admin | Platforma paneli |

   443 və ya 80 portu məşğuldursa, `.env`-də `HTTPS_PORT=8443` və `HTTP_PORT=8080` təyin edin və ünvanlara portu əlavə edin.

5. Mühiti avtomatik yoxlayın (TLS, domenə görə şirkət seçimi, təhlükəsizlik başlıqları — 18 yoxlama):

   ```bash
   bash scripts/smoke-test.sh
   ```

Şirkət domenə görə seçilir: `mockcoffee.localhost` və `demo-cay.localhost` fərqli bazalarla işləyir, bir-birinin məlumatını görmür. Məsələn, https://mockcoffee.localhost/api/v1/public/company yalnız Mock Coffee House-un məlumatını qaytarır.

## Test məlumatları (yalnız dev)

`dev` profilində tətbiq ilk dəfə işə düşəndə iki şirkəti **real provisioning servisi ilə** yaradır (ayrıca baza, ayrıca DB rolu) və doldurur:

| Şirkət | Domen | Məzmun |
|---|---|---|
| Mock Coffee House | `mockcoffee.localhost` | 2 filial (Mərkəz, Gənclik), 3 NFC çipi (biri `NTAG424_SUN`), "9 möhür → hədiyyə qəhvə" qaydası, ~150 müştəri və son 90 günün tarixçəsi (möhürlər, mükafatlar, istifadə olunmuş mükafatlar, rədd edilmiş toxunuşlar, fırıldaq işarələri) |
| Demo Çay Evi | `demo-cay.localhost` | 1 filial, 1 çip, 12 müştəri — tenant izolyasiyasını nümayiş etdirmək üçün |

Müştərilərin dili təxminən 65% az, 20% ru, 15% en-dir. Telefon nömrələri saxtadır və bazada şifrələnmiş saxlanılır. Seed yalnız boş bazanı doldurur, təkrar işə salma məlumatı dublikat etmir. Söndürmək üçün: `.env` faylında `DEV_SEED_ENABLED=false`.

### Dev giriş məlumatları

> ⚠️ Bu hesablar **yalnız `dev` profilində** yaradılır (`DevAccountSeeder`). Prod-da `dev` profili və seed aktiv olarsa, tətbiq ümumiyyətlə başlamır (`ProdSafetyGuard`).

Bütün hesabların şifrəsi: **`Kofe-Dev-Parol-2026`** (`.env` faylında `DEV_SEED_ADMIN_PASSWORD` ilə dəyişdirilə bilər).

| Panel | Ünvan | Email | Rol | 2FA |
|---|---|---|---|---|
| Mock Coffee House | https://mockcoffee.localhost/admin | `owner@mockcoffee.az` | Sahib (bütün filiallar) | ✅ dev TOTP sirri |
| Mock Coffee House | https://mockcoffee.localhost/admin | `manager@mockcoffee.az` | Filial meneceri (Gənclik) | — |
| Mock Coffee House | https://mockcoffee.localhost/admin | `barista@mockcoffee.az` | Barista (Mərkəz), interfeys dili rus | — |
| Demo Çay Evi | https://demo-cay.localhost/admin | `owner@democay.az` | Sahib | ilk girişdə qurulur (QR kod) |
| Platforma | https://platform.localhost/admin | `superadmin@platform.localhost` | Platforma admini | ✅ dev TOTP sirri |

**Dev TOTP sirri** (sahib və platforma admini üçün): `MNQW MZJN NRXX SYLM OR4S 2ZDF OYWW WZLZ`. Autentifikator tətbiqinə (Google Authenticator, Microsoft Authenticator, 1Password və s.) "açarı əl ilə daxil et" ilə əlavə edin: növ — vaxta əsaslanan (TOTP), 6 rəqəm, 30 saniyə. Hər kod yalnız bir dəfə qəbul olunur — eyni kodla ikinci giriş üçün növbəti kodu gözləyin.

**Dev poçt qutusu.** Dev-də email göndərilmir: dəvət və şifrə bərpası məktubları yaddaşda saxlanılır və cari domen üzrə göstərilir — məsələn, https://mockcoffee.localhost/api/v1/dev/outbox (platforma üçün https://platform.localhost/api/v1/dev/outbox). Məktubdakı linki brauzerdə açın. Bu endpoint prod-da mövcud deyil.

## Yeni şirkətin yaradılması (provisioning)

Prod-da yeni şirkət işləyən tətbiqdən ayrı, birdəfəlik əmrlə yaradılır. Bu, tətbiqin baza yaratma hüququ olmadan işləməsinə imkan verir:

```bash
java -jar app.jar --spring.main.web-application-type=none \
  --provision-tenant=mockcoffee --domain=bonus.mockcoffee.az \
  --name-az="Mock Coffee House" --name-en="Mock Coffee House" --name-ru="Mock Coffee House"
```

Proses ayrıca baza (`loyalty_t_<slug>`), iki DB rolu (sxem sahibi və yalnız DML hüquqlu tətbiq rolu) yaradır, miqrasiyaları işə salır və domeni qeydiyyata alır. Yarımçıq qalsa, eyni əmri təkrar işə salmaq kifayətdir.

Platforma panelindən (https://platform.localhost/admin → "Yeni şirkət") yaratmaq da mümkündür: bu, step-up (TOTP ilə təkrar təsdiq) tələb edir və prod-da standart olaraq söndürülüb (`APP_PROVISIONING_ENABLED=false`). Şirkət yaradılandan sonra platforma admini sahibə dəvət göndərir — link şirkətin öz domenində açılır, sahib hesab yaradır və ilk girişdə 2FA qurur.

### İlk platforma admini (prod)

Prod-da veb interfeysdən qeydiyyat yoxdur. İlk platforma admini birdəfəlik əmrlə yaradılır; şifrə əmr sətrində (və shell tarixçəsində) görünməsin deyə yalnız mühit dəyişənindən oxunur:

```bash
PLATFORM_ADMIN_PASSWORD='<ən azı 12 simvol>' java -jar app.jar --spring.main.web-application-type=none \
  --create-platform-admin=admin@example.az --display-name="Ad Soyad"
```

İlk girişdə TOTP qurulması məcburidir. Əmrdən sonra `PLATFORM_ADMIN_PASSWORD` dəyişənini silin.

## Lokal inkişaf

**Variant A — backend Docker-də, frontend Vite ilə (ən sadəsi).** `docker-compose.dev.yml` backend-i və PostgreSQL-i yalnız `127.0.0.1`-də açır:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

Sonra frontend-i Vite ilə işə salın (3-cü addım). Brauzer `*.localhost` domenlərini təhlükəsiz kontekst sayır, ona görə `__Host-` cookie-ləri HTTP-də də işləyir.

**Variant B — backend IDE-dən (Docker-siz):**

1. Yalnız PostgreSQL-i Docker-də işə salın (port yalnız `127.0.0.1`-də açılır):

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d postgres
   ```

2. Backend (PowerShell, JDK 21 ilə). `dev` profili sirləri layihənin kökündəki `.env` faylından oxuyur:

   ```powershell
   $env:JAVA_HOME = "<JDK 21 qovluğu>"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   cd backend
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```

   Swagger UI (yalnız dev): http://localhost:8080/api/v1/docs

3. Frontend:

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

   Vite dev serveri `/api` sorğularını backend-ə (8080) ötürür. Şirkət domenə görə seçildiyi üçün `localhost` yox, dev domenini açın: http://mockcoffee.localhost:5173 (admin: http://mockcoffee.localhost:5173/admin, platforma: http://platform.localhost:5173/admin).

## Testlər

```powershell
cd backend; .\mvnw.cmd test            # unit və ArchUnit testləri (Docker lazım deyil)
cd backend; .\mvnw.cmd verify          # + inteqrasiya testləri (*IT): Testcontainers ilə real PostgreSQL 16
```

İnteqrasiya testləri production-dakı eyni init skripti ilə PostgreSQL konteyneri qaldırır, iki test şirkəti yaradır və izolyasiyanı həm HTTP, həm də DB hüquqları səviyyəsində yoxlayır. Docker yoxdursa, bu testlər uğursuz sayılmır, atlanır.

Autentifikasiya testləri (`StaffAuthIT`, `PasswordResetIT`, `InvitationIT`, `PlatformAdminIT`, `AdminAccessIT`, `RateLimitIT`) idarə olunan test saatı ilə işləyir: 30 dəqiqəlik fəaliyyətsizlik, 8 saatlıq mütləq limit, link ömrü və TOTP addımları gözləmədən yoxlanılır. `AdminAccessIT` bütün admin endpoint-lərinin inventarını çıxarır və sessiyasız heç birinin işləmədiyini təsdiqləyir.

```bash
cd frontend
npm run lint        # ESLint (UI-da tərcüməsiz mətn qadağandır)
npm run typecheck   # TypeScript (mövcud olmayan tərcümə açarı xəta verir)
npm test            # Vitest: tərcümə paritetı, cəm formaları, formatlar, şriftlər
npm run build
```

## API müqaviləsi (OpenAPI → TypeScript)

Frontend-in API klienti backend-in OpenAPI sənədindən yaradılan tiplərlə işləyir (`openapi-fetch`): mövcud olmayan endpoint və ya səhv sahə build zamanı xəta verir.

1. `backend`-də `.\mvnw.cmd verify` — `OpenApiContractIT` sənədi `frontend/src/shared/api/openapi.json`-a yazır (hər əməliyyatın Azərbaycan dilində təsviri olduğunu da yoxlayır).
2. `frontend`-də `npm run api:types` — `schema.d.ts` yenilənir.

CI hər iki faylın commit olunmuş versiyasının aktual olduğunu yoxlayır.

## Yeni tərcümə açarının əlavə edilməsi

**Frontend:**

1. Açarı uyğun namespace faylına **3 dilin hamısında** əlavə edin: `frontend/src/locales/az/<ns>.json`, `en/<ns>.json`, `ru/<ns>.json`. Namespace-lər: `common`, `auth`, `profile`, `admin`, `errors`.
2. Sayla dəyişən mətnlər üçün cəm formalarından istifadə edin:
   - az və en: `açar_one`, `açar_other`;
   - ru: `açar_one`, `açar_few`, `açar_many`, `açar_other` (1 штамп / 2 штампа / 5 штампов).
3. Komponentdə: `const { t } = useTranslation('profile'); t('card.progress', { count })`.
4. Yoxlayın: `npm run typecheck` (açar `az` faylında yoxdursa xəta verir) və `npm test` (3 dilin açarları və cəm formaları uyğun gəlmirsə uğursuz olur).

**Backend:**

1. Açarı `backend/src/main/resources/i18n/messages_az.properties`, `messages_en.properties` və `messages_ru.properties` fayllarının hamısına əlavə edin (UTF-8).
2. Yeni xəta kodu üçün `ErrorCode` enum-una dəyər və `error.<KOD>.title` / `error.<KOD>.detail` açarlarını əlavə edin. Frontend üçün `frontend/src/locales/*/errors.json` fayllarına da eyni kodu əlavə edin.
3. Arqumentli mesajlarda apostrofu iki dəfə yazın (`''`) — bu, `MessageFormat` qaydasıdır.
4. `MessagesParityTest` 3 faylın açarlarının eyni olduğunu yoxlayır.

Terminologiya (möhür / stamp / штамп və s.) üçün plana baxın: `docs/architecture-plan.md` §11.8.

## Layihə strukturu

```
backend/    Spring Boot tətbiqi (az.cafeloyalty.*)
frontend/   Vite: müştəri PWA (/) və admin paneli (/admin)
infra/      nginx və PostgreSQL konfiqurasiyası
scripts/    sirlərin və dev sertifikatlarının yaradılması
docs/       arxitektura planı və sənədlər
```

## Növbəti bölmələr

Aşağıdakı bölmələr müvafiq mərhələlərdə əlavə olunacaq:

- NFC toxunuşunun simulyasiyası (Mərhələ 5)
- Real NTAG 424 DNA çipinin SUN ilə proqramlaşdırılması (Mərhələ 9, `docs/nfc-ntag424-programming.md`)
- Yeni şirkətin və domenin qoşulması (Mərhələ 9, `docs/tenant-onboarding.md`)
