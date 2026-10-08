# TapStamp

NFC əsaslı universal loyallıq platforması: kafe, avtoyuma, bərbər, mağaza və istənilən xidmət üçün. Müştəri telefonunu piştaxtadakı NFC çipinə toxundurur, möhür qazanır və kart dolanda hədiyyə alır. Tətbiq quraşdırmaq lazım deyil. Şirkət sahibləri və işçiləri üçün idarəetmə paneli var. Platforma multi-tenant-dır: hər şirkətin öz domeni və ayrıca verilənlər bazası olur.

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
| Mock Coffee House | https://mockcoffee.localhost/admin | `barista@mockcoffee.az` | İşçi (Mərkəz), interfeys dili rus | — |
| Demo Çay Evi | https://demo-cay.localhost/admin | `owner@democay.az` | Sahib | ilk girişdə qurulur (QR kod) |
| Platforma | https://platform.localhost/admin | `superadmin@platform.localhost` | Platforma admini | ✅ dev TOTP sirri |

**Dev TOTP sirri** (sahib və platforma admini üçün): `ORQX A43U MFWX ALLE MV3C 25DP ORYG WZLZ`. Autentifikator tətbiqinə (Google Authenticator, Microsoft Authenticator, 1Password və s.) "açarı əl ilə daxil et" ilə əlavə edin: növ — vaxta əsaslanan (TOTP), 6 rəqəm, 30 saniyə. Hər kod yalnız bir dəfə qəbul olunur — eyni kodla ikinci giriş üçün növbəti kodu gözləyin.

**Dev poçt qutusu.** Dev-də email və SMS göndərilmir: dəvət və şifrə bərpası məktubları, müştərilərin OTP kodları yaddaşda saxlanılır və cari domen üzrə göstərilir — məsələn, https://mockcoffee.localhost/api/v1/dev/outbox (yalnız SMS: `?channel=SMS`, platforma üçün https://platform.localhost/api/v1/dev/outbox). SMS mətni backend-in loguna da yazılır (`docker compose logs backend | grep SMS`). Bu endpoint və konsol provayderi prod-da mövcud deyil.

### Müştəri girişi (dev)

Müştəri tətbiqi: https://mockcoffee.localhost (və ya Vite ilə http://mockcoffee.localhost:5173). Giriş telefon nömrəsi və SMS kodu ilədir:

- **İstənilən yeni nömrə** (məs. `055 123 45 67`) — kod təsdiqlənəndən sonra qeydiyyat ekranı açılır (ad istəyə bağlıdır, məxfilik siyasəti və şərtlərlə razılıq məcburidir).
- **Qayıdan müştəri:** `050 123 45 67` — Mock Coffee House-un seed müştərilərindən biridir, kartında möhürlər və tarixçəsi var.
- Kodu dev poçt qutusundan və ya backend logundan götürün. Kod 5 dəqiqə etibarlıdır, 5 yanlış cəhddən sonra bloklanır; yeni kod 60 saniyədən sonra istənilə bilər (nömrə üzrə 15 dəqiqədə 3, gündə 10).

Sessiya: access token 15 dəqiqə, refresh token 180 gün (hər istifadədə yenilənir); hər ikisi HttpOnly cookie-dədir, JavaScript onları oxuya bilmir. "Parametrlər" səhifəsində cihazlar, dil, hüquqi sənədlər və hesabın silinməsi (SMS kodu ilə təsdiq) var.

> iOS qeydi: iOS-da ana ekrana əlavə olunmuş PWA-nın cookie-ləri Safari-dən ayrıdır, NFC linki isə həmişə Safari-də açılır. Müştəri ən azı bir dəfə Safari-də daxil olmalıdır.

## Yeni şirkətin yaradılması (provisioning)

Prod-da yeni şirkət işləyən tətbiqdən ayrı, birdəfəlik əmrlə yaradılır. Bu, tətbiqin baza yaratma hüququ olmadan işləməsinə imkan verir:

```bash
java -jar app.jar --spring.main.web-application-type=none \
  --provision-tenant=mockcoffee --domain=bonus.mockcoffee.az \
  --name-az="Mock Coffee House" --name-en="Mock Coffee House" --name-ru="Mock Coffee House"
```

Proses ayrıca baza (`loyalty_t_<slug>`), iki DB rolu (sxem sahibi və yalnız DML hüquqlu tətbiq rolu) yaradır, miqrasiyaları işə salır və domeni qeydiyyata alır. Yarımçıq qalsa, eyni əmri təkrar işə salmaq kifayətdir.

Platforma panelindən (https://platform.localhost/admin → "Yeni şirkət") yaratmaq da mümkündür: bu, step-up (TOTP ilə təkrar təsdiq) tələb edir və prod-da standart olaraq söndürülüb (`APP_PROVISIONING_ENABLED=false`). Şirkət yaradılandan sonra platforma admini sahibə dəvət göndərir — link şirkətin öz domenində açılır, sahib hesab yaradır. 2FA (iki addımlı yoxlama) könüllüdür, amma **Hesabım** səhifəsindən qurmaq tövsiyə olunur.

### İlk platforma admini (prod)

Prod-da veb interfeysdən qeydiyyat yoxdur. İlk platforma admini birdəfəlik əmrlə yaradılır; şifrə əmr sətrində (və shell tarixçəsində) görünməsin deyə yalnız mühit dəyişənindən oxunur:

```bash
PLATFORM_ADMIN_PASSWORD='<ən azı 12 simvol>' java -jar app.jar --spring.main.web-application-type=none \
  --create-platform-admin=admin@example.az --display-name="Ad Soyad"
```

2FA (TOTP) könüllüdür — hesab səhifəsindən qurmaq tövsiyə olunur. Əmrdən sonra `PLATFORM_ADMIN_PASSWORD` dəyişənini silin.

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
   cd be-tapstamp
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```

   Swagger UI (yalnız dev): http://localhost:8080/api/v1/docs

3. Frontend:

   ```bash
   cd fe-tapstamp
   npm install
   npm run dev
   ```

   Vite dev serveri `/api` sorğularını backend-ə (8080) ötürür. Şirkət domenə görə seçildiyi üçün `localhost` yox, dev domenini açın: http://mockcoffee.localhost:5173 (admin: http://mockcoffee.localhost:5173/admin, platforma: http://platform.localhost:5173/admin).

## Testlər

```powershell
cd be-tapstamp; .\mvnw.cmd test            # unit və ArchUnit testləri (Docker lazım deyil)
cd be-tapstamp; .\mvnw.cmd verify          # + inteqrasiya testləri (*IT): Testcontainers ilə real PostgreSQL 16
```

İnteqrasiya testləri production-dakı eyni init skripti ilə PostgreSQL konteyneri qaldırır, iki test şirkəti yaradır və izolyasiyanı həm HTTP, həm də DB hüquqları səviyyəsində yoxlayır. Docker yoxdursa, bu testlər uğursuz sayılmır, atlanır.

Autentifikasiya testləri (`StaffAuthIT`, `PasswordResetIT`, `InvitationIT`, `PlatformAdminIT`, `AdminAccessIT`, `RateLimitIT`) idarə olunan test saatı ilə işləyir: 30 dəqiqəlik fəaliyyətsizlik, 8 saatlıq mütləq limit, link ömrü və TOTP addımları gözləmədən yoxlanılır. `AdminAccessIT` bütün admin endpoint-lərinin inventarını çıxarır və sessiyasız heç birinin işləmədiyini təsdiqləyir.

```bash
cd fe-tapstamp
npm run lint        # ESLint (UI-da tərcüməsiz mətn qadağandır)
npm run typecheck   # TypeScript (mövcud olmayan tərcümə açarı xəta verir)
npm test            # Vitest: tərcümə paritetı, cəm formaları, formatlar, şriftlər
npm run build
npm run e2e          # Playwright: işləyən dev mühitinə qarşı (docker compose), sistemdəki Edge ilə
```

Playwright dəsti (`fe-tapstamp/e2e`, ardıcıl işləyir — dev hesablarının TOTP kodu eyni 30 saniyədə iki dəfə qəbul olunmur):

| Fayl | Nə yoxlanılır |
|---|---|
| `nfc-tap.spec.ts`, `gift-card.spec.ts` | NFC toxunuşu → qeydiyyat → möhür, replay qorunması; kartın dolması, hədiyyə kartının təhvili |
| `language.spec.ts` | Dilin dəyişməsi (qonaq), müştərinin və işçinin dilinin profildə saxlanması (başqa cihazda da) |
| `isolation.spec.ts` | Tenant izolyasiyası: sessiya cookie-ləri, hədiyyə kartı kodu və NFC linki digər şirkətdə işləmir |
| `admin.spec.ts` | İşçi dəvəti → qəbul → giriş → silinmə; müştəriyə əl ilə möhür; platforma adminin NFC çipini qeydiyyatı və deaktivasiyası, sahibin yalnız baxması |
| `quality.spec.ts` | Bütün əsas səhifələr: telefon (375 px)/masaüstü, az/en/ru — üfüqi daşma yoxdur, axe (WCAG 2.1 AA) ciddi pozuntu tapmır |

Dev profilində yalnız **IP üzrə** sorğu limitləri artırılıb (`application-dev.yml` → `app.rate-limits`), çünki lokalda bütün testlər bir IP-dən gəlir; nömrə və email üzrə limitlər, eləcə də prod dəyərləri dəyişmir.

**PWA (telefona quraşdırma).** Hər şirkətin öz manifesti (`/manifest.webmanifest`: ad, brend rəngi) və ikonları (`/api/v1/public/icons/{180,192,512,maskable-512}.png`) var. Service worker yalnız prod build-də qeydiyyat olunur, yalnız statik faylları keşləyir (API, NFC linkləri və admin paneli heç vaxt keşlənmir), internet olmayanda 3 dildə oflayn ekran göstərir. Lokalda service worker-in işləməsi üçün dev CA sertifikatı brauzerdə etibarlı olmalıdır (`scripts/gen-dev-certs.sh`). Müştərinin **Parametrlər** səhifəsində "Ana ekrana əlavə et" (Android) və ya iPhone üçün təlimat görünür.

## API müqaviləsi (OpenAPI → TypeScript)

Frontend-in API klienti backend-in OpenAPI sənədindən yaradılan tiplərlə işləyir (`openapi-fetch`): mövcud olmayan endpoint və ya səhv sahə build zamanı xəta verir.

1. `be-tapstamp`-də `.\mvnw.cmd verify` — `OpenApiContractIT` sənədi `fe-tapstamp/src/shared/api/openapi.json`-a yazır (hər əməliyyatın Azərbaycan dilində təsviri olduğunu da yoxlayır).
2. `frontend`-də `npm run api:types` — `schema.d.ts` yenilənir.

CI hər iki faylın commit olunmuş versiyasının aktual olduğunu yoxlayır.

## Yeni tərcümə açarının əlavə edilməsi

**Frontend:**

1. Açarı uyğun namespace faylına **3 dilin hamısında** əlavə edin: `fe-tapstamp/src/locales/az/<ns>.json`, `en/<ns>.json`, `ru/<ns>.json`. Namespace-lər: `common`, `auth`, `profile`, `admin`, `errors`.
2. Sayla dəyişən mətnlər üçün cəm formalarından istifadə edin:
   - az və en: `açar_one`, `açar_other`;
   - ru: `açar_one`, `açar_few`, `açar_many`, `açar_other` (1 штамп / 2 штампа / 5 штампов).
3. Komponentdə: `const { t } = useTranslation('profile'); t('card.progress', { count })`.
4. Yoxlayın: `npm run typecheck` (açar `az` faylında yoxdursa xəta verir) və `npm test` (3 dilin açarları və cəm formaları uyğun gəlmirsə uğursuz olur).

**Backend:**

1. Açarı `be-tapstamp/src/main/resources/i18n/messages_az.properties`, `messages_en.properties` və `messages_ru.properties` fayllarının hamısına əlavə edin (UTF-8).
2. Yeni xəta kodu üçün `ErrorCode` enum-una dəyər və `error.<KOD>.title` / `error.<KOD>.detail` açarlarını əlavə edin. Frontend üçün `fe-tapstamp/src/locales/*/errors.json` fayllarına da eyni kodu əlavə edin.
3. Arqumentli mesajlarda apostrofu iki dəfə yazın (`''`) — bu, `MessageFormat` qaydasıdır.
4. `MessagesParityTest` 3 faylın açarlarının eyni olduğunu yoxlayır.

Terminologiya (möhür / stamp / штамп və s.) üçün plana baxın: `docs/architecture-plan.md` §11.8.

## Layihə strukturu

```
be-tapstamp/ Spring Boot tətbiqi (az.tapstamp.*)
fe-tapstamp/ Vite: müştəri PWA (/) və admin paneli (/admin)
infra/      nginx və PostgreSQL konfiqurasiyası
scripts/    sirlərin və dev sertifikatlarının yaradılması
docs/       arxitektura planı və sənədlər
```

### NFC toxunuşunun simulyasiyası (dev)

Real çip olmadan bütün axını yoxlamaq üçün:

1. https://mockcoffee.localhost/admin — `barista@mockcoffee.az` ilə daxil olun (2FA yoxdur).
2. Menyuda **NFC simulyatoru** (yalnız dev-də görünür): çipi seçin və **Toxun** düyməsini basın. Hər basış, real çip kimi, sayğacı artırır və yeni imzalı link (`/t/{uid}?picc=…&cmac=…`) yaradır.
   - `04A1B2C3D4E5F2` (Piştaxta 2) — `NTAG424_SUN`: link **real NTAG 424 DNA kriptoqrafiyası** ilə yaradılır (AES-128 PICCData + AES-CMAC), yoxlamanı `Ntag424SunVerifier` aparır.
   - Digərləri — `SIMULATED`: `APP_DEV_SIMULATOR_KEY` ilə HMAC imzası (yalnız dev).
3. "Bu brauzerdə aç" — linki açın (və ya QR-ı telefonla skan edin). Daxil olmamısınızsa, telefon nömrəsi ilə girişdən sonra möhür avtomatik verilir (toxunuş 10 dəqiqə saxlanılır).
4. "Eyni linki təkrar aç" — ikinci dəfə **"Bu link artıq istifadə olunub"** görünməlidir (replay qorunması).

Kart dolanda (Mock Coffee House-da 9 möhür) təbrik animasiyası çıxır və **Hədiyyə kartları** bölməsində daimi unikal kodlu (`7K3M-9QX2` formatında) və QR-lı kart yaranır. Sui-istifadə qaydaları (standart): iki möhür arasında 20 dəqiqə — **bütün filiallar üzrə** (bir filialda möhür alan müştəri bu müddətdə digər filialda da ala bilməz) — və gündə ən çox 3 möhür (şirkətin saat qurşağı ilə). Sahib (`owner@mockcoffee.az`) bunları, həmçinin hədiyyə üçün möhür sayını (1–50), hədiyyənin adını (3 dildə) və hədiyyə kartının müddətini admin panelin **Parametrlər** bölməsindən dəyişir.

İşçi təsdiqi rejimi (`require_staff_approval`) aktiv olan filialda möhür **İşçi konsolu**nda təsdiqlənənə qədər gözləyir; 30 dəqiqə ərzində təsdiqlənməzsə, avtomatik rədd edilir.

### Hədiyyə kartının təhvili (dev)

1. Müştəri **Hədiyyə kartları** bölməsində "Kodu göstər" basır — kod (məs. `Q85J-QMJW`) və QR görünür.
2. İşçi https://mockcoffee.localhost/admin/staff-console səhifəsində kodu yazır (kiçik hərf, boşluq və tiresiz də olar) və ya **QR-ı skan et** ilə kameradan oxuyur. Kartın həqiqiliyi, müştərinin adı və maskalı nömrəsi, hədiyyə və tarixlər görünür.
3. **Hədiyyəni təhvil ver** — kart birdəfəlik istifadə olunmuş sayılır (filial və işçi qeyd olunur). Müştərinin açıq ekranında 2–3 saniyə ərzində "Hədiyyəniz təhvil verildi!" çıxır.
4. Eyni kodu yenidən yoxlasanız — "İstifadə olunub" (tarix və filial ilə).

Kodu yalnız daxil olmuş işçi yoxlaya bilər; işçi üzrə dəqiqədə 30 cəhd, uğursuz cəhdlər audit jurnalına yazılır. Kameradan istifadə yalnız `/admin` səhifələrində icazəlidir (Permissions-Policy).

### Admin paneli (Mərhələ 7)

Sahib (`owner@mockcoffee.az`) və filial meneceri (`manager@mockcoffee.az`, yalnız öz filialı) üçün:

| Bölmə | Nə var |
|---|---|
| **İdarəetmə paneli** | Ümumi/aktiv müştərilər, yeni müştərilər, möhürlər, hədiyyələr, geri qayıtma; günlük/həftəlik qrafik, istilik xəritəsi (gün × saat), dil bölgüsü; dövr və filial filtri. Təriflər: `docs/metrics.md` |
| **Müştərilər** | Ad və ya telefonla axtarış, filtrlər (dil, status, gəlməyənlər, filial), sıralama; detallar, tarixçə, fırıldaq işarələri; möhürün əl ilə düzəlişi (səbəb məcburi, təkrar göndərişdən qorunur). Sahib üçün: tam nömrə və CSV ixracı (kimliyin təsdiqi ilə, auditə yazılır), bloklama |
| **NFC çipləri** | Yalnız baxış: çiplər, vəziyyəti (pulsuz/pullu), toxunuş jurnalı. Çipləri platforma admini əlavə edir: **Platforma → şirkət → NFC çipləri** (qeydiyyat, birdəfəlik provisioning paketi, açar rotasiyası, deaktivasiya; hər şirkətdə ilk çip pulsuzdur, `APP_NFC_FREE_TAGS`). Təlimat: `docs/nfc-ntag424-programming.md` |
| **Parametrlər** | Möhür qaydası, hədiyyə, şirkətin adı/təsviri (3 dildə), brend rəngi, saat qurşağı; filiallar (ad, ünvan, iş saatları) və filial üzrə ayrıca qayda |
| **Komanda** | Dəvət, bloklama, silmə (bazada yumşaq silinmə), 2FA-nın sıfırlanması |
| **Audit jurnalı** | Kim, nə vaxt, nə etdi (yalnız sahib) |

Platforma panelində (`superadmin@platform.localhost`) şirkətin səhifəsində **Domenlər** (əlavə etmək, əsas etmək, silmək) və ayrıca **Audit jurnalı** var.

Ekran görüntüləri: `docs/screenshots/phase7-*`.

## Sənədlər

| Sənəd | Mövzu |
|---|---|
| [docs/deployment.md](docs/deployment.md) | Prod-a yerləşdirmə: server, DNS, sirlər, HTTPS (certbot), ehtiyat nüsxə, yeniləmə |
| [docs/tenant-onboarding.md](docs/tenant-onboarding.md) | Yeni şirkətin və domenin qoşulması, sahibin ilk addımları |
| [docs/nfc-ntag424-programming.md](docs/nfc-ntag424-programming.md) | NTAG 424 DNA çipinin proqramlaşdırılması və açar rotasiyası |
| [docs/load-test.md](docs/load-test.md) | Yük testi: 50 şirkət, metodologiya və nəticələr |
| [docs/i18n.md](docs/i18n.md) | Çoxdillilik: dilin seçilməsi, yeni mətn və yeni dil |
| [docs/error-codes.md](docs/error-codes.md) | API xəta kodları (avtomatik yaradılır: `node scripts/gen-error-codes.cjs`) |
| [docs/metrics.md](docs/metrics.md) | Analitika göstəricilərinin tərifləri |
| [docs/security-review.md](docs/security-review.md) | Yekun təhlükəsizlik icmalı (OWASP Top 10, asılılıq skanı, qəbul edilmiş risklər) |
| [docs/architecture-plan.md](docs/architecture-plan.md) | Arxitektura planı və qərarlar jurnalı (§18) |
| [docs/screenshots](docs/screenshots/README.md) | Mərhələlər üzrə ekran görüntüləri |
