# Kafe Loyallıq Platforması

NFC əsaslı kafe loyallıq sistemi. Müştəri telefonunu piştaxtadakı NFC çipinə toxundurur, qəhvə möhürü qazanır və kart dolanda hədiyyə qəhvə alır. Kafe sahibləri və işçiləri üçün idarəetmə paneli var. Platforma multi-tenant-dır: hər şirkətin öz domeni və ayrıca verilənlər bazası olur.

> **Vəziyyət:** Mərhələ 1 — skelet və infrastruktur. Tam plan: [docs/architecture-plan.md](docs/architecture-plan.md).

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

> Mərhələ 1-də bütün domenlər eyni skelet tətbiqi göstərir. Domenə görə şirkətin seçilməsi Mərhələ 2-də əlavə olunur.

## Lokal inkişaf (Docker-siz)

Backend (PowerShell, JDK 21 ilə):

```powershell
$env:JAVA_HOME = "<JDK 21 qovluğu>"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Swagger UI (yalnız dev): http://localhost:8080/api/v1/docs

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Vite dev serveri http://localhost:5173 ünvanında işləyir; `/api` sorğularını backend-ə (8080) ötürür. Admin paneli: http://localhost:5173/admin

## Testlər

```powershell
cd backend; .\mvnw.cmd verify          # unit, inteqrasiya (Docker lazımdır) və ArchUnit testləri
```

```bash
cd frontend
npm run lint        # ESLint (UI-da tərcüməsiz mətn qadağandır)
npm run typecheck   # TypeScript (mövcud olmayan tərcümə açarı xəta verir)
npm test            # Vitest: tərcümə paritetı, cəm formaları, formatlar, şriftlər
npm run build
```

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

- Dev giriş məlumatları və test məlumatları (Mərhələ 2–3)
- NFC toxunuşunun simulyasiyası (Mərhələ 5)
- Real NTAG 424 DNA çipinin SUN ilə proqramlaşdırılması (Mərhələ 9, `docs/nfc-ntag424-programming.md`)
- Yeni şirkətin və domenin qoşulması (Mərhələ 9, `docs/tenant-onboarding.md`)
