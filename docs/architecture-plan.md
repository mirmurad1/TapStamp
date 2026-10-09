# TapStamp — Arxitektura Planı

> **Status:** təsdiq olunub (v1.1) · **Tarix:** 06.10.2026
>
> Bu sənəd layihənin əsas texniki qərarlarını əhatə edir. v1.0 sizin cavablarınızdan sonra yeniləndi: hər şirkətin ayrıca verilənlər bazası və öz domeni var, Spring Boot 4.1 seçildi (§18-dəki qərarlar jurnalına bax). İş §16-dakı mərhələlərlə aparılır. Hər mərhələnin sonunda testlər işə salınır, ayrıca commit edilir və Azərbaycan dilində hesabat verilir. Texniki terminlər ilk dəfə işlənəndə mötərizədə qısa izah verilir; tam siyahı §19-dakı **Terminlər lüğəti**ndədir.

## Mündəricat

1. Əsas qərarların xülasəsi
2. İş mühiti
3. Ümumi arxitektura və multi-tenancy
4. Qovluq strukturu
5. Verilənlər bazası sxemi
6. NFC toxunuşunun təhlükəsizliyi
7. Müştəri autentifikasiyası və sessiyalar
8. Admin, işçi və platforma autentifikasiyası, rollar, izolyasiya
9. Loyallıq məntiqi: möhürlər, qaydalar, mükafatlar
10. API siyahısı
11. Çoxdillilik (az / en / ru)
12. Təhlükəsizlik dizaynı və OWASP Top 10
13. Frontend dizaynı
14. Test strategiyası
15. İnfrastruktur, konfiqurasiya və CI
16. Mərhələlərə bölgü
17. İlkin risklər
18. Qərarlar jurnalı
19. Terminlər lüğəti

---

## 1. Əsas qərarların xülasəsi

| Mövzu | Qərar | Əsaslandırma |
|---|---|---|
| Arxitektura üslubu | **Modul-monolit** (modular monolith: bir tətbiq, daxildə sərhədləri aydın olan modullar) | Bu miqyas üçün mikroservislər lüzumsuz əməliyyat yükü yaradır. Modul sərhədləri ArchUnit testləri ilə qorunur. |
| Backend | Java 21, **Spring Boot 4.1.x** (Spring Security 7, Hibernate 7, Jackson 3), Spring Data JPA, PostgreSQL 16, Flyway, Bean Validation, MapStruct, Lombok (minimal), Bucket4j, springdoc-openapi. Əlavə: BouncyCastle (AES-CMAC, Argon2), libphonenumber, ICU4J, ShedLock. Qurma aləti: **Maven** (wrapper ilə). | Spring Boot 3.5-in pulsuz dəstəyi 30.06.2026-da bitib, 4.1 isə 31.07.2027-yə qədər dəstəklənir (§18). |
| Multi-tenancy (bir sistemdə bir neçə müstəqil şirkət) | **Hər şirkət üçün ayrıca verilənlər bazası** (database-per-tenant) və ayrıca DB rolu. Platforma bazasında yalnız şirkətlərin reyestri, domenlər və platforma adminləri saxlanılır. | Şirkətlərin məlumatları fiziki olaraq ayrılır: bir şirkətin DB bağlantısı digər şirkətin bazasını heç cür oxuya bilməz. |
| Tenant-ın müəyyənləşdirilməsi | Sorğunun `Host` başlığına görə (hər şirkətin öz domeni var). `TenantRoutingDataSource` bağlantını cari tenant-ın ayrıca connection pool-undan verir. | Sorğunun body-si və ya parametrləri tenant-ı heç vaxt təyin etmir. |
| Domenlər | Hər şirkət **yalnız öz domenində** işləyir (məs. `bonus.mockcoffee.az`): müştəri tətbiqi `/`, işçi paneli `/admin`. Platforma paneli platformanın öz domenindədir. Dev-də `mockcoffee.localhost` kimi ünvanlar işlədilir. | Sizin seçiminiz. TLS sertifikatları hər domen üçün avtomatik alınır (Let's Encrypt). |
| Frontend | **Bir Vite layihəsi, iki giriş nöqtəsi** (multi-page build): `index.html` → müştəri PWA, `admin/index.html` → işçi və platforma paneli. Ortaq kod `src/shared` qovluğundadır. | i18n, UI komponentləri və API klienti təkrarlanmır. Müştərinin telefonu admin kodunu və Recharts-ı yükləmir. |
| Müştəri hesabı | Hesab **şirkətin öz bazasındadır**: eyni telefon nömrəsi hər kafedə ayrıca qeydiyyatdan keçir. | Sizin seçiminiz. Hər şirkət ayrıca məlumat nəzarətçisidir, şirkətlər arasında müştəri məlumatı bağlantısı yaranmır. |
| İdentifikatorlar | Bütün əsas açarlar **UUIDv7**-dir (zamana görə sıralanan UUID). | Xaricə yalnız UUID çıxır, ardıcıl ID yoxdur. |
| Müştəri sessiyası | Opaque **access token** (15 dəq) + **refresh token** (180 gün, sürüşən, hər istifadədə rotasiya, təkrar istifadə aşkarlanır), HttpOnly cookie-lərdə. | JWT və localStorage istifadə olunmur. Sessiya istənilən an ləğv edilə bilər. Cookie-lər domenə bağlıdır, ona görə bir şirkətin cookie-si digərinə heç vaxt göndərilmir. |
| Admin sessiyası | Tək opaque token, `__Host-` cookie, `SameSite=Strict`, mütləq ömür 8 saat, fəaliyyətsizlik limiti 30 dəqiqə. | Tələbə uyğundur. |
| NFC | `TagVerifier` strategiyası: `Ntag424SunVerifier` (AES-128 + AES-CMAC, NXP AN12196 test vektorları ilə) və `DevSimulatorVerifier` (yalnız `dev`). | Replay qorunması hər iki implementasiya üçün eyni ortaq koddadır. |
| Rate limiting (sorğu tezliyinin məhdudlaşdırılması) | Bucket4j + PostgreSQL (platforma bazasında, açarlar hash-lənmiş) + nginx `limit_req` kənar qatı. | Redis kimi əlavə servis lazım deyil, bir neçə backend instansiyasında da işləyir. |
| Şifrələmə | **Envelope encryption** (zərf şifrələməsi): env-dən gələn KEK → hər tenant bazasında saxlanan DEK-lər → AES-256-GCM. `KeyEncryptionService` interfeysi KMS və ya Vault-a keçidə hazırdır. | Master açarı dəyişmək üçün məlumatı yenidən şifrələmək lazım gəlmir. |
| Xəta formatı | RFC 7807 Problem Details + sabit `code` + `traceId`. | Frontend mesajı `code`-a görə öz tərcüməsindən göstərir. |
| Fon işləri | Spring `@Scheduled` + ShedLock (platforma bazasında). İş bütün aktiv tenant-lar üzərində növbə ilə icra olunur. | Bir neçə instansiya olsa da, hər iş bir dəfə icra olunur. |
| API müqaviləsi | Backend-in OpenAPI sənədindən TypeScript tipləri generasiya olunur (`openapi-typescript` + `openapi-fetch`). | Frontend ilə backend arasındakı uyğunsuzluq build zamanı tutulur. |

---

## 2. İş mühiti

| Alət | Vəziyyət | Qeyd |
|---|---|---|
| JDK 21 | ✅ `%USERPROFILE%\.jdks\ms-21.0.9` (Microsoft Build of OpenJDK) | Sistemdəki standart `java` 1.8-dir. Layihə skriptləri `JAVA_HOME`-u JDK 21-ə yönəldir. |
| Maven | lazım deyil | Layihədə Maven Wrapper (`mvnw`) var. |
| Node.js / npm | ✅ v24.19.0 / 11.17.0 | |
| Docker | ⏳ Docker Desktop quraşdırılacaq | `docker compose up` və Testcontainers üçün mütləqdir. 250-dən çox işçisi olan şirkətlərdə Docker Desktop ödənişli abunə tələb edir. |
| Git | ✅ | Hər mərhələ ayrıca commit olunur. |

Docker quraşdırılana qədər Docker tələb etməyən işlər görülür: skelet, unit testlər, frontend. Testcontainers əsaslı inteqrasiya testləri Docker olmadıqda avtomatik **atlanır** (uğursuz sayılmır) və hesabatda bu açıq qeyd olunur.

---

## 3. Ümumi arxitektura və multi-tenancy

### 3.1 Komponent diaqramı

```
 Müştəri telefonu:  NFC → https://bonus.mockcoffee.az/t/{uid}?picc=…&cmac=…   (müştəri PWA: "/")
 Şirkət işçiləri:   https://bonus.mockcoffee.az/admin                          (işçi paneli)
 Platforma admini:  https://admin.platforma.example                            (platforma paneli)
                                   │ HTTPS · SNI: hər domen öz sertifikatı ilə
              ┌────────────────────▼─────────────────────────────────────┐
              │ nginx — kənar reverse proxy                                │
              │  • TLS: sertifikat $ssl_server_name ilə seçilir            │
              │  • Host ≠ SNI → 421; tanınmayan domen → bağlantı kəsilir   │
              │  • başlıqlar (CSP, HSTS, …), limit_req                     │
              │  • /api/*, /t/*, /manifest.webmanifest → backend           │
              │  • /admin/*, /*                         → frontend (statik)│
              └──────────┬───────────────────────────────────┬────────────┘
                         │ Host başlığı saxlanılır             │
       ┌─────────────────▼──────────────────┐   ┌─────────────▼─────────────┐
       │ backend — Spring Boot 4.1           │   │ frontend — statik fayllar  │
       │  TenantResolver: Host → tenant      │   │ (müştəri PWA + admin SPA)  │
       │  TenantRoutingDataSource            │   └────────────────────────────┘
       │  modullar: nfc · loyalty · customer │
       │  admin · analytics · audit ·        │── SmsProvider ─► Console (dev) / real provayder
       │  notification · platform · [dev]    │── MailSender  ─► Console (dev) / SMTP
       └──┬──────────────────┬───────────────┘
          │                  │  hər tenant üçün ayrıca connection pool və ayrıca DB rolu
 ┌────────▼─────────┐ ┌──────▼──────────────────┐ ┌────────────────────────┐
 │ loyalty_platform  │ │ loyalty_t_mockcoffee    │ │ loyalty_t_demo_cay     │  …
 │ tenant reyestri,  │ │ müştərilər, möhürlər,   │ │ (eyni sxem, ayrı data) │
 │ domenlər,         │ │ çiplər, işçilər, audit  │ │                        │
 │ platforma adminləri│ └─────────────────────────┘ └────────────────────────┘
 └──────────────────┘
   Dev-də hamısı bir PostgreSQL serverindədir; prod-da istənilən tenant bazası ayrıca serverə köçürülə bilər.
```

Bütün vaxtlar bazada UTC kimi saxlanılır, ekranda isə şirkətin saat qurşağında göstərilir (standart `Asia/Baku`).

### 3.2 Tenant-ın müəyyənləşdirilməsi və izolyasiya qatları

1. **nginx:** sertifikat SNI adına görə seçilir (`ssl_certificate /etc/nginx/certs/$ssl_server_name/fullchain.pem`). Sertifikatı olmayan domenlə TLS bağlantısı qurulmur. HTTP `Host` başlığı SNI ilə üst-üstə düşmürsə, `421` qaytarılır. Bu, bir domenin TLS bağlantısı üzərindən başqa şirkətin `Host`-una sorğu göndərməyin qarşısını alır.
2. **`TenantResolutionFilter`:** `Host` (kiçik hərflə, portsuz) platforma bazasındakı `tenant_domains` cədvəlində axtarılır (60 saniyəlik keşlə). Tapılmazsa, heç bir emal olmadan `404` qaytarılır. Platformanın öz domeni "platforma konteksti" deməkdir: bu kontekstdə tenant məlumatına müraciət mümkün deyil.
3. **`TenantContext`** sorğu müddətində tenant-ı saxlayır və sorğu bitəndə mütləq təmizlənir. Bunu ayrıca test yoxlayır, çünki təmizlənməsə thread-lər arasında sızma ola bilər.
4. **Bağlantı marşrutu:** JPA və JDBC `TenantRoutingDataSource` üzərində işləyir: bağlantı `TenantContext`-dəki tenant-ın Hikari pool-undan verilir. Tenant yoxdursa, bağlantı verilmir və əməliyyat rədd edilir (fail closed). Aktiv tranzaksiya daxilində tenant-ı dəyişmək qadağandır. Hibernate başlanğıcda heç bir tenant bazasına qoşulmur; entity-lərin sxemə uyğunluğu `EntityMappingIT` testi ilə yoxlanılır.
5. **DB rolları:** hər tenant bazasının öz rolu var (`t_<slug>_app`, təsadüfi şifrə ilə; şifrə platforma bazasında envelope encryption ilə saxlanılır). `REVOKE CONNECT ON DATABASE … FROM PUBLIC` icra olunur, yəni rol yalnız öz bazasına qoşula bilir. A şirkətinin kontekstində SQL inyeksiyası olsa belə, bağlantı B şirkətinin bazasını fiziki olaraq görə bilməz.
6. **Cookie-lər domenə bağlıdır** (`__Host-` prefiksi), ona görə A şirkətinin sessiyası B-yə heç vaxt göndərilmir. Sessiya qeydləri də tenant bazasındadır: A-nın tokeni B-nin bazasında sadəcə yoxdur.
7. **Fon işləri** aktiv tenant-ların siyahısını gəzir, hər biri üçün `TenantContext` qurur və işi icra edir. Bir tenant-dakı xəta digərlərinə təsir etmir.
8. **Connection pool-lar** tənbəl yaradılır (lazım olanda): kiçik ölçü (ən çox 5), boş qalanda bağlanır. Tenant sayı artanda PgBouncer əlavə etmək olar (§17).

### 3.3 Tenant-ın yaradılması (provisioning)

`TenantProvisioner` servisi platforma panelindən və ya CLI rejimindən (`java -jar app.jar --provision-tenant=…`) çağırılır:

1. `tenants` cədvəlində `PROVISIONING` statuslu qeyd yaradılır;
2. `CREATE ROLE t_<slug>_app LOGIN PASSWORD '<təsadüfi>'` və `CREATE DATABASE loyalty_t_<slug> OWNER t_<slug>_owner` icra olunur, `CONNECT` hüququ `PUBLIC`-dən alınır;
3. tenant miqrasiyaları (Flyway, `db/migration/tenant`) işə salınır;
4. şirkət profili, standart qaydalar və DEK yaradılır;
5. status `ACTIVE` olur; OWNER-ə dəvət göndərilir.

Proses idempotentdir: yarımçıq qalarsa, təkrar işə salındıqda qaldığı yerdən davam edir. Bu əməliyyatlar üçün `loyalty_provisioner` rolu (`CREATEDB`, `CREATEROLE`) istifadə olunur. Prod-da provisioning-i ayrıca, birdəfəlik əmr kimi işlətmək tövsiyə olunur; bu halda işləyən tətbiqdə bu hüquq olmur (§17).

**Miqrasiyalar:** tətbiq başlayanda əvvəlcə platforma, sonra bütün aktiv tenant bazaları miqrasiya olunur. Bir tenant-ın miqrasiyası uğursuz olarsa, həmin tenant `MIGRATION_FAILED` statusu alır və xidmət göstərmir (fail closed). Digər tenant-lar işləməyə davam edir.

### 3.4 Backend modulları

| Modul | Məsuliyyət |
|---|---|
| `shared` | Problem Details xəta formatı, i18n, `Clock`, idempotentlik, ümumi baza sinifləri |
| `security` | Spring Security konfiqurasiyası, CSRF, başlıqlar, rate limiting, kriptoqrafiya (KEK/DEK, AES-GCM, HMAC, token generatoru) |
| `tenancy` | Tenant reyestri, domenlər, `TenantResolutionFilter`, `TenantContext`, `TenantRoutingDataSource`, connection pool-lar, provisioning, tenant miqrasiyaları |
| `company` | Şirkət profili (tenant daxilində), filiallar |
| `customer` | OTP, qeydiyyat, sessiyalar, cihazlar, razılıqlar, hesabın silinməsi |
| `staff` | Şirkət işçiləri: giriş, TOTP, bloklanma, şifrə bərpası, dəvətlər, rollar |
| `nfc` | Çiplər, çip açarları, `TagVerifier`, toxunuş endpoint-i, `tap_token`-lər |
| `loyalty` | Möhürlər, mükafat qaydaları, sui-istifadə siyasəti, mükafatlar, mükafatın istifadəsi (redemption) |
| `analytics` | İdarəetmə paneli sorğuları |
| `audit` | Audit jurnalı, toxunuş hadisələri, fırıldaq işarələri |
| `notification` | `SmsProvider`, `MailSender`, 3 dildə şablonlar |
| `platform` | Platforma adminləri (SUPER_ADMIN), tenant-ların və domenlərin idarəsi |
| `dev` | Yalnız `dev` profili: simulyator, SMS/email outbox, test məlumatları |

Hər modulun daxili quruluşu: `api` (controller + DTO), `domain` (entity, enum), `service`, `repository`, `mapper` (MapStruct). ArchUnit ilə yoxlanılan qaydalar:

- controller-lər entity qaytarmır, yalnız DTO qaytarır;
- modullar bir-birinin `internal` paketlərinə müraciət etmir;
- heç bir paket `dev` paketinə istinad etmir;
- platforma entity-ləri ilə tenant entity-ləri ayrı paketlərdədir və bir-birinə istinad etmir;
- texniki sətir əməliyyatlarında `toUpperCase()` və `toLowerCase()` yalnız `Locale.ROOT` ilə çağırılır (§11.6).

### 3.5 Sorğunun backend-dəki yolu

```
nginx → RemoteIpValve (X-Forwarded-For yalnız etibarlı proxy-dən qəbul olunur)
      → TraceId → TenantResolutionFilter (Host → tenant və ya platforma konteksti)
      → Security filter chain: başlıqlar → Origin yoxlaması → CSRF → rate limit
          → CustomerSessionFilter | StaffSessionFilter | PlatformSessionFilter
      → Controller (@Valid DTO) → Service (@Transactional, AccessPolicy) → Repository (tenant bazası)
      → ExceptionHandler → Problem Details (lokallaşdırılmış title/detail + sabit code)
```

Ayrı-ayrı `SecurityFilterChain`-lər:

| Zəncir | Kontekst | Yollar | Autentifikasiya |
|---|---|---|---|
| tap | tenant | `/t/**` | yoxdur, yoxlama kriptoqrafikdir |
| public | tenant | `/api/v1/public/**`, `/api/v1/csrf`, `/manifest.webmanifest`, `GET /api/v1/taps/{token}` | yoxdur |
| customer | tenant | `/api/v1/auth/**`, `/api/v1/me/**`, `/api/v1/taps/**`, `/api/v1/rewards/**`, `/api/v1/redemptions/**` | müştəri sessiyası (auth endpoint-ləri istisna olmaqla) |
| staff | tenant | `/api/v1/admin/**` | işçi sessiyası + rol |
| dev | tenant | `/api/v1/dev/**` | yalnız `dev` profilində mövcuddur, işçi sessiyası tələb edir |
| platform | platforma | `/api/v1/platform/**` | platforma admin sessiyası |
| actuator | hər ikisi | `/actuator/health/**` | açıq; digər actuator endpoint-ləri bağlıdır |
| default | — | qalan hər şey | `denyAll` (hamısı qadağandır) |

Tenant zəncirləri platforma domenində, platforma zənciri isə tenant domenində `404` qaytarır.

---

## 4. Qovluq strukturu

```
tapstamp/
├── README.md                        # Azərbaycanca: quraşdırma, diaqram, simulyasiya, NFC, tərcümələr
├── CLAUDE.md                        # layihə qaydaları (dil, konvensiyalar)
├── .env.example                     # bütün konfiqurasiya; sirr dəyərləri boşdur
├── docker-compose.yml               # postgres, backend, frontend, nginx
├── docker-compose.prod.yml          # prod əlavələri: certbot, sərt limitlər
├── .github/workflows/ci.yml         # build, testlər, asılılıq skanı, SBOM
├── scripts/
│   ├── gen-secrets.ps1 / .sh        # .env üçün təsadüfi açarlar
│   ├── gen-dev-certs.ps1 / .sh      # *.localhost dev domenləri üçün TLS sertifikatları
│   └── issue-cert.sh                # prod: domen üçün Let's Encrypt sertifikatı
├── infra/
│   ├── nginx/
│   │   ├── nginx.conf
│   │   ├── conf.d/app.conf          # SNI ilə sertifikat seçimi, marşrutlar, limit_req
│   │   ├── snippets/security-headers.conf
│   │   └── certs/<domen>/           # TLS sertifikatları (.gitignore-dadır)
│   └── postgres/init/01-platform.sh # platforma bazası və rollar
├── be-tapstamp/
│   ├── pom.xml, mvnw, mvnw.cmd, Dockerfile
│   └── src/
│       ├── main/java/az/tapstamp/
│       │   ├── TapStampApplication.java
│       │   ├── shared/              # error, i18n, time, web, persistence, idempotency
│       │   ├── security/            # config, csrf, headers, ratelimit, crypto
│       │   ├── tenancy/             # registry, resolution, hibernate, provisioning, migration
│       │   ├── company/             # şirkət profili, filiallar
│       │   ├── customer/            # auth (otp, register), session, profile, consent, deletion
│       │   ├── staff/               # auth, mfa, user, team, session
│       │   ├── nfc/                 # tag, key, verify (TagVerifier, Ntag424SunVerifier, sun/), tap
│       │   ├── loyalty/             # stamp, policy, rule, reward, redemption, account
│       │   ├── analytics/
│       │   ├── audit/
│       │   ├── notification/        # sms, mail, templates
│       │   ├── platform/            # platforma adminləri, tenant-ların idarəsi
│       │   ├── dev/                 # @Profile("dev"): DevSimulatorVerifier, simulyator, outbox, seed
│       │   └── config/              # ProdSafetyGuard, OpenApiConfig, AppProperties
│       ├── main/resources/
│       │   ├── application.yml, application-dev.yml, application-prod.yml
│       │   ├── db/migration/platform/   # platforma bazasının Flyway miqrasiyaları
│       │   ├── db/migration/tenant/     # tenant bazalarının Flyway miqrasiyaları
│       │   ├── i18n/messages_az.properties, messages_en.properties, messages_ru.properties
│       │   └── security/common-passwords.txt
│       └── test/java/az/tapstamp/    # unit, *IT (Testcontainers), arch (ArchUnit)
├── fe-tapstamp/
│   ├── package.json, vite.config.ts, tsconfig.json, eslint.config.js
│   ├── index.html                   # müştəri giriş nöqtəsi (/)
│   ├── admin/index.html             # işçi və platforma paneli (/admin)
│   ├── public/                      # ikonlar, şriftlər
│   └── src/
│       ├── customer/                # main.tsx, routes, pages, features (auth, card, tap, rewards, settings)
│       ├── admin/                   # main.tsx, routes, pages, features (dashboard, customers, staff, settings, audit, platform, dev)
│       ├── shared/
│       │   ├── api/                 # schema.d.ts (generasiya olunur), client.ts, errors.ts
│       │   ├── i18n/                # init, dil müəyyənləşdirmə, i18next.d.ts (tip təhlükəsizliyi)
│       │   ├── ui/                  # shadcn/ui komponentləri
│       │   ├── lib/                 # format.ts (Intl), phone.ts, cn.ts
│       │   └── styles/              # tema tokenləri (yalnız açıq rejim, qərar 40)
│       └── locales/{az,en,ru}/{common,auth,profile,admin,errors}.json
├── e2e/                             # Playwright: playwright.config.ts, tests/, fixtures/
└── docs/
    ├── architecture-plan.md         # bu sənəd
    ├── architecture.md              # yekun arxitektura təsviri
    ├── security.md                  # təhlükəsizlik modeli və yekun icmal
    ├── nfc-ntag424-programming.md   # real çipin SUN ilə proqramlaşdırılması
    ├── tenant-onboarding.md         # yeni şirkətin və domenin qoşulması
    ├── i18n-guide.md                # yeni tərcümə açarının əlavə edilməsi, terminologiya
    ├── error-codes.md               # xəta kodları kataloqu
    └── metrics.md                   # panel göstəricilərinin dəqiq tərifləri
```

---

## 5. Verilənlər bazası sxemi

### 5.1 Ümumi qaydalar

- **İki növ baza:** bir `loyalty_platform` bazası və hər şirkət üçün bir `loyalty_t_<slug>` bazası. Tenant bazalarının sxemi eynidir və eyni Flyway miqrasiyaları ilə idarə olunur.
- **Əsas açar:** `id UUID` (UUIDv7, tətbiqdə yaradılır).
- **Vaxt:** `TIMESTAMPTZ`, UTC. JVM `-Duser.timezone=UTC` ilə işləyir, `hibernate.jdbc.time_zone=UTC`, Java tərəfində `Instant`.
- **Enum-lar:** `TEXT` + `CHECK` məhdudiyyəti. PostgreSQL enum tipindən istifadə etmirəm, çünki onun miqrasiyası çətindir.
- **Çoxdilli məzmun:** `JSONB` formatında `{"az": "...", "en": "...", "ru": "..."}`. `az` məcburidir (`CHECK (x ? 'az')`). Tərcümə yoxdursa, az versiyası göstərilir.
- **Tenant bazasında `company_id` sütunu yoxdur:** baza özü bir şirkətə məxsusdur. Bu, sorğuları sadələşdirir və "filtri unutmaq" səhvini aradan qaldırır.
- **Genişlənmələr:** `citext` (registrdən asılı olmayan email və domen), `pg_trgm` (ad üzrə axtarış).
- **Optimistik kilid:** tez-tez dəyişən aqreqatlarda `version BIGINT`.
- Bütün dəyişikliklər Flyway miqrasiyaları ilədir; tətbiq sxemi heç vaxt özü dəyişmir (`ddl-auto=validate`).

### 5.2 Platforma bazası (`loyalty_platform`)

```sql
tenants (
  id uuid PK, slug citext UNIQUE NOT NULL,        -- "mockcoffee"; DB adında istifadə olunur
  display_name jsonb NOT NULL,                     -- {az,en,ru}, yalnız platforma paneli üçün
  status text,       -- PROVISIONING|ACTIVE|SUSPENDED|MIGRATION_FAILED|DECOMMISSIONED
  db_host text, db_port int, db_name text,
  db_username text, db_password_enc bytea,         -- tenant rolunun şifrəsi, envelope encryption
  schema_version text, created_at, updated_at, version
)

tenant_domains (
  id, tenant_id → tenants, hostname citext UNIQUE NOT NULL,   -- "bonus.mockcoffee.az"
  is_primary bool, tls_status text,                -- PENDING|ISSUED|FAILED
  created_at,
  UNIQUE (tenant_id) WHERE is_primary
)

platform_admins (
  id, email citext UNIQUE, password_hash text,     -- Argon2id
  display_name, preferred_language, status,
  totp_secret_enc bytea, totp_enabled_at, totp_last_used_step bigint,   -- TOTP məcburidir
  failed_login_count int, locked_until, password_changed_at, last_login_at, created_at, updated_at
)
platform_admin_sessions (id, admin_id, token_hash UNIQUE, state, created_at, last_activity_at,
                         absolute_expires_at, mfa_verified_at, step_up_at, ip, user_agent, revoked_at)
platform_recovery_codes (id, admin_id, code_hash, used_at)
platform_audit_logs (id, actor_id, action, target_type, target_id, ip, user_agent, details jsonb, created_at)
data_encryption_keys (id, purpose, wrapped_key bytea, kek_id, status, created_at)   -- platforma DEK-i
rate_limit_buckets (id text PK, state bytea, expires_at)   -- Bucket4j; açarlarda telefon yalnız HMAC şəklində
shedlock (name PK, lock_until, locked_at, locked_by)
```

### 5.3 Tenant bazası (`loyalty_t_<slug>`)

#### Şirkət və filiallar

```sql
company_profile (                                -- tək sətir
  singleton bool PK DEFAULT true CHECK (singleton),
  name jsonb NOT NULL, description jsonb,
  timezone text NOT NULL DEFAULT 'Asia/Baku',
  default_language text NOT NULL DEFAULT 'az',     -- az|en|ru
  allowed_phone_regions text[] NOT NULL DEFAULT '{AZ}',   -- SMS fırıldağına qarşı
  sms_sender_name text,                            -- məs. "MockCoffee"
  updated_at, version
)

branches (
  id, name jsonb NOT NULL, address jsonb, description jsonb,
  business_hours jsonb,     -- {"mon":[["08:00","22:00"]], …}; istəyə bağlı iş saatları qaydası üçün
  status text,              -- ACTIVE|CLOSED
  created_at, updated_at, version
)
```

#### İşçilər

```sql
staff_users (
  id, email citext UNIQUE NOT NULL,
  password_hash text NOT NULL,              -- Argon2id, PHC formatında
  display_name text, preferred_language text NOT NULL,
  status text,                              -- ACTIVE|DISABLED|INVITED
  totp_secret_enc bytea NULL, totp_enabled_at, totp_last_used_step bigint,
  failed_login_count int NOT NULL DEFAULT 0, locked_until timestamptz NULL,
  password_changed_at, last_login_at, created_at, updated_at, version
)

staff_roles (                               -- rol təyinatları
  id, staff_user_id → staff_users,
  role text,                                -- COMPANY_OWNER|BRANCH_MANAGER|STAFF
  branch_id NULL → branches,                -- MANAGER və STAFF üçün məcburidir
  UNIQUE NULLS NOT DISTINCT (staff_user_id, role, branch_id)
)

staff_sessions (
  id, staff_user_id, token_hash bytea UNIQUE,   -- SHA-256(token); tokenin özü saxlanılmır
  state text,                                   -- PENDING_MFA|MFA_ENROLLMENT|ACTIVE
  created_at, last_activity_at, absolute_expires_at, mfa_verified_at, step_up_at,
  ip inet, user_agent text, revoked_at, revoke_reason
)

staff_recovery_codes  (id, staff_user_id, code_hash, used_at)
staff_invitations     (id, email, role, branch_id, token_hash UNIQUE, expires_at, accepted_at, invited_by, created_at)
password_reset_tokens (id, staff_user_id, token_hash UNIQUE, expires_at, used_at, requested_ip, created_at)
```

#### Müştərilər

```sql
customers (
  id,
  phone_enc    bytea NULL,     -- AES-256-GCM; silinəndə NULL
  phone_hash   bytea NULL,     -- HMAC-SHA256(tenant phone key, E.164), axtarış üçün
  phone_masked text NULL,      -- "+994 50 ***-**-67"; siyahı üçün deşifrə lazım olmur
  name text NULL,
  preferred_language text NOT NULL,
  status text,                 -- ACTIVE|BLOCKED|DELETED
  registered_at, last_visit_at, last_branch_id, deleted_at, created_at, updated_at, version,
  UNIQUE (phone_hash) WHERE phone_hash IS NOT NULL
)
INDEX (last_visit_at DESC), (registered_at DESC), (preferred_language), GIN (name gin_trgm_ops)

customer_sessions (
  id, customer_id,
  access_token_hash bytea UNIQUE, access_expires_at,
  refresh_expires_at,                          -- sürüşən 180 gün
  device_label text,                           -- "iPhone · Safari" (User-Agent-dən)
  user_agent, created_ip inet, last_ip inet,
  created_at, last_seen_at, revoked_at, revoke_reason
)

customer_refresh_tokens (                      -- rotasiya tarixçəsi; təkrar istifadəni aşkarlamaq üçün
  id, session_id → customer_sessions, token_hash bytea UNIQUE,
  issued_at, expires_at, consumed_at
)

otp_challenges (
  id, phone_hash, phone_enc bytea,             -- qeydiyyatda müştəri yaratmaq üçün
  code_hash bytea,                             -- HMAC-SHA256(APP_OTP_PEPPER, id ‖ code)
  language text, attempts int DEFAULT 0, max_attempts int DEFAULT 5,
  status text,                                 -- ACTIVE|VERIFIED|EXPIRED|LOCKED|SUPERSEDED
  tap_token_id NULL,                           -- giriş toxunuşla başlayıbsa
  registration_token_hash NULL, registration_expires_at NULL,
  ip inet, created_at, expires_at, verified_at
)
INDEX (phone_hash, created_at DESC)

legal_documents (
  id, type text,                               -- PRIVACY_POLICY|TERMS
  doc_version int, content jsonb,              -- {az,en,ru}, Markdown
  published_at, is_current bool,
  UNIQUE (type, version)
)

consents (
  id, customer_id, document_id, document_type, document_version,
  language, accepted_at, ip inet, user_agent, withdrawn_at
)
```

`phone_hash` üçün açar HKDF ilə tenant-a görə törədilir: `HKDF(APP_PHONE_HMAC_KEY, tenant_id)`. Nəticədə eyni nömrənin iki şirkətdəki hash-ləri fərqlidir, iki baza sızsa belə müştəriləri bir-birinə bağlamaq olmur.

#### NFC

```sql
nfc_tags (
  id, branch_id → branches,
  uid bytea UNIQUE NOT NULL,                   -- 7 bayt
  type text,                                   -- NTAG424_SUN|SIMULATED
  label text,                                  -- "Piştaxta 1"
  status text,                                 -- ACTIVE|DISABLED
  last_counter int NOT NULL DEFAULT -1,        -- son qəbul olunmuş SDMReadCtr (24 bit)
  last_tap_at, created_at, disabled_at, disabled_by, version
)

nfc_tag_keys (
  id, tag_id → nfc_tags, key_version int,
  status text,                                 -- PENDING|ACTIVE|RETIRED
  app_master_key_enc bytea,                    -- K0
  meta_read_key_enc  bytea,                    -- K1
  file_read_key_enc  bytea,                    -- K2
  dek_id → data_encryption_keys,
  provisioning_exported_at, activated_at, retired_at, created_by, created_at,
  UNIQUE (tag_id) WHERE status = 'ACTIVE',
  UNIQUE (tag_id) WHERE status = 'PENDING'
)

tap_tokens (
  id, token_hash bytea UNIQUE,                 -- SHA-256; tokenin özü saxlanılmır
  branch_id, tag_id, tap_event_id,
  binding_hash bytea,                          -- brauzer identifikatoru cookie-sinin hash-i
  status text,                                 -- ACTIVE|RESERVED|CONSUMED|REJECTED|EXPIRED
  expires_at,                                  -- +60 saniyə
  reserved_challenge_id NULL, reserved_until NULL,   -- giriş və qeydiyyat üçün +10 dəqiqə
  consumed_at, consumed_by_customer_id, created_at
)

tap_events (                                   -- HƏR cəhd: həm uğurlu, həm rədd edilən
  id, branch_id NULL, tag_id NULL,             -- naməlum çipdə NULL
  stage text,                                  -- VERIFY|CLAIM
  outcome text,                                -- ACCEPTED|REJECTED
  reason text NULL,                            -- COUNTER_REPLAY, CMAC_INVALID, STAMP_COOLDOWN_ACTIVE, …
  counter int NULL, counter_gap int NULL,
  customer_id NULL, tap_token_id NULL, stamp_id NULL,
  ip inet NULL, browser_id_hash bytea NULL, user_agent_hash bytea NULL,
  created_at
)
UNIQUE (tag_id, counter) WHERE stage = 'VERIFY' AND outcome = 'ACCEPTED'   -- replay-ə qarşı ikinci qat
INDEX (created_at DESC), (tag_id, created_at DESC), (customer_id, created_at DESC)
```

#### Loyallıq

```sql
reward_rules (                                 -- versiyalı mükafat qaydası
  id, rule_version int UNIQUE,
  stamps_required int CHECK (stamps_required BETWEEN 1 AND 50),   -- məs. 9 möhür → 10-cu qəhvə pulsuz
  reward_name jsonb NOT NULL, reward_description jsonb,
  active bool, created_by, created_at,
  UNIQUE (active) WHERE active
)

stamp_policies (                               -- sui-istifadə qaydaları
  id, branch_id NULL,                          -- NULL = şirkət üzrə standart; doluysa filial üzrə override
  min_interval_minutes int DEFAULT 20,
  max_stamps_per_day int DEFAULT 3,
  require_staff_approval bool DEFAULT false,
  pending_timeout_minutes int DEFAULT 30,
  enforce_business_hours bool DEFAULT false,
  max_taps_per_tag_per_minute int DEFAULT 10,
  updated_by, updated_at, version,
  UNIQUE NULLS NOT DISTINCT (branch_id)
)

loyalty_accounts (
  id, customer_id UNIQUE → customers,
  current_stamps int CHECK (current_stamps >= 0),   -- cari kartdakı möhürlər
  total_stamps int, rewards_earned int, rewards_redeemed int,
  last_stamp_at, created_at, updated_at, version
)

stamps (
  id, branch_id, customer_id, tag_id NULL,
  tap_token_id NULL UNIQUE,                    -- idempotentlik: bir token = ən çox bir möhür
  source text,                                 -- NFC_TAP|MANUAL
  status text,                                 -- PENDING|CONFIRMED|REJECTED|REVOKED
  local_date date,                             -- şirkətin saat qurşağında tarix (gündəlik limit üçün)
  reason text NULL, created_by_staff_id NULL,  -- əl ilə düzəlişdə məcburi səbəb
  confirmed_at, confirmed_by, revoked_at, revoked_by, revoke_reason,
  reward_id NULL,                              -- bu möhür hansı mükafatı tamamladı
  created_at
)
INDEX (created_at)                                      -- panel qrafikləri
INDEX (branch_id, created_at)
INDEX (customer_id, created_at DESC)                    -- cooldown, tarixçə
INDEX (customer_id, local_date) WHERE status IN ('PENDING','CONFIRMED')
INDEX (branch_id, created_at) WHERE status = 'PENDING'  -- işçi növbəsi

rewards (
  id, customer_id, rule_id,
  stamps_required_snapshot int, reward_name_snapshot jsonb,   -- qayda dəyişsə belə, qazanılan saxlanılır
  status text,                                 -- AVAILABLE|REDEEMED|REVOKED
  earned_at, redeemed_at, created_at
)
INDEX (customer_id, status), (earned_at), (redeemed_at)

redemptions (
  id, reward_id, customer_id,
  code_hash bytea,                             -- HMAC(6 rəqəmli kod)
  qr_token_hash bytea UNIQUE,                  -- QR üçün 128 bit təsadüfi token
  status text,                                 -- PENDING|COMPLETED|EXPIRED|CANCELLED
  expires_at,                                  -- +2 dəqiqə
  branch_id NULL, confirmed_by NULL, confirmed_at, created_at,
  UNIQUE (code_hash) WHERE status = 'PENDING',
  UNIQUE (reward_id) WHERE status = 'PENDING'
)
```

#### Audit və sistem

```sql
audit_logs (                                   -- yalnız əlavə olunur (append-only)
  id, actor_type text,                         -- STAFF|CUSTOMER|PLATFORM|SYSTEM
  actor_id NULL,
  action text,                                 -- CUSTOMER_PHONE_REVEALED, CUSTOMERS_EXPORTED,
                                               -- STAMP_MANUAL_ADJUSTED, TAG_KEY_ROTATED, STAFF_LOGIN_FAILED, …
  target_type, target_id, ip inet, user_agent,
  details jsonb,                               -- PII-siz (telefon yalnız maskalı)
  created_at
)
INDEX (created_at DESC), (actor_id, created_at DESC), (action, created_at DESC)

fraud_flags (
  id, customer_id NULL, tag_id NULL,
  type text,        -- REPLAY_ATTEMPT|COUNTER_GAP|RAPID_MULTI_ACCOUNT|COOLDOWN_HAMMERING|BINDING_MISMATCH
  severity text,    -- LOW|MEDIUM|HIGH
  details jsonb, created_at, resolved_at, resolved_by, resolution_note
)

data_encryption_keys (id, purpose text, wrapped_key bytea, kek_id text, status text, created_at)   -- PII|NFC_KEYS|TOTP
idempotency_keys (scope, key, request_hash, response_status, response_body jsonb, created_at, expires_at, PK (scope, key))
```

### 5.4 Əlaqələrin xülasəsi

```
platforma:  tenants 1─* tenant_domains          platform_admins 1─* platform_admin_sessions

tenant:     company_profile (tək sətir)
            branches 1─* nfc_tags 1─* nfc_tag_keys
                              └─* tap_events, tap_tokens
            customers 1─1 loyalty_accounts
                ├─* stamps ──► rewards 1─* redemptions
                ├─* customer_sessions 1─* customer_refresh_tokens
                └─* consents ──► legal_documents
            staff_users 1─* staff_roles, staff_sessions, staff_recovery_codes
            reward_rules (bir aktiv versiya), stamp_policies
            audit_logs, fraud_flags, data_encryption_keys
```

### 5.5 DB rolları və hüquqlar (minimal səlahiyyət prinsipi)

| Rol | Hüquqları |
|---|---|
| `loyalty_platform_owner` | Platforma sxeminin sahibi; platforma miqrasiyaları bu rolla işləyir |
| `loyalty_platform_app` | Platforma bazasında yalnız DML |
| `loyalty_provisioner` | `CREATEDB`, `CREATEROLE`: yalnız yeni tenant bazası və rolu yaratmaq üçün |
| `t_<slug>_owner` | Tenant sxeminin sahibi; tenant miqrasiyaları bu rolla işləyir |
| `t_<slug>_app` | Yalnız öz bazasına `CONNECT`, yalnız DML. `audit_logs` və `tap_events` üzərində yalnız `INSERT` və `SELECT` icazəsi var |

Saxlama müddəti təmizliyi və hesab silinəndə IP-lərin silinməsi yalnız `SECURITY DEFINER` funksiyaları ilə edilir. Beləliklə tətbiq səhvən və ya ələ keçirilərək audit izlərini dəyişə bilməz.

---

## 6. NFC toxunuşunun təhlükəsizliyi

### 6.1 Problem

Adi NFC stikerində URL statikdir. Bir dəfə kopyalanan linki evdən istənilən qədər açmaq olar. Məqsəd: hər toxunuş yalnız **bir dəfə** və yalnız **həqiqi çipdən** gələn məlumatla möhür versin. Köhnə sorğunun təkrar göndərilməsi hücumu (**replay attack**) mümkün olmamalıdır.

### 6.2 NTAG 424 DNA SUN necə işləyir

NTAG 424 DNA çipi **SUN/SDM** funksiyasını dəstəkləyir (Secure Unique NFC / Secure Dynamic Messaging: çip hər oxunuşda URL-ə dinamik, kriptoqrafik imzalı məlumat yazır). Hər oxunuşda çip:

1. daxili **oxunma sayğacını** (SDMReadCtr, 24 bit) bir vahid artırır; sayğacı azaltmaq mümkün deyil;
2. UID və sayğacı **SDMMetaReadKey** ilə AES-128-CBC alqoritmi ilə şifrələyir və URL-ə `picc` parametri kimi yazır (PICCData, 16 bayt = 32 hex simvol);
3. **SDMFileReadKey**, UID və sayğacdan sessiya açarı törədir, **CMAC** hesablayır (AES əsaslı mesaj autentifikasiya kodu: açarı bilməyən saxtalaşdıra bilməz) və onun 8 baytını `cmac` parametri kimi yazır.

Çipə yazılan NDEF şablonu (domen şirkətin öz domenidir):

```
https://bonus.mockcoffee.az/t/04A1B2C3D4E5F6?picc=00000000000000000000000000000000&cmac=0000000000000000
                              └ UID (statik)       └ çip hər oxunuşda doldurur          └ çip doldurur
```

Domen tenant-ı müəyyən edir, `{tagUid}` isə həmin tenant-ın bazasında çipi tapmaq üçündür. `{tagUid}` proqramlaşdırma zamanı statik yazılır və **yalnız açarı tapmaq üçün ipucudur**. Ona etibar edilmir: etibarlı UID deşifrə olunmuş PICC məlumatından götürülür və uyğunluğu yoxlanılır.

Hər çip üçün 3 unikal, təsadüfi AES-128 açarı yaradılır:

| Açar | Təyinatı |
|---|---|
| K0 — AppMasterKey | çipin konfiqurasiyasını və açarlarını dəyişmək. NDEF faylına yazma hüququ yalnız bu açardadır, ona görə URL-i kənar şəxs dəyişə bilməz. |
| K1 — SDMMetaReadKey | PICCData-nın şifrələnməsi |
| K2 — SDMFileReadKey | CMAC üçün sessiya açarının törədilməsi |

### 6.3 Yoxlama alqoritmi (`Ntag424SunVerifier`)

1. **Format yoxlaması:** `uid` 14 hex, `picc` 32 hex, `cmac` 16 hex olmalıdır. Əks halda `TAP_MALFORMED` qaytarılır, DB-yə müraciət olunmur.
2. **Rate limit:** IP və çip üzrə.
3. Çip tenant bazasında `uid` ilə tapılır. Yoxdursa `TAG_UNKNOWN`, deaktivdirsə `TAG_DISABLED`.
4. ACTIVE açar dəsti götürülür. Rotasiya gedirsə və ACTIVE uğursuz olarsa, PENDING dəsti də yoxlanılır.
5. `PICCData = AES-128-CBC-Decrypt(K1, IV = 0, picc)`. Birinci bayt `PICCDataTag = 0xC7` olmalıdır (UID və sayğac əks olunub, UID uzunluğu 7). UID 1–7-ci, sayğac 8–10-cu baytlardadır (kiçik bayt əvvəldə, LSB first).
6. Deşifrə olunmuş UID çipin UID-i ilə eyni olmalıdır.
7. `SV2 = 3C C3 00 01 00 80 ‖ UID ‖ SDMReadCtr`, `K_ses = AES-CMAC(K2, SV2)`, `MAC = AES-CMAC(K_ses, ∅)`. Giriş boşdur, çünki SDMMACInputOffset = SDMMACOffset. Gözlənilən `cmac` MAC-ın 1, 3, 5, …, 15 indeksli baytlarıdır. Müqayisə **sabit zamanlı** aparılır (`MessageDigest.isEqual`), beləliklə cavab müddətinə görə təxmin etmək olmur.
8. **Sayğac yalnız CMAC doğru olduqdan sonra nəzərə alınır.** Əks halda saxta sorğu sayğacı "irəli itələyib" həqiqi toxunuşları bloklaya bilərdi.
9. **Replay qorunması** (ortaq `TapVerificationService`, bir tranzaksiyada):
   - `SELECT … FROM nfc_tags WHERE id = ? FOR UPDATE` (sətir kilidi);
   - `counter <= last_counter` olarsa `COUNTER_REPLAY` qaytarılır və `fraud_flags`-a yazılır;
   - əks halda `last_counter = counter` olur və `counter_gap = counter − last_counter − 1` qeyd edilir;
   - `tap_events`-ə VERIFY/ACCEPTED yazılır. `UNIQUE (tag_id, counter)` qismən indeksi **ikinci qatdır**: məntiqdə səhv olsa belə, DB eyni sayğacı iki dəfə qəbul etmir;
   - yeni `tap_token` yaradılır.
10. PENDING açarla uğurlu yoxlamada həmin tranzaksiyada PENDING açar ACTIVE, köhnəsi isə RETIRED olur. Bununla rotasiya tamamlanır.

Xarici cavabda dəqiq səbəb açıqlanmır. CMAC, UID və PICC xətaları istifadəçi üçün eyni koddur (`TAP_SIGNATURE_INVALID`). Belə olduqda hücumçu hansı addımı keçdiyini bilə bilmir. Dəqiq səbəb `tap_events`-də saxlanılır.

Kriptoqrafiya: AES/CBC/NoPadding standart JCE ilə, AES-CMAC isə BouncyCastle ilə. Unit testlər NXP-nin **AN12196** sənədindəki rəsmi test vektorlarından istifadə edir.

### 6.4 `TagVerifier` strategiyası, dev simulyatoru və prod qoruyucusu

```java
/** Çipdən gələn imzalı məlumatı yoxlayır. DB-yə yazmır; sayğac və replay məntiqi ortaq servisdədir. */
public interface TagVerifier {
    boolean supports(TagType type);
    VerifiedTap verify(NfcTag tag, TagKeys keys, TapParams params); // uid + counter və ya VerificationException
}
```

| Implementasiya | Profil | Çip növü | Kriptoqrafiya |
|---|---|---|---|
| `Ntag424SunVerifier` | hamısı | `NTAG424_SUN` | AES-128 + AES-CMAC (yuxarıdakı alqoritm) |
| `DevSimulatorVerifier` | yalnız `dev` | `SIMULATED` | `picc` = sayğac + təsadüfi baytlar, `cmac` = HMAC-SHA256(`APP_DEV_SIMULATOR_KEY`, uid ‖ picc) dəyərinin ilk 8 baytı |

Hər iki implementasiyada URL forması eynidir. Ona görə toxunuşdan sonrakı bütün axın (replay qorunması, `tap_token`, yönləndirmə, möhür) dev mühitində real mühitdəki kodla işləyir.

**Simulyator səhifəsi** (`/admin/dev/simulator`, yalnız işçi paneli üçün, yalnız `dev` profilində): çipi seçirsiniz və "Toxun" düyməsini basırsınız. Backend həmin şirkətin domeni və növbəti sayğacla imzalı URL yaradır, səhifə onu link və QR kod kimi göstərir. Linki eyni brauzerdə açmaq və ya QR-ı telefonla skan etmək olar. Seed-dəki bir `NTAG424_SUN` çipinin açarları dev-də məlumdur. Simulyator bu çipi **real NTAG 424 kriptoqrafiyası ilə emulyasiya edir**, beləliklə `Ntag424SunVerifier` də avadanlıqsız, uçdan-uca yoxlanılır. "Eyni URL-i təkrar aç" düyməsi replay qorunmasını nümayiş etdirir.

**Prod qoruyucusu** (`ProdSafetyGuard`). `prod` profili aktivdirsə, tətbiq başlayarkən aşağıdakıları yoxlayır:

- `dev` profili də aktivdirmi;
- kontekstdə `DevSimulatorVerifier`, simulyator controller-i və ya dev outbox bean-i varmı;
- `app.dev.*` parametrlərindən hər hansı biri aktivdirmi;
- SMS və ya email provayderi `console`-durmu;
- sirlər boşdurmu, çox qısadırmı, `.env.example`-dakı nümunə dəyərlərdirmi;
- cookie-lərdə `Secure` söndürülübmü; `*.localhost` domenləri reyestrdə aktivdirmi.

Bunlardan biri doğrudursa, `IllegalStateException` atılır və **tətbiq başlamır**. Əlavə qatlar: `dev` paketindəki bütün siniflər `@Profile("dev")` ilə işarələnib; ArchUnit başqa paketlərdən `dev`-ə istinadı qadağan edir; inteqrasiya testi `prod,dev` profilləri ilə kontekstin başlamadığını yoxlayır.

### 6.5 Toxunuş axını

```mermaid
sequenceDiagram
  autonumber
  participant P as Telefon (brauzer)
  participant N as nginx
  participant B as Backend
  participant D as Tenant bazası
  P->>N: GET https://bonus.mockcoffee.az/t/{uid}?picc=…&cmac=…
  N->>B: proxy (Host saxlanılır, + X-Forwarded-For)
  B->>B: Host → tenant; rate limit (IP, çip)
  B->>D: çip + şifrələnmiş açarlar (KEK → DEK → açar)
  B->>B: PICC deşifrəsi, CMAC yoxlaması (sabit zamanlı)
  B->>D: TX: çip FOR UPDATE · counter > last_counter? · last_counter yenilə · tap_events · tap_tokens (60 san)
  B-->>P: 303 Location: /tap/{tapToken} · Set-Cookie: brauzer identifikatoru · no-referrer · no-store
  P->>B: GET /api/v1/taps/{tapToken} → {filial, status}
  alt Müştəri daxil olub
    P->>B: POST /api/v1/taps/{tapToken}/claim (CSRF tokeni ilə)
    B->>D: TX: token FOR UPDATE · hesab FOR UPDATE · qaydalar · stamps INSERT · token CONSUMED
    B-->>P: 201 {stamp, card, rewardEarned}
    P->>P: "/" — yeni fincan animasiya ilə dolur ☕
  else Daxil olmayıb
    P->>B: POST /api/v1/auth/otp/request {phone, tapToken} → token RESERVED (10 dəq)
    P->>B: POST /api/v1/auth/otp/verify → (yeni müştəridirsə) POST /api/v1/auth/register
    B->>D: sessiya + möhür eyni tranzaksiyada
  end
```

### 6.6 `tap_token` dizaynı

- 256 bit təsadüfi dəyər (`SecureRandom`), base64url formatında. Bazada yalnız SHA-256 hash-i saxlanılır.
- Ömrü **60 saniyədir** və birdəfəlikdir: ACTIVE vəziyyətindən CONSUMED, REJECTED və ya EXPIRED vəziyyətinə keçir.
- **Brauzerə bağlanır.** `/t/` cavabında `__Host-cl_bid` cookie-si qoyulur (HttpOnly, brauzerin təsadüfi identifikatoru; yoxdursa yaradılır, ömrü 1 il). Tokendə bu dəyərin hash-i saxlanılır və claim zamanı uyğunluq yoxlanılır. Nəticədə `/tap/{token}` linkini başqasına göndərmək işə yaramır (`TAP_BINDING_MISMATCH`). Həmin identifikator fırıldaq analitikasında da istifadə olunur (bir brauzerdən çoxlu hesab).
- **Giriş üçün uzadılır.** SMS-in gəlməsi və kodun daxil edilməsi 60 saniyədən uzun çəkə bilər. Ona görə daxil olmamış istifadəçi OTP istəyəndə token həmin OTP sorğusuna **rezerv olunur** (RESERVED, 10 dəqiqə). Token yalnız həmin sorğunun uğurlu təsdiqi ilə istifadə oluna bilər. Möhürün vaxtı kimi toxunuşun vaxtı götürülür.
- **Yönləndirmə cavabı:** `303 See Other`, `Cache-Control: no-store`, `Referrer-Policy: no-referrer`. İmzalı parametrlər növbəti səhifənin ünvanında və Referer başlığında qalmır. Əsas qoruma isə sayğacdır: istifadə olunmuş `picc/cmac` cütü bir daha heç vaxt keçərli olmur.
- `HEAD` sorğuları və məlum link-preview botları sayğacı istehlak etmir.

### 6.7 Çip açarlarının idarəsi

| Əməliyyat | Necə işləyir |
|---|---|
| Qeydiyyat | OWNER UID-i, filialı və etiketi ("Piştaxta 1") daxil edir. Server 3 təsadüfi açar yaradır (PENDING) və şifrələnmiş saxlayır. |
| Provisioning paketi | OWNER üçün, TOTP step-up ilə. **Yalnız bir dəfə** yüklənir (JSON: şirkətin domeni ilə URL şablonu, ofsetlər, K0/K1/K2, SDM parametrləri) və auditə yazılır. |
| Proqramlaşdırma | NXP TagXplorer və PC/SC NFC oxuyucusu (məs. ACR1252U) ilə. Addım-addım təlimat `docs/nfc-ntag424-programming.md` faylında olacaq. |
| Aktivləşdirmə | Proqramlaşdırılmış çipə ilk toxunuş PENDING açarla doğrulanır və açar ACTIVE olur. |
| Açarın yenilənməsi | Yeni PENDING dəst yaradılır, çip cari K0 ilə yenidən proqramlaşdırılır, ilk uğurlu toxunuşda keçid avtomatik baş verir. Keçidə qədər köhnə açar işləyir, xidmət dayanmır. |
| Deaktivasiya | Dərhal qüvvəyə minir: bütün toxunuşlar `TAG_DISABLED` alır. |
| Domen dəyişikliyi | Şirkətin əsas domeni dəyişsə, çiplər yenidən proqramlaşdırılmalıdır (URL çipə yazılıb). Keçid dövründə köhnə domen də reyestrdə saxlanılır. |
| Gələcək üçün | PC/SC üzərindən çipi avtomatik proqramlaşdıran CLI. Bu halda açarları heç kim görmür. |

### 6.8 Envelope encryption (zərf şifrələməsi)

```
KEK — master açar (env: APP_CRYPTO_KEKS, identifikatorla; KMS və ya Vault-a köçürülə bilər)
  └─ wrap (AES-256-GCM) ─► DEK (hər tenant bazasında data_encryption_keys; məqsəd üzrə)
        └─ AES-256-GCM ─► sahə (telefon, çip açarları, TOTP sirri)

Şifrəli mətnin formatı: version(1) ‖ dek_id(16) ‖ nonce(12) ‖ ciphertext ‖ tag(16)
AAD = "<tenant id>/<cədvəl>.<sütun>:<sətir id>"   → şifrəli dəyəri başqa sətrə və ya başqa tenant-a köçürmək mümkün deyil
```

- `KeyEncryptionService` interfeysinin indiki implementasiyası `LocalKeyEncryptionService`-dir (KEK env-dən gəlir). Gələcəkdə `AwsKmsKeyEncryptionService` və ya `VaultTransitKeyEncryptionService` əlavə etmək olar.
- Açılmış DEK-lər yaddaşda qısa müddət (10 dəq) keşlənir.
- KEK rotasiyası üçün yeni KEK identifikatoru əlavə olunur və DEK-lər yenidən "wrap" edilir. Məlumatın özünü yenidən şifrələmək lazım gəlmir.
- DEK-lər tenant bazasındadır. Şirkət çıxanda bazanın silinməsi həm də **crypto-shredding**-dir (açarı silməklə şifrəli məlumatı oxunmaz etmək).

### 6.9 SUN-un tək başına həll etmədiyi hallar

- **Əvvəlcədən yığılmış URL-lər (pre-harvesting).** Hücumçu piştaxtada çipi NFC oxuyucu tətbiqi ilə bir neçə dəfə oxuyub URL-ləri açmadan saxlaya bilər. Server bu oxunuşları görmədiyi üçün onların sayğacı "təzə"dir və sonradan evdən istifadə oluna bilər. Lakin istənilən başqa müştərinin növbəti toxunuşu bütün belə URL-ləri dərhal etibarsız edir, çünki sayğac onları keçir. Əlavə qorunmalar: cooldown və gündəlik limit (zərər gündə ən çox 3 möhürdür, yəni real ziyarət qədər), istəyə bağlı iş saatları qaydası, `counter_gap` fırıldaq siqnalı, yüksək riskli filialda "işçi təsdiqi" rejimi.
- **Relay (real vaxtda ötürmə).** Kafedəki şərik URL-i oxuyub dərhal uzaqdakı birinə göndərir. Kriptoqrafiya bunu ayırd edə bilmir. Eyni qorunmalar tətbiq olunur, əlavə olaraq analitika bir çipdə qısa müddətdə çoxlu fərqli hesabı işarələyir.
- **Stikerin üstündən başqa stiker.** Çipin üstünə saxta (fişinq) NFC stiker yapışdırmaq mümkündür. Buna qarşı fiziki qoruma (çipin piştaxtaya daxil edilməsi, möhkəm bərkidilməsi) və işçilərin gündəlik yoxlaması lazımdır.

---

## 7. Müştəri autentifikasiyası və sessiyalar

### 7.1 Telefon nömrəsi

- libphonenumber ilə yoxlanılır və normallaşdırılır, standart region `AZ`-dir. `050 123 45 67`, `+994501234567` və `994 50 123-45-67` eyni `+994501234567` dəyərinə çevrilir (**E.164**: beynəlxalq standart nömrə formatı).
- Yalnız mobil nömrələr qəbul olunur. İcazəli ölkələr şirkət profilində təyin edilir (`allowed_phone_regions`, standart olaraq yalnız `AZ`). Bu, **SMS pumping**-ə (bahalı beynəlxalq nömrələrə SMS göndərtməklə edilən fırıldaq) qarşı qorunmadır.

### 7.2 OTP axını

```
1. POST /api/v1/auth/otp/request {phone, tapToken?}
   → rate limit (telefon, IP, tenant, qlobal) → normallaşdırma → əvvəlki ACTIVE sorğu SUPERSEDED olur
   → kod = SecureRandom.nextInt(1_000_000), 6 rəqəmə tamamlanır
   → code_hash = HMAC-SHA256(APP_OTP_PEPPER, challengeId ‖ code)   — kodun özü heç yerdə saxlanılmır
   → SMS commit-dən SONRA asinxron göndərilir (cavab müddəti nə SMS provayderindən, nə qeydiyyat statusundan asılıdır)
   ← 202 {challengeId, expiresAt (+5 dəq), resendAvailableAt (+60 san)}
     Nömrə qeydiyyatda olsa da, olmasa da cavab EYNİDİR.

2. POST /api/v1/auth/otp/verify {challengeId, code}
   → atomik yeniləmə:
       UPDATE otp_challenges SET attempts = attempts + 1
       WHERE id = ? AND status = 'ACTIVE' AND attempts < max_attempts AND expires_at > now()
       RETURNING …
     (paralel sorğularla 5 cəhd limitini keçmək olmur; 5-ci uğursuz cəhddən sonra LOCKED)
   → sabit zamanlı müqayisə
   ← mövcud müştəri: 200 {status: AUTHENTICATED, claim?} + sessiya cookie-ləri
   ← yeni nömrə:     200 {status: REGISTRATION_REQUIRED, registrationToken (10 dəq)}

3. POST /api/v1/auth/register {registrationToken, name?, acceptedDocuments[], language}
   ← 201 + sessiya cookie-ləri; rezerv olunmuş toxunuş varsa, möhür eyni tranzaksiyada verilir
```

Qeydiyyat statusu yalnız OTP təsdiqindən **sonra**, yəni nömrəyə sahiblik sübut olunanda bəlli olur. Beləliklə **enumeration** (nömrələri yoxlayaraq kimin qeydiyyatda olduğunu öyrənmək) mümkün olmur. Əlavə qayda: bir telefon üçün gündə ən çox 10 uğursuz yoxlama.

### 7.3 Rate limit-lər

| Hədəf | Limit (standart) | Qeyd |
|---|---|---|
| OTP göndərişi, telefon üzrə | 15 dəqiqədə 3, gündə 10 | tələbə uyğun |
| OTP göndərişi, IP üzrə | saatda 20 | kafenin Wi-Fi-ında çox müştəri eyni IP-dən gəlir, ona görə limit yumşaqdır |
| OTP göndərişi, tenant üzrə | dəqiqədə 100 | bir şirkətə hücum digərlərinin SMS limitini tükətmir |
| OTP göndərişi, qlobal | dəqiqədə 300 | SMS xərcinin qorunması |
| OTP yoxlanışı, IP üzrə | saatda 60 | |
| Uğursuz OTP yoxlanışı, telefon üzrə | gündə 10 | |
| `/t/` toxunuş, IP üzrə | dəqiqədə 30 | |
| `/t/` toxunuş, çip üzrə | `max_taps_per_tag_per_minute` | şirkət parametri |
| İşçi və platforma girişi, IP üzrə | 15 dəqiqədə 20 | hesab bloklanmasına əlavə |
| Şifrə bərpası, email üzrə | saatda 3 | |
| Mükafat kodunun yoxlanışı, işçi üzrə | dəqiqədə 30 | |

Bütün limitlər `application.yml` və env vasitəsilə dəyişdirilə bilər. Limit aşılanda `429` + `Retry-After` başlığı + `code: RATE_LIMITED` qaytarılır. Rate limit açarlarında telefon yalnız HMAC şəklində olur. Müştəri IP-si yalnız nginx-in `X-Forwarded-For` başlığından götürülür (etibarlı proxy siyahısı ilə), klient onu saxtalaşdıra bilməz. İnterfeysdə təkrar göndərmə düyməsi `resendAvailableAt` dəyərinə görə 60 saniyəlik taymerlə bağlı qalır.

### 7.4 Sessiyalar və cookie-lər

Bütün cookie-lər şirkətin öz domeninə bağlıdır.

| Cookie | Məzmunu | Atributları | Ömrü |
|---|---|---|---|
| `__Host-cl_at` | müştərinin access tokeni (256 bit, opaque) | HttpOnly; Secure; SameSite=Lax; `Path=/` | 15 dəq |
| `__Secure-cl_rt` | müştərinin refresh tokeni | HttpOnly; Secure; SameSite=Lax; `Path=/api/v1/auth` | 180 gün, sürüşən |
| `__Host-XSRF-TOKEN` | CSRF tokeni | Secure; SameSite=Lax; `Path=/` (JS oxuya bilir) | sessiya |
| `__Host-cl_bid` | brauzer identifikatoru (toxunuşu bağlamaq üçün) | HttpOnly; Secure; SameSite=Lax; `Path=/` | 1 il |
| `lang` | `az`, `en` və ya `ru` | Secure; SameSite=Lax; `Path=/` | 1 il |
| `__Host-cl_staff` | işçi sessiyası | HttpOnly; Secure; SameSite=Strict; `Path=/` | 8 saat, 30 dəq fəaliyyətsizlik |
| `__Host-cl_platform` | platforma admin sessiyası (yalnız platforma domenində) | HttpOnly; Secure; SameSite=Strict; `Path=/` | 8 saat, 30 dəq fəaliyyətsizlik |

`__Host-` prefiksi cookie-nin yalnız həmin domenə aid olmasını, `Secure` olmasını və subdomenlərdən dəyişdirilə bilməməsini brauzer səviyyəsində təmin edir. Refresh tokeni yalnız `/api/v1/auth` yollarına göndərilir.

**Refresh rotasiyası** (`POST /api/v1/auth/refresh`):

- Token aktivdirsə və vaxtı keçməyibsə, `consumed` kimi işarələnir, yeni access və refresh tokenlər verilir, `refresh_expires_at = now + 180 gün` olur.
- Token son 10 saniyədə artıq istifadə olunubsa, bu, iki tabın eyni anda yeniləmə etməsidir (**race condition**, yəni yarış vəziyyəti). Sessiya ləğv edilmir, `409 REFRESH_RACE` qaytarılır. Klient əsas sorğunu təkrarlayır; yeni cookie artıq brauzerdədir.
- Token daha əvvəl istifadə olunubsa, bu **təkrar istifadədir**: bütün sessiya ləğv olunur, audit və təhlükəsizlik hadisəsi yazılır, `401` qaytarılır.
- Frontend **single-flight** yeniləmə edir: eyni anda yalnız bir refresh sorğusu gedir, tablar arasında `navigator.locks` ilə əlaqələndirilir.

Digər qaydalar:

- Tokenlər bazada SHA-256 hash şəklində saxlanılır. Access token hər sorğuda DB-də yoxlanılır (ən çox 15 saniyəlik keşlə; ləğv ediləndə keş dərhal təmizlənir). Beləliklə ləğvetmə praktiki olaraq dərhal qüvvəyə minir.
- Hər girişdə yeni sessiya yaradılır, ona görə **session fixation** (hücumçunun əvvəlcədən verdiyi sessiyanı qurbana qəbul etdirməsi) mümkün deyil.
- **Cihazlar:** müştəri aktiv sessiyalarını görür (cihaz adı, yaranma vaxtı, son aktivlik, "bu cihaz" işarəsi). Birindən və ya "digər bütün cihazlardan" çıxış edə bilər.

### 7.5 CSRF qorunması

**CSRF** (başqa saytın istifadəçinin adından sorğu göndərməsi) üçün Spring Security-nin SPA rejimini istifadə edirəm: `CookieCsrfTokenRepository` (cookie adı `__Host-XSRF-TOKEN`) və BREACH hücumuna qarşı maskalama. Frontend bütün `POST/PUT/PATCH/DELETE` sorğularında `X-XSRF-TOKEN` başlığını göndərir. Əlavə qat kimi `Origin` və `Sec-Fetch-Site` başlıqları yoxlanılır: başqa saytdan gələn dəyişdirici sorğular rədd edilir. `/t/` endpoint-i zərurətdən `GET`-dir, çünki NFC linki GET ilə açır. Onun yan təsiri (sayğacın istehlakı) hücumçuya heç nə qazandırmır, çünki token qurbanın brauzerinə bağlanır.

### 7.6 Razılıq (consent)

- Yeni nömrə OTP ilə təsdiqləndikdən sonra "Qeydiyyatı tamamla" ekranı açılır: ad (istəyə bağlı) və **məcburi razılıq checkbox-u**. Checkbox-un yanında məxfilik siyasəti və istifadə şərtlərinin cari dildəki linkləri olur.
- `consents` cədvəlinə sənədin növü, versiyası, dil, vaxt, IP və User-Agent yazılır.
- Sənədin yeni versiyası dərc olunanda müştəri növbəti girişdə yeni razılıq ekranını görür.
- Sənədlər hər şirkətin bazasında 3 dildə və versiyalı saxlanılır. Seed-də Mock Coffee House üçün nümunə mətnlər olacaq. Hüquqi mətnləri hüquqşünas yoxlamalıdır.

### 7.7 Hesabın silinməsi

OTP ilə əlavə təsdiq (step-up) tələb olunur. Sonra bir tranzaksiyada:

- `customers`: `status = DELETED`; `phone_enc`, `phone_hash`, `phone_masked` və `name` NULL olur;
- bütün sessiyalar ləğv edilir, aktiv redemption-lar CANCELLED, AVAILABLE mükafatlar REVOKED olur;
- `otp_challenges` silinir, `tap_events`-dəki IP-lər `SECURITY DEFINER` funksiyası ilə silinir;
- `stamps`, `rewards`, `redemptions` sətirləri anonim müştəri ID-si ilə qalır, beləliklə **ümumi statistika saxlanılır**;
- `consents` sətrində `withdrawn_at` qeyd olunur;
- auditə `CUSTOMER_DELETED` yazılır (PII olmadan).

Eyni nömrə sonra yenidən qeydiyyatdan keçə bilər və boş, yeni hesab alır. Silmə yalnız həmin şirkətə aiddir.

### 7.8 SMS

- `SmsProvider { SendResult send(SmsMessage message); }` interfeysi.
- `ConsoleSmsProvider` (dev) OTP-ni loga və dev outbox-a yazır. Prod qoruyucusu bunu qadağan edir.
- `HttpSmsProvider` real provayder üçün yer tutucudur: timeout, bir təkrar cəhd və circuit breaker ilə. Göndərən adı şirkət profilindən götürülür (`sms_sender_name`).
- Mətn istifadəçinin dilindədir (`MessageSource`). **Diqqət:** təkcə rus dili deyil, Azərbaycan dili də UCS-2 tələb edir, çünki ə, ş, ğ, ı hərfləri GSM-7 əlifbasında yoxdur. Deməli, az və ru SMS-ləri 70 simvola, en isə 160 simvola sığmalıdır. Avtomatik test hər dildə ən uzun şirkət adı ilə SMS-in 1 seqmentə sığdığını yoxlayır.
- Nümunələr (dəqiq mətnlər tərcümə fayllarında olacaq):
  - az: `Mock Coffee kodu: 123456. Heç kimə deməyin.`
  - en: `Mock Coffee code: 123456. Do not share it.`
  - ru: `Код Mock Coffee: 123456. Никому не сообщайте.`
- Android üçün Web OTP sətri (`@bonus.mockcoffee.az #123456`) yer qalarsa əlavə olunur. iOS üçün input-da `autocomplete="one-time-code"` istifadə olunur.

---

## 8. Admin, işçi və platforma autentifikasiyası, rollar, izolyasiya

İki ayrı admin növü var:

- **Şirkət işçiləri** (COMPANY_OWNER, BRANCH_MANAGER, STAFF) şirkətin bazasında saxlanılır və yalnız şirkətin domenində (`/admin`) daxil olurlar.
- **Platforma adminləri** (SUPER_ADMIN) platforma bazasında saxlanılır və yalnız platforma domenində daxil olurlar. Onlar tenant-ları və domenləri idarə edir, amma **şirkətlərin müştəri məlumatlarına girişləri yoxdur**.

### 8.1 Giriş axını (hər iki növ üçün eyni mexanizm)

```
email + şifrə ──► uğursuz: ümumi xəta, sayğac artır
             ├─► TOTP aktivdir                       → PENDING_MFA (5 dəq) → TOTP və ya bərpa kodu → ACTIVE
             └─► OWNER/SUPER_ADMIN, TOTP qurulmayıb → MFA_ENROLLMENT (yalnız qeydiyyat endpoint-ləri açıqdır)
                                                      → QR → təsdiq kodu → 10 bərpa kodu → ACTIVE
Hər vəziyyət keçidində sessiya tokeni yenilənir.
```

- **Argon2id** (yaddaş tələb edən, GPU ilə sındırılması çətin şifrə hash alqoritmi): Spring Security-nin `Argon2PasswordEncoder` sinfi, OWASP parametrləri m=19 MiB, t=2, p=1. Parametrlər dəyişəndə hash girişdə avtomatik yenilənir.
- **Şifrə siyasəti** (NIST 800-63B): ən az 12, ən çox 128 simvol; geniş yayılmış şifrələr siyahısında olmamalı və email ilə eyni olmamalıdır. Məcburi "böyük hərf + rəqəm + simvol" qaydası yoxdur, çünki NIST bunu tövsiyə etmir.
- **Artan gözləmə ilə bloklanma:** 5 ardıcıl uğursuz cəhddən sonra 1 dəqiqə, hər növbəti uğursuzluqda müddət ikiqat artır (1 → 2 → 4 → … → ən çox 60 dəq). Uğurlu giriş sayğacı sıfırlayır. Mesaj həmişə ümumidir. Mövcud olmayan email üçün də eyni Argon2 hesablaması aparılır, ona görə cavab müddətinə görə hesabın mövcudluğunu bilmək olmur. Bundan əlavə IP üzrə limit var.
- **TOTP** (vaxta əsaslanan birdəfəlik kod, Google Authenticator və s.): RFC 6238, SHA-1, 6 rəqəm, 30 saniyə, ±1 addım tolerantlığı. 160 bitlik sirr envelope encryption ilə şifrələnir. `totp_last_used_step` eyni kodun ikinci dəfə istifadəsinin qarşısını alır. 10 birdəfəlik bərpa kodu verilir (hash kimi saxlanılır). TOTP hamı üçün könüllüdür (qərar 29); hesab səhifəsindən müvəqqəti söndürülə və ya silinə bilər. OWNER işçinin 2FA-sını sıfırlaya bilər, OWNER-in 2FA-sını isə yalnız SUPER_ADMIN platforma panelindən sıfırlaya bilər. Hər iki halda auditə yazılır.
- **Step-up** (həssas əməliyyat üçün əlavə təsdiq): tam telefonun göstərilməsi, CSV ixracı, çip provisioning paketi, rolların dəyişməsi və 2FA-nın sıfırlanması son 10 dəqiqədə TOTP təsdiqi tələb edir.
- **Şifrə bərpası:** `POST …/password/forgot` həmişə `202` qaytarır. Token 256 bitdir, hash kimi saxlanılır, 30 dəqiqə etibarlıdır və birdəfəlikdir. Email istifadəçinin dilində göndərilir, link şirkətin öz domenindədir. Bərpadan sonra bütün sessiyalar ləğv olunur və bildiriş emaili gedir. Bərpa 2FA-nı keçmir.
- **Dəvətlər:** OWNER işçini email ilə dəvət edir. İlk OWNER-i tenant yaradılanda SUPER_ADMIN dəvət edir. Link 72 saat etibarlıdır. İşçi şifrəni özü təyin edir, rol tələb edirsə TOTP da qurur. Bu üsulla heç kimə şifrə ötürülmür.
- **Sessiya:** mütləq ömür 8 saat, fəaliyyətsizlik limiti 30 dəqiqə. Fon sorğuları (məs. işçi panelinin avtomatik yenilənməsi) `X-Background: 1` başlığı ilə gedir və fəaliyyətsizlik taymerini uzatmır.

### 8.2 Rol matrisi

| İcazə | SUPER_ADMIN (platforma) | COMPANY_OWNER | BRANCH_MANAGER | STAFF |
|---|---|---|---|---|
| Tenant-ların yaradılması, dayandırılması, domenlər | ✅ | — | — | — |
| Tenant üzrə ümumi göstəricilər (yalnız saylar) | ✅ | — | — | — |
| OWNER dəvəti və OWNER-in 2FA-sının sıfırlanması | ✅ | — | — | — |
| Panel və analitika | — | ✅ bütün filiallar | ✅ öz filialı | — |
| Müştərilər cədvəli (maskalı telefon) | — | ✅ | ✅ | — |
| Tam telefonu göstər (audit) | — | ✅ + step-up | — | — |
| CSV ixracı (audit) | — | ✅ + step-up | — | — |
| Möhürə əl ilə düzəliş (səbəb + audit) | — | ✅ | ✅ öz filialı | — |
| Gözləyən möhürləri təsdiqləmək | — | ✅ | ✅ | ✅ öz filialı |
| Mükafatı təsdiqləmək (kod və ya QR) | — | ✅ | ✅ | ✅ |
| Mükafat qaydası, sui-istifadə limitləri | — | ✅ | — | — |
| NFC çipləri: qeydiyyat, deaktivasiya | — | ✅ | 👁 | — |
| Çip açarının yenilənməsi, provisioning | — | ✅ + step-up | — | — |
| İşçilərin idarəsi | — | ✅ | — | — |
| Audit jurnalı | platforma auditi | ✅ | — | — |
| Dev simulyatoru (yalnız dev) | — | ✅ | ✅ | — |

👁 = yalnız baxış.

### 8.3 İzolyasiya və IDOR testləri

1. **Şirkətlər arası izolyasiya** fizikidir və §3.2-dəki qatlarla təmin olunur: domen → tenant → ayrıca baza və ayrıca DB rolu.
2. **Şirkət daxilində filial əhatəsi:** MANAGER və STAFF yalnız öz filiallarının məlumatına çata bilər (`AccessPolicy.requireBranch(branchId)`).
3. Başqa filialın və ya mövcud olmayan resursun ID-si ilə sorğuya **404** qaytarılır (403 yox). Belə olduqda resursun mövcud olub-olmadığı da bilinmir.
4. **Testlər** (IDOR: başqasının obyektinə onun ID-si ilə müraciət etmək):
   - **tenant-lar arası:** Testcontainers-də iki tenant yaradılır. A-nın cookie-si ilə B-nin domeninə sorğu `401` alır. A-nın resurs ID-ləri ilə B-nin domeninə sorğu `404` alır. Host və SNI uyğunsuzluğu, tanınmayan domen və thread-lər arasında `TenantContext` sızması ayrıca yoxlanılır;
   - **avtomatik endpoint inventarı:** test `RequestMappingHandlerMapping`-dən bütün işçi endpoint-lərini götürür və başqa filialın işçisi ilə sorğu göndərir; gözlənilən cavab 404 və ya 403-dür. Yeni endpoint əlavə olunub siyahıda təsnif edilməyibsə, test **uğursuz olur**;
   - **rol matrisi testi:** hər rol × hər endpoint üçün gözlənilən 2xx və ya 403.

---

## 9. Loyallıq məntiqi: möhürlər, qaydalar, mükafatlar

### 9.1 Möhürün alınması (claim) tranzaksiyası

```
@Transactional   (tenant bazasında)
claim(tapToken, customer):
  t   = tap_tokens ... FOR UPDATE                 -- token hash-i ilə
  yoxla: status ACTIVE (və ya bu müştəri üçün RESERVED), vaxtı keçməyib, brauzer identifikatoru uyğundur
  t.status = CONSUMED və consumed_by == customer → əvvəlki nəticəni qaytar (idempotent, 200)
  acc = loyalty_accounts ... FOR UPDATE           -- eyni müştərinin sorğuları ardıcıl icra olunur
  policy = filial override-ı ?? şirkət standartı
  son PENDING/CONFIRMED möhürdən min_interval keçməyib   → STAMP_COOLDOWN_ACTIVE (retryAfterSeconds)
  bu gün (şirkətin saat qurşağında) möhür sayı >= max     → STAMP_DAILY_LIMIT_REACHED
  enforce_business_hours və filial bağlıdır                → OUTSIDE_BUSINESS_HOURS
  stamp = INSERT (require_staff_approval ? PENDING : CONFIRMED)
  CONFIRMED olduqda: acc.current_stamps++
      current_stamps == stamps_required → rewards AVAILABLE, current_stamps = 0, stamp.reward_id
  t.status = CONSUMED; customer.last_visit_at = now
  tap_events (CLAIM, ACCEPTED)
```

- Rədd halında token `REJECTED` olur, təkrar cəhd mümkün deyil. Rədd hadisəsi `tap_events`-ə **ayrıca tranzaksiyada** (`REQUIRES_NEW`) yazılır. Əsas tranzaksiya geri qaytarılsa da qeyd itmir.
- **İdempotentlik** (eyni əməliyyatın təkrarı nəticəni dəyişmir): `stamps.tap_token_id UNIQUE`. Eyni token eyni müştəri tərəfindən təkrar göndərilsə, eyni nəticə qaytarılır. Başqa müştəri göndərsə, `TAP_TOKEN_USED` qaytarılır.
- Cooldown və gündəlik limit PENDING möhürləri də sayır. Bu, təsdiq rejimində "gözləyənləri yığmağın" qarşısını alır.
- "Gün" şirkətin saat qurşağı ilə hesablanır (`local_date`). Bakıda gecəyarısı 00:00 UTC deyil, 20:00 UTC-dir. Testlər bu sərhədi ayrıca yoxlayır.

### 9.2 İşçi təsdiqi rejimi

- PENDING möhür təsdiqlənənə qədər karta əlavə olunmur.
- İşçi panelində öz filialının gözləyən möhürləri görünür (5 saniyədən bir yenilənir). İşçi təsdiqləyir və ya səbəb yazaraq rədd edir.
- `pending_timeout_minutes` (standart 30 dəq) keçəndə fon işi möhürü avtomatik rədd edir (səbəb `PENDING_TIMEOUT`).
- Müştəri profilində "Barista təsdiqi gözlənilir" vəziyyəti göstərilir.

### 9.3 Mükafatlar

- `current_stamps` `stamps_required` həddinə çatanda `rewards` cədvəlində AVAILABLE mükafat yaranır və kart sıfırlanır. Eyni anda bir neçə mövcud mükafat ola bilər.
- Qayda dəyişəndə yeni versiya yaradılır. Artıq qazanılmış mükafatlar öz snapshot-larını saxlayır. Yeni hədd müştərinin cari möhürlərindən kiçikdirsə, mükafatlar həmin tranzaksiyada dərhal verilir və auditə yazılır.

### 9.4 Mükafatın istifadəsi

```
Müştəri:  POST /api/v1/rewards/{rewardId}/redemptions
          → 6 rəqəmli kod (SecureRandom; aktiv kodlar arasında unikal — toqquşmada yenidən yaradılır)
          → QR məzmunu: "CLR1." + base64url(128 bit təsadüfi token)
          → ömür 2 dəqiqə; eyni mükafat üçün əvvəlki aktiv kod CANCELLED olur
İşçi:     POST /api/v1/admin/staff-console/redemptions/confirm {code | qrToken}
          → TX: redemption FOR UPDATE (PENDING, vaxtı keçməyib)
              → COMPLETED; reward REDEEMED; branch_id = işçinin filialı; confirmed_by
          → eyni sorğunun təkrarı eyni nəticəni qaytarır (idempotent)
          → ikinci, fərqli təsdiq: REDEMPTION_ALREADY_COMPLETED
Müştəri ekranı statusu 2 saniyədən bir yoxlayır → "Afiyət olsun! ☕"
```

**Kobud güc hücumu** (bütün kombinasiyaları sınamaq): kodu yalnız autentifikasiyalı işçi yoxlaya bilər, dəqiqədə 30 cəhd limiti var, yalnız öz şirkətinin PENDING kodları axtarılır (başqa şirkətin kodları ümumiyyətlə başqa bazadadır) və uğursuz cəhdlər auditə yazılır.

### 9.5 Əl ilə düzəliş

- `+N` və ya `−N`, məcburi səbəb (ən az 5 simvol), `Idempotency-Key` başlığı və audit tələb olunur.
- Silmə cari kartın son CONFIRMED möhürlərini REVOKED edir; say sıfırdan aşağı düşə bilməz.
- Silmə AVAILABLE mükafatı yaradan möhürü də əhatə edirsə, mükafat REVOKED olur və kart əvvəlki vəziyyətinə qaytarılır. REDEEMED mükafatı geri almaq mümkün deyil.

### 9.6 Fırıldaq işarələri

| Növ | Nə vaxt yaranır |
|---|---|
| `REPLAY_ATTEMPT` | köhnə sayğaclı URL yenidən göndərilib |
| `COUNTER_GAP` | sayğac böyük addımla artıb, yəni server görmədiyi oxunuşlar olub (əvvəlcədən yığılmış URL ola bilər) |
| `RAPID_MULTI_ACCOUNT` | bir çipdə və ya bir brauzerdən qısa müddətdə çoxlu fərqli hesab |
| `COOLDOWN_HAMMERING` | çoxlu cooldown rəddi |
| `BINDING_MISMATCH` | token başqa brauzerdən istifadə edilməyə çalışılıb |

İşarələr müştəri detalları səhifəsində görünür və qeyd yazılaraq "həll olundu" kimi bağlana bilər.

---

## 10. API siyahısı

Baza yolu `/api/v1`-dir. Tenant **domendən** müəyyən olunur, ona görə yollarda şirkət identifikatoru yoxdur. Bütün cavablar JSON-dur, xətalar `application/problem+json` formatındadır. Dəyişdirici sorğular CSRF tokeni tələb edir. OpenAPI təsvirləri Azərbaycan dilindədir. Swagger UI yalnız dev-də açıqdır.

### 10.1 İctimai və toxunuş (tenant domeni)

| Metod | Yol | Təsvir |
|---|---|---|
| GET | `/t/{tagUid}?picc&cmac` | Toxunuşu yoxlayır və `303 /tap/{token}` və ya `303 /tap/error?code=` cavabı verir |
| GET | `/api/v1/taps/{tapToken}` | Tokenin statusu və filial (yan təsiri yoxdur) |
| GET | `/api/v1/public/company` | Kafe, filiallar və mükafat qaydası (3 dildə) |
| GET | `/api/v1/public/legal/{type}` | Cari məxfilik siyasəti və ya şərtlər (3 dildə, versiya ilə) |
| GET | `/manifest.webmanifest` | Şirkətə xas PWA manifesti (ad, rənglər, ikonlar) |
| GET | `/api/v1/csrf` | CSRF cookie-sini təmin edir |

### 10.2 Müştəri autentifikasiyası — `/api/v1/auth`

| Metod | Yol | Təsvir |
|---|---|---|
| POST | `/otp/request` | OTP göndərir (`{phone, tapToken?}` → 202) |
| POST | `/otp/verify` | OTP-ni yoxlayır: `AUTHENTICATED` və ya `REGISTRATION_REQUIRED` |
| POST | `/register` | Qeydiyyatı tamamlayır: ad, razılıqlar, dil |
| POST | `/consents` | Sənədin yeni versiyasına razılıq |
| POST | `/refresh` | Refresh token rotasiyası |
| POST | `/logout` | Cari sessiyanı bağlayır |
| POST | `/step-up/otp/request`, `/step-up/otp/verify` | Həssas əməliyyat (hesabın silinməsi) üçün təkrar təsdiq |

### 10.3 Müştəri

| Metod | Yol | Təsvir |
|---|---|---|
| GET | `/api/v1/me` | Profil, kart, mövcud mükafatlar, qayda, gözləyən möhürlər, növbəti möhürün vaxtı |
| PATCH | `/api/v1/me` | Ad və dil |
| DELETE | `/api/v1/me` | Hesabın silinməsi (anonimləşdirmə, step-up ilə) |
| GET | `/api/v1/me/visits?cursor=` | Ziyarət tarixçəsi (keyset səhifələmə) |
| GET | `/api/v1/me/sessions` | Aktiv cihazlar |
| DELETE | `/api/v1/me/sessions/{sessionId}` | Cihazdan çıxış |
| POST | `/api/v1/me/sessions/revoke-others` | Digər bütün cihazlardan çıxış |
| POST | `/api/v1/taps/{tapToken}/claim` | Toxunuşa görə möhür alır |
| POST | `/api/v1/rewards/{rewardId}/redemptions` | Mükafat üçün kod və QR yaradır |
| GET | `/api/v1/redemptions/{redemptionId}` | Redemption statusu (polling) |
| DELETE | `/api/v1/redemptions/{redemptionId}` | Redemption-u ləğv edir |

### 10.4 İşçi autentifikasiyası və hesab — `/api/v1/admin`

| Metod | Yol | Təsvir |
|---|---|---|
| POST | `/auth/login` | Email + şifrə |
| POST | `/auth/mfa/verify` | TOTP və ya bərpa kodu |
| POST | `/auth/mfa/enrollment`, `/auth/mfa/enrollment/confirm` | TOTP qurulması |
| POST | `/auth/step-up` | Həssas əməliyyat üçün TOTP təsdiqi |
| POST | `/auth/logout` | Çıxış |
| POST | `/auth/password/forgot`, `/auth/password/reset` | Şifrə bərpası |
| POST | `/auth/invitations/accept` | Dəvətin qəbulu |
| GET, PATCH | `/me` | Profil, rollar, filiallar, dil, aktiv funksiyalar (məs. `devTools`) |
| POST | `/me/password` | Şifrənin dəyişdirilməsi |
| POST | `/me/mfa/recovery-codes` | Bərpa kodlarının yenilənməsi |
| GET, DELETE | `/me/sessions`, `/me/sessions/{id}` | İşçi sessiyaları |

### 10.5 Analitika — `/api/v1/admin/analytics` (OWNER, MANAGER; parametrlər: `from`, `to`, `branchId`)

| Metod | Yol | Təsvir |
|---|---|---|
| GET | `/overview` | Ümumi və aktiv (7/30/90 gün) müştərilər, bu həftə və ay yeni müştərilər, mükafat istifadə faizi |
| GET | `/stamps-series?granularity=DAY` (və ya `WEEK`) | Verilən möhürlər və istifadə olunan mükafatlar (xətti qrafik) |
| GET | `/heatmap` | Saat × həftənin günü üzrə möhürlər |
| GET | `/retention` | 30 gün ərzində geri qayıdanların faizi |
| GET | `/languages` | Müştərilərin dil üzrə bölgüsü |

Göstəricilərin dəqiq tərifləri `docs/metrics.md` faylında olacaq. Məsələn, **aktiv müştəri (N gün)** son N gündə ən azı bir CONFIRMED möhürü olan müştəridir. **Geri qayıtma** seçilmiş dövrdə qeydiyyatdan keçənlərdən ilk 30 gün ərzində başqa bir gündə yenidən gələnlərin faizidir.

### 10.6 Müştərilər — `/api/v1/admin/customers`

| Metod | Yol | Rol | Təsvir |
|---|---|---|---|
| GET | `` | OWNER, MANAGER | Səhifələmə, sıralama, axtarış (ad və ya tam telefon), filtrlər: dil, status, `inactiveDays`, filial, qeydiyyat tarixi |
| GET | `/{id}` | OWNER, MANAGER | Müştəri detalları |
| GET | `/{id}/timeline?cursor=` | OWNER, MANAGER | Möhür və mükafat tarixçəsinin zaman xətti |
| POST | `/{id}/phone-reveal` | OWNER + step-up | Tam nömrəni göstərir (auditə yazılır) |
| POST | `/{id}/stamp-adjustments` | OWNER, MANAGER | Əl ilə düzəliş (səbəb, `Idempotency-Key`, audit) |
| PATCH | `/{id}/status` | OWNER | Bloklamaq və ya blokdan çıxarmaq |
| GET | `/{id}/fraud-flags` | OWNER, MANAGER | Fırıldaq işarələri |
| POST | `/fraud-flags/{flagId}/resolve` | OWNER, MANAGER | İşarəni bağlamaq |
| POST | `/export` | OWNER + step-up | CSV ixracı (stream, audit, başlıqlar adminin dilində) |

### 10.7 İşçi konsolu — `/api/v1/admin/staff-console`

| Metod | Yol | Təsvir |
|---|---|---|
| GET | `/pending-stamps?branchId=` | Təsdiq gözləyən möhürlər |
| POST | `/pending-stamps/{stampId}/confirm` | Möhürü təsdiqləyir |
| POST | `/pending-stamps/{stampId}/reject` | Möhürü səbəblə rədd edir |
| POST | `/redemptions/confirm` | Mükafatı kod və ya QR ilə təsdiqləyir |
| GET | `/redemptions/recent` | Son təsdiqlər |

### 10.8 Parametrlər, çiplər, komanda, audit — `/api/v1/admin`

| Metod | Yol | Təsvir |
|---|---|---|
| GET, PUT | `/settings/company` | Kafe məzmunu (3 dildə), saat qurşağı, standart dil, SMS göndərən adı |
| GET, POST, PATCH | `/settings/branches[/{id}]` | Filiallar, iş saatları |
| GET, PUT | `/settings/reward-rule` | Mükafat qaydası (möhür sayı, adı 3 dildə) |
| GET, PUT | `/settings/stamp-policy` | Sui-istifadə limitləri (şirkət üzrə və filial override-ı) |
| GET, POST | `/settings/legal-documents` | Hüquqi sənədin yeni versiyasının dərci |
| GET, POST | `/tags` | Çiplərin siyahısı və qeydiyyatı |
| PATCH | `/tags/{id}` | Etiket, filial |
| POST | `/tags/{id}/disable`, `/tags/{id}/enable` | Deaktivasiya və aktivləşdirmə |
| POST | `/tags/{id}/key-rotations` | Yeni PENDING açar dəsti |
| POST | `/tags/{id}/provisioning-package` | Birdəfəlik provisioning paketi (step-up) |
| GET | `/tags/{id}/events` | Çipin toxunuş jurnalı |
| GET | `/team` | İşçilər |
| POST, DELETE | `/team/invitations[/{id}]` | Dəvət göndərmək və ləğv etmək |
| PATCH | `/team/{staffUserId}` | Rol, filiallar, status |
| POST | `/team/{staffUserId}/mfa-reset` | 2FA-nın sıfırlanması (step-up) |
| GET | `/audit-logs` | Audit jurnalı (filtrlər: icraçı, əməliyyat, tarix) |
| GET | `/tap-events` | Toxunuş jurnalı (rədd edilənlər və səbəbləri daxil) |

### 10.9 Platforma — `/api/v1/platform` (yalnız platforma domenində)

| Metod | Yol | Təsvir |
|---|---|---|
| POST | `/auth/login`, `/auth/mfa/verify`, `/auth/mfa/enrollment[/confirm]`, `/auth/logout` | Platforma admininin girişi (TOTP məcburidir) |
| GET, PATCH | `/me` | Profil, dil |
| GET, POST | `/tenants` | Tenant-ların siyahısı və yaradılması (provisioning) |
| PATCH | `/tenants/{id}` | Status (dayandırmaq, aktivləşdirmək) |
| GET | `/tenants/{id}/stats` | Yalnız ümumi saylar (müştəri, möhür) — fərdi məlumat yoxdur |
| POST, DELETE | `/tenants/{id}/domains[/{domainId}]` | Domenin qoşulması və çıxarılması |
| POST | `/tenants/{id}/owner-invitations` | OWNER dəvəti |
| POST | `/tenants/{id}/owners/{staffUserId}/mfa-reset` | OWNER-in 2FA-sının sıfırlanması (step-up) |
| GET | `/audit-logs` | Platforma audit jurnalı |

### 10.10 Dev, daxili və sistem

| Metod | Yol | Təsvir |
|---|---|---|
| GET | `/api/v1/dev/simulator/tags` | Simulyasiya üçün çiplər (yalnız `dev`) |
| POST | `/api/v1/dev/simulator/taps` | İmzalı toxunuş URL-i yaradır (yalnız `dev`) |
| GET | `/api/v1/dev/outbox?channel=SMS` (və ya `EMAIL`) | Son SMS və emaillər (yalnız `dev`; Playwright testləri də istifadə edir) |
| GET | `/internal/tls/domains` | Sertifikat alınmalı domenlərin siyahısı; yalnız daxili şəbəkədən (nginx xaricə açmır) |
| GET | `/actuator/health/liveness`, `/actuator/health/readiness` | Sağlamlıq yoxlaması (açıq, detalsız) |
| GET | `/api/v1/docs`, `/api/v1/openapi.json` | Swagger UI və OpenAPI (yalnız dev; prod-da bağlıdır) |

### 10.11 Xəta formatı və kodlar

```json
{
  "type": "https://docs.tapstamp.example/problems/stamp-cooldown-active",
  "title": "Bir az gözləyin",
  "status": 409,
  "detail": "Növbəti möhürü 14 dəqiqədən sonra ala bilərsiniz.",
  "instance": "/api/v1/taps/…/claim",
  "code": "STAMP_COOLDOWN_ACTIVE",
  "retryAfterSeconds": 840,
  "traceId": "4f1c2a…"
}
```

Kodların ilkin kataloqu (tam siyahı `docs/error-codes.md` faylında olacaq): `VALIDATION_FAILED`, `RATE_LIMITED`, `UNAUTHENTICATED`, `SESSION_EXPIRED`, `FORBIDDEN`, `NOT_FOUND`, `CSRF_INVALID`, `TENANT_UNAVAILABLE`, `PHONE_INVALID`, `PHONE_REGION_NOT_ALLOWED`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `CONSENT_REQUIRED`, `REFRESH_RACE`, `TAP_MALFORMED`, `TAG_UNKNOWN`, `TAG_DISABLED`, `TAP_SIGNATURE_INVALID`, `TAP_REPLAYED`, `TAP_TOKEN_EXPIRED`, `TAP_TOKEN_USED`, `TAP_BINDING_MISMATCH`, `STAMP_COOLDOWN_ACTIVE`, `STAMP_DAILY_LIMIT_REACHED`, `OUTSIDE_BUSINESS_HOURS`, `REWARD_NOT_AVAILABLE`, `REDEMPTION_CODE_INVALID`, `REDEMPTION_EXPIRED`, `REDEMPTION_ALREADY_COMPLETED`, `LOGIN_FAILED`, `LOGIN_TEMPORARILY_BLOCKED`, `MFA_REQUIRED`, `MFA_INVALID`, `STEP_UP_REQUIRED`, `PASSWORD_POLICY_VIOLATION`, `IDEMPOTENCY_KEY_REUSED`, `INTERNAL_ERROR`.

Validasiya xətalarında əlavə olaraq `errors: [{field, code, message}]` massivi qaytarılır.

---

## 11. Çoxdillilik (az / en / ru)

### 11.1 Frontend

- **react-i18next**. Tərcümələr `src/locales/{az,en,ru}/{common,auth,profile,admin,errors}.json` fayllarındadır. Lazım olarsa yeni namespace-lər (məs. `platform`) əlavə oluna bilər.
- **Tip təhlükəsizliyi:** `i18next.d.ts` faylında `CustomTypeOptions.resources` `az` fayllarından götürülür. `t('profile:card.titlee')` kimi mövcud olmayan açar TypeScript xətası verir və `npm run build` (`tsc -b && vite build`) dayanır.
- **Hardcoded mətn qadağandır:** ESLint `i18next/no-literal-string` qaydası JSX-də tərcüməsiz mətni xəta sayır.
- **Cəm formaları:** i18next `Intl.PluralRules` əsasında işləyir. az və en dillərində `_one` və `_other`, ru dilində `_one`, `_few`, `_many`, `_other` formaları olur (1 кофе / 2 кофе / 5 кофе; 1 штамп / 2 штампа / 5 штампов).
- **Açar paritetı testi** (Vitest): 3 dildə açarların eyni olduğunu yoxlayır. Cəm suffikslərini hər dilin CLDR kateqoriyalarına görə normallaşdırır, ona görə rus dilinin əlavə formaları səhv sayılmır. Çatışmayan açar olduqda test uğursuz olur.

### 11.2 Dilin müəyyənləşdirilməsi və saxlanması

Prioritet sırası (həm frontend, həm backend üçün eynidir):

1. daxil olmuş istifadəçinin profilindəki dil (`customers.preferred_language`, `staff_users.preferred_language`, `platform_admins.preferred_language`);
2. `lang` cookie-si (daxil olmamış istifadəçi üçün);
3. brauzerin `Accept-Language` başlığı (`navigator.languages`);
4. şirkətin standart dili, o da yoxdursa `az`.

Dil dəyişəndə nə baş verir:

- `i18n.changeLanguage()` dili dərhal dəyişir. Səhifə yenilənmir, komponentlər yenidən qurulmur (`key={lang}` istifadə olunmur), ona görə yarımçıq formalar (məs. OTP) itmir.
- `languageChanged` hadisəsində `document.documentElement.lang` yenilənir və `lang` cookie-si yazılır.
- İstifadəçi daxil olubsa, dərhal `PATCH /me {preferredLanguage}` göndərilir (optimistik). Digər cihazlar növbəti `/me` sorğusunda yeni dili götürür (pəncərəyə fokus qayıdanda avtomatik yenilənir).
- Daxil olmamış istifadəçinin seçdiyi dil qeydiyyat sorğusunda göndərilir və profilə yazılır.
- Dil seçicisi hər yerdə işləyir: müştəri header-i (AZ / EN / RU), profil parametrləri, qeydiyyat və OTP ekranları, admin yuxarı paneli və istifadəçi menyusu.

### 11.3 Backend

- `MessageSource` + `messages_az.properties`, `messages_en.properties`, `messages_ru.properties` (UTF-8), `fallbackToSystemLocale=false`, standart dil `az`.
- Prioritet sırasını tətbiq edən xüsusi `LocaleResolver`.
- Problem Details-in `title` və `detail` sahələri, Bean Validation mesajları, SMS və emaillər istifadəçinin dilindədir. `code` sahəsi isə dildən asılı deyil.
- Rus dilində cəm formaları tələb edən backend mesajları (məs. "через 14 минут") ICU4J `MessageFormat` ilə formatlanır.
- CSV başlıqları adminin dilindədir.
- **Açar paritetı testi** (JUnit): 3 `.properties` faylında açarların eyni olduğunu yoxlayır.

### 11.4 Lokallaşdırılmış formatlar

Mərkəzi `format.ts` modulu: `Intl.DateTimeFormat(lng, { timeZone })` (şirkətin saat qurşağı, standart `Asia/Baku`), nisbi vaxt üçün `Intl.RelativeTimeFormat` ("3 gün əvvəl" / "3 days ago" / "3 дня назад"), rəqəmlər üçün `Intl.NumberFormat`, valyuta üçün `Intl.NumberFormat(lng, { style: 'currency', currency: 'AZN' })`.

### 11.5 Admin tərəfindən daxil edilən çoxdilli məzmun

- API JSONB sahələri 3 dildə birlikdə qaytarır, ona görə dil dəyişəndə yenidən sorğu göndərmək lazım olmur. Tərcümə yoxdursa, `az` versiyası göstərilir.
- Admin redaktorunda `LocalizedTextField` komponenti 3 tab-dan ibarətdir (AZ / EN / RU). Tərcümə çatışmayan tab xüsusi işarə ilə göstərilir. `az` məcburidir.

### 11.6 Azərbaycan dilinə xas texniki tələlər

- **`i` / `İ` problemi:** `az` (və `tr`) lokalında `"i".toUpperCase()` nəticəsi `"İ"`-dir. Lokal standart kimi `az` olarsa, Java-da enum adları və texniki identifikatorlar pozula bilər. Qayda: texniki sətirlərdə həmişə `Locale.ROOT` istifadə olunur. Bunu ArchUnit yoxlayır. UI-da böyük hərfə çevirmə üçün `lang="az"` atributu ilə CSS `text-transform` istifadə olunur, brauzer bunu düzgün edir.
- **Şriftlər:** öz serverimizdə saxlanılır, Google CDN istifadə olunmur (CSP və məxfilik səbəbindən). Əsas mətn üçün **Inter** (Azərbaycan hərflərini və Kiril əlifbasını tam dəstəkləyir). Başlıqlar üçün Kiril dəstəkli serif şrift (namizədlər: **Lora** və ya **Playfair Display**). Avtomatik skript şrift fayllarında ə Ə ğ Ğ ı İ ö Ö ü Ü ş Ş ç Ç və bütün Kiril hərflərinin olduğunu yoxlayır; biri çatışmırsa test uğursuz olur.

### 11.7 Uzun mətnlər və dizayn

- Sabit en istifadə olunmur. Düymələrdə `min-width` var və mətn sətrə keçə bilir. Başlıqlarda `text-balance`, cədvəl başlıqlarında `truncate` + tooltip istifadə olunur.
- Playwright əsas ekranların 3 dil × mobil/desktop ekran görüntülərini çəkir. Avtomatik **daşma testi** element `scrollWidth > clientWidth` olduqda uğursuz olur.

### 11.8 Tərcümə keyfiyyəti və terminologiya

Mətnlər qısa, isti və kafe kontekstinə uyğun olacaq, maşın tərcüməsi kimi səslənməyəcək. Terminologiya lüğəti `docs/i18n-guide.md` faylında saxlanılacaq:

| az | en | ru |
|---|---|---|
| möhür | stamp | штамп |
| mükafat / hədiyyə qəhvə | reward / free coffee | награда / подарочный кофе |
| kart | card | карта |
| filial | branch | филиал |
| Qəhvən qeydə alındı ☕ | Your coffee is stamped ☕ | Ваш кофе засчитан ☕ |
| Növbəti möhür 14 dəqiqədən sonra | Next stamp in 14 minutes | Следующий штамп через 14 минут |
| Hədiyyə qəhvəni al | Claim your free coffee | Получить подарочный кофе |

---

## 12. Təhlükəsizlik dizaynı və OWASP Top 10

### 12.1 OWASP Top 10 (2025 buraxılışı)

| Kateqoriya | Necə həll olunur |
|---|---|
| **A01 — Broken Access Control** (giriş nəzarətinin pozulması) | Hər şirkətin ayrıca bazası və DB rolu; tenant yalnız domendən müəyyən olunur; Host və SNI uyğunluğu; hər şey standart olaraq qadağandır (`denyAll`); `@PreAuthorize` + `AccessPolicy` (filial əhatəsi); xaricə yalnız UUID; başqa resursa 404; tenant-lar arası testlər, avtomatik IDOR inventar testi və rol matrisi testi; CORS yoxdur (hər şey eyni origin-dədir). SSRF riski yoxdur: istifadəçinin verdiyi URL-ə server sorğu göndərmir, xaricə yalnız konfiqurasiya olunmuş SMS və SMTP ünvanlarına müraciət olunur. |
| **A02 — Security Misconfiguration** (səhv konfiqurasiya) | `ProdSafetyGuard` (səhv konfiqurasiyada tətbiq başlamır); actuator-da yalnız health; Swagger prod-da bağlıdır; təhlükəsizlik başlıqları (§12.2); stack trace göstərilmir (`include-stacktrace=never`); konteynerlər root olmayan istifadəçi ilə, yalnız oxunan fayl sistemi ilə işləyir; nginx-də TLS 1.2+ və güclü şifrələr; tanınmayan domenlər rədd edilir; prod-da standart giriş məlumatları yoxdur. |
| **A03 — Software Supply Chain Failures** (proqram təchizatı zəncirinin zəifliyi) | OWASP dependency-check (CVSS ≥ 9 olarsa build dayanır), `npm audit --audit-level=critical`, Trivy ilə image skanı; lockfile + `npm ci`; Maven Wrapper checksum yoxlaması; base image-lər digest ilə sabitlənir; CycloneDX SBOM; Renovate və ya Dependabot. |
| **A04 — Cryptographic Failures** (kriptoqrafiya xətaları) | Hər yerdə TLS (hər domen üçün avtomatik sertifikat); AES-256-GCM sütun şifrələməsi + envelope encryption; AAD ilə tenant-a və sətrə bağlama; telefon axtarışı və OTP üçün HMAC-SHA256 + pepper (tenant üzrə törədilmiş açar); Argon2id; yalnız `SecureRandom`; yalnız standart alqoritmlər (NTAG üçün NXP spesifikasiyası); açar identifikatorları ilə rotasiya. |
| **A05 — Injection** (inyeksiya) | JPA parametrli sorğuları; native SQL-də sətir birləşdirmə yoxdur; sıralama sahələri ağ siyahı ilə; provisioning-də DB və rol adları ciddi regex ilə yoxlanılır və identifikator kimi sitatlanır; Bean Validation; React avtomatik escape edir, `dangerouslySetInnerHTML` qadağandır; hüquqi Markdown mətnləri xam HTML-siz render olunur; CSV injection qorunması (`=`, `+`, `-`, `@` ilə başlayan xanalar neytrallaşdırılır); JSON loglar sətir sonu inyeksiyasının qarşısını alır. |
| **A06 — Insecure Design** (etibarsız dizayn) | Toxunuş axını üçün STRIDE təhdid modeli (§12.8); sui-istifadə qaydaları; tap token-in brauzerə bağlanması; işçi təsdiqi rejimi; biznes invariantları DB məhdudiyyətləri ilə (unique, check); idempotentlik; şirkətlər arasında fiziki ayrılıq. |
| **A07 — Authentication Failures** (autentifikasiya xətaları) | OTP qaydaları (hash, 5 dəq, 5 cəhd, atomik sayğac); Argon2id; OWNER və SUPER_ADMIN üçün məcburi TOTP; artan gözləmə ilə bloklanma; refresh rotasiyası və təkrar istifadənin aşkarlanması; idle və mütləq timeout; session fixation qorunması; ümumi cavablar (enumeration yoxdur); NIST şifrə siyasəti; step-up. |
| **A08 — Software or Data Integrity Failures** (proqram və ya məlumat bütövlüyünün pozulması) | NFC məlumatı CMAC ilə yoxlanılır; klientdən gələn heç bir "imzasız" vəziyyətə etibar edilmir; Java deserializasiyası yoxdur; Jackson-da polimorf default typing söndürülüb; naməlum JSON sahələri rədd edilir (mass assignment qorunması); Flyway checksum-ları; audit cədvəli DB hüquqları ilə dəyişilməzdir; CI pipeline-ı qorunur. |
| **A09 — Security Logging and Alerting Failures** (loglama və xəbərdarlıq çatışmazlığı) | `audit_logs`, `platform_audit_logs`, `tap_events`, `fraud_flags`; təhlükəsizlik hadisələri (uğursuz girişlər, bloklanma, MFA xətaları, refresh təkrarı, replay, rate limit, telefonun göstərilməsi, ixrac) strukturlaşdırılmış JSON loglara `traceId` və tenant ilə yazılır; Micrometer sayğacları və xəbərdarlıq qaydası nümunələri; PII maskalanır. |
| **A10 — Mishandling of Exceptional Conditions** (gözlənilməz halların səhv idarəsi) | Qlobal exception handler → Problem Details; **fail closed** (xəta olduqda rədd etmək, keçirmək yox): tenant tapılmırsa, miqrasiya uğursuzdursa, kontekst yoxdursa sorğu rədd olunur; tranzaksiyalar xətada tam geri qaytarılır; rədd hadisələri `REQUIRES_NEW` ilə qeyd olunur; SMS və SMTP üçün timeout-lar; konfiqurasiya xətasında tətbiq başlamır; xəta yolları üçün testlər. |

### 12.2 HTTP təhlükəsizlik başlıqları

```
Strict-Transport-Security: max-age=63072000; includeSubDomains     (yalnız prod-da, aşağıdakı qeydə bax)
Content-Security-Policy:   default-src 'self'; script-src 'self'; style-src 'self' 'nonce-{n}';
                           img-src 'self' data: blob:; font-src 'self'; connect-src 'self';
                           manifest-src 'self'; worker-src 'self'; object-src 'none'; base-uri 'none';
                           form-action 'self'; frame-ancestors 'none'; upgrade-insecure-requests
X-Content-Type-Options:    nosniff
Referrer-Policy:           strict-origin-when-cross-origin   (/t/ və /tap/ üçün: no-referrer)
X-Frame-Options:           DENY                              (köhnə brauzerlər üçün)
Permissions-Policy:        camera=() — müştəri; camera=(self) — /admin (QR skaneri); microphone=(), geolocation=(), payment=()
Cross-Origin-Opener-Policy:   same-origin
Cross-Origin-Resource-Policy: same-origin
Cache-Control:             no-store                          (bütün /api cavabları)
```

- **CSP** (Content Security Policy: brauzerə hansı mənbələrdən skript və resurs yükləməyə icazə verildiyini bildirən başlıq). `script-src 'self'` sərtdir: `unsafe-inline` və `unsafe-eval` yoxdur. Radix/shadcn bəzi `<style>` elementlərini runtime-da yaradır, ona görə nginx hər sorğu üçün nonce yaradıb `index.html`-ə və başlığa yerləşdirir, kitabxanalar onu `get-nonce` ilə alır. Bu uyğun gəlməzsə, ehtiyat variant yalnız `style-src` üçün `'unsafe-inline'`-dır; skriptlər sərt qalır. Bu, risk kimi sənədləşdiriləcək.
- **HSTS** (brauzerə saytı yalnız HTTPS ilə açmağı əmr edən başlıq) dev-də qəsdən söndürülüb, çünki brauzer onu bütün localhost ünvanlarına tətbiq edir və başqa layihələri pozur. `includeSubDomains` şirkətin domen strukturunu təsir edə biləcəyi üçün tenant üzrə konfiqurasiya olunur.
- Başlıqlar həm nginx-də, həm Spring Security-də qoyulur. Biri səhv konfiqurasiya olunsa, digəri qoruyur.

### 12.3 CORS

Hər şirkətin frontend-i və API-si eyni domendədir, ona görə CORS-a ehtiyac yoxdur və **bütün cross-origin sorğular rədd edilir**. Prod qoruyucusu CORS-un açılmasına icazə vermir.

### 12.4 Giriş məlumatlarının yoxlanması və xətalar

- Hər DTO-da `@Valid`, ölçü limitləri və format nümunələri.
- Body ölçüsü limitlənir (nginx `client_max_body_size 64k` və Spring).
- Naməlum JSON sahələri rədd edilir.
- Problem Details; `server.error.include-stacktrace=never`, `include-message=never`. Gözlənilməz xətada yalnız `INTERNAL_ERROR` və `traceId` qaytarılır.

### 12.5 Şəxsi məlumatlar

- Telefon AES-GCM ilə şifrələnir, axtarış üçün tenant üzrə HMAC istifadə olunur (§5.3). Siyahılarda deşifrə edilmədən `phone_masked` göstərilir.
- **Loglarda maskalama:** `PhoneMasker`; DTO-ların `toString` metodu sirləri çıxarır; son qat kimi Logback maskalama konvertoru.
- **Prod-da OTP və tokenlər heç vaxt loga yazılmır.** Bunu inteqrasiya testi yoxlayır: prod-a bənzər profildə OTP axını zamanı loglar tutulur və kodun, tokenlərin orada olmadığı təsdiqlənir.
- **Saxlama müddətləri** (konfiqurasiya olunur): OTP sorğuları 24 saat, bitmiş sessiyalar 30 gün, `tap_events`-dəki IP-lər 90 gün.
- Platforma adminləri şirkətlərin fərdi məlumatlarına çata bilmir.
- Azərbaycanın "Fərdi məlumatlar haqqında" Qanununa uyğunluq (o cümlədən məlumatların lokalizasiyası tələbləri) production-dan əvvəl hüquqşünasla yoxlanılmalıdır.

### 12.6 Actuator və vaxt

- `management.endpoints.web.exposure.include=health`, `show-details=never`. nginx yalnız `/actuator/health/**` yolunu xaricə açır. Metrikalar daxili şəbəkədə ayrıca portda yerləşir və host-a açılmır.
- Bazada UTC, ekranda şirkətin saat qurşağı. Kodda `Clock` bean-i inject olunur, ona görə testlərdə vaxtı idarə etmək mümkündür.

### 12.7 Konteynerlər

Root olmayan istifadəçi, yalnız oxunan fayl sistemi, `no-new-privileges`, Linux capability-ləri söndürülür, healthcheck-lər var. Prod compose-da PostgreSQL portu host-a açılmır.

### 12.8 STRIDE təhdid modeli (toxunuş axını)

| Təhdid | Nümunə | Qorunma |
|---|---|---|
| Spoofing (saxta kimlik) | Saxta URL yaratmaq; başqa şirkətin domeni adından sorğu | CMAC, hər çipə unikal açarlar; Host və SNI uyğunluğu |
| Tampering (dəyişdirmə) | `picc` və ya `cmac` dəyərini dəyişmək; çipin URL-ini yenidən yazmaq | CMAC yoxlaması; NDEF-ə yazmaq yalnız K0 ilə |
| Repudiation (inkar) | "Mən möhür verməmişəm" | `audit_logs`, `tap_events`, işçinin ID-si qeyd olunur |
| Information disclosure (məlumat sızması) | URL-in tarixçədə və Referer-də qalması; xəta səbəbinin oracle kimi istifadəsi | 303 yönləndirmə, `no-referrer`, ümumi `TAP_SIGNATURE_INVALID` kodu |
| Denial of service (xidmətin dayandırılması) | Sayğacı irəli itələmək; endpoint-i sorğularla doldurmaq | Sayğac yalnız CMAC-dan sonra yenilənir; rate limit (IP, çip, tenant, qlobal); nginx `limit_req` |
| Elevation of privilege (səlahiyyətin artırılması) | Simulyatoru prod-da işə salmaq; başqa şirkətin bazasına çatmaq | `ProdSafetyGuard`, `@Profile("dev")`, ArchUnit; ayrıca baza və DB rolu |

---

## 13. Frontend dizaynı

### 13.1 Texnologiyalar

React 18 + TypeScript (strict), Vite, Tailwind CSS 4, shadcn/ui (Radix əsaslı), TanStack Query, React Router, react-i18next, Framer Motion, Recharts (yalnız admin), react-hook-form + zod (formalar), `vite-plugin-pwa` (Workbox), `openapi-typescript` + `openapi-fetch` (tipli API klienti), `qrcode` (QR yaratmaq), `qr-scanner` (işçi planşetində QR skanı), `canvas-confetti`. Dəqiq versiyalar Mərhələ 1-də ən son stabil buraxılışlara görə sabitlənəcək.

Eyni frontend build-i bütün şirkətlərin domenlərində işləyir. Şirkətin adı, təsviri və loqosu API-dən (`/api/v1/public/company`) gəlir.

### 13.2 Müştəri marşrutları (mobile-first PWA, şirkətin domenində)

| Marşrut | Ekran |
|---|---|
| `/` | Profil: salamlama, möhür kartı, irəliləyiş zolağı, mükafat düyməsi, son ziyarətlər. Daxil olmayıbsa — kafe haqqında qısa məlumat və giriş düyməsi. |
| `/login` | Telefon → OTP → (yenidirsə) qeydiyyatın tamamlanması |
| `/tap/:token` | Toxunuşun emalı: "Möhür qeydə alınır…" |
| `/tap/error` | Etibarsız, vaxtı keçmiş və ya təkrar istifadə olunmuş toxunuş |
| `/history` | Ziyarətlər tarixçəsi |
| `/rewards/:rewardId` | Kod + QR + 2 dəqiqəlik geri sayım |
| `/about` | Kafe və filiallar haqqında |
| `/settings` | Dil, aktiv cihazlar, hesabın silinməsi, hüquqi sənədlər |
| `/legal/:type` | Məxfilik siyasəti və ya şərtlər |

### 13.3 Admin marşrutları (desktop-first; işçi konsolu planşetə uyğun)

Şirkətin domenində (`/admin`):

| Marşrut | Ekran |
|---|---|
| `/admin/login`, `/admin/login/mfa`, `/admin/mfa/setup` | Giriş və 2FA |
| `/admin/forgot-password`, `/admin/reset-password`, `/admin/invitation` | Şifrə bərpası və dəvət |
| `/admin` | İdarəetmə paneli: KPI kartları, xətti qrafik, istilik xəritəsi, geri qayıtma, dil bölgüsü, filial və tarix filtrləri |
| `/admin/customers`, `/admin/customers/:id` | Müştərilər cədvəli; zaman xətti, fırıldaq işarələri, əl ilə düzəliş |
| `/admin/staff-console` | İşçi konsolu: gözləyən möhürlər, mükafatın təsdiqi (kod daxil etmək və ya QR skan etmək) |
| `/admin/settings/*` | Mükafat qaydası, limitlər, NFC çipləri, komanda, filiallar, kafe məzmunu (3 dil tab-ı), hüquqi sənədlər |
| `/admin/audit` | Audit jurnalı və toxunuş jurnalı |
| `/admin/account` | Profil, dil, 2FA, sessiyalar |
| `/admin/dev/simulator`, `/admin/dev/outbox` | Yalnız dev |

Platforma domenində eyni admin build-i platforma rejimində açılır (`/admin/tenants`, `/admin/tenants/:id`, `/admin/audit`, `/admin/account`). Rejim backend-in `GET /api/v1/context` cavabına görə seçilir.

### 13.4 Vizual dil

- **Palitra** (açıq rejim): krem fon `#F6EFE6`, latte kart `#EADCC8`, espresso mətn və əsas düymə `#3B2418`, mocha `#6F4E37`, karamel vurğu `#C8763A`, adaçayı (uğur) `#7A9A72`. **Qaranlıq rejim:** fon `#1A1310`, kart `#251B16`, mətn `#F3E9DD`, vurğu `#E09A5F`. Rənglər tema tokenləri kimi təyin olunur və WCAG AA kontrastı yoxlanılır. Gələcəkdə şirkət öz brend rəngini təyin edə bilər (tokenlər buna hazırdır).
- 16–24 px yuvarlaq künclər, yumşaq kölgələr, ən az 44×44 px toxunma sahələri.
- `prefers-reduced-motion` ayarı nəzərə alınır: bu ayar aktivdirsə, animasiyalar sadələşir.
- Admin panel eyni tokenlərdən istifadə edir, amma daha neytral və sıx tərtibatdadır (shadcn sidebar layout).

### 13.5 Möhür kartı UX

- Kartda N fincan ikonu (məs. 9 adi + 10-cu "hədiyyə" fincanı) var. Mobildə 5×2 şəbəkədə yerləşir.
- **Yeni möhür:** fincan "dolur" (maye qalxır, buxar çıxır), `navigator.vibrate([30, 50, 30])` (dəstəklənirsə; iOS Safari dəstəkləmir) və toast: "Qəhvən qeydə alındı ☕". Animasiya yalnız toxunuşdan gələndə oynayır (router state), səhifə yeniləndikdə təkrarlanmır.
- **Kart doldu:** konfetti, bayram animasiyası və "Hədiyyə qəhvəni al" düyməsi.
- **Xəta vəziyyətləri:** cooldown üçün canlı geri sayım ("Növbəti möhür 14 dəqiqədən sonra"), gündəlik limit, etibarsız və ya vaxtı keçmiş toxunuş, "barista təsdiqi gözlənilir".
- **Mükafat ekranı:** böyük kod, QR, 2 dəqiqəlik dairəvi geri sayım. Ekran sönməsin deyə Wake Lock istifadə olunur. Status təsdiqlənəndə "Afiyət olsun!" göstərilir.

### 13.6 PWA

- Manifest şirkətə xasdır (`/manifest.webmanifest` backend-dən gəlir: şirkətin adı, rəngləri, ikonları), quraşdırıla bilir.
- **Service worker yalnız statik resursları keşləyir.** `/api/*` sorğuları üçün `NetworkOnly` strategiyası işlədilir, API cavabları keşlənmir.
- **Kritik qayda:** `navigateFallbackDenylist: [/^\/api\//, /^\/t\//, /^\/admin/]`. Əks halda service worker `/t/…` toxunuş linkini tutub `index.html` qaytarar və toxunuş backend-ə çatmaz.
- Çıxışda TanStack Query keşi təmizlənir.
- **iOS məhdudiyyəti:** iOS-da quraşdırılmış PWA-nın cookie-ləri Safari-dən ayrıdır, NFC linki isə həmişə Safari-də açılır. Müştəri Safari-də bir dəfə daxil olmalıdır. Bu, README-də izah olunacaq.

### 13.7 API klienti

`openapi-fetch` middleware-ləri: CSRF başlığı; müştəri tərəfində 401 cavabında single-flight refresh və sorğunun təkrarı; admin tərəfində 401 cavabında giriş səhifəsinə yönləndirmə; Problem Details cavabını tipli `ApiError { code }` obyektinə çevirmə. Mesaj `errors:{code}` tərcümə açarından götürülür.

---

## 14. Test strategiyası

| Səviyyə | Alət | Nə yoxlanılır |
|---|---|---|
| Unit | JUnit 5, AssertJ | Kriptoqrafiya (AN12196 vektorları, AES-GCM/AAD, HMAC), OTP, TOTP (RFC 6238 vektorları), qaydalar, maskalama, SMS seqmentləri |
| İnteqrasiya | Spring Boot Test, MockMvc, **Testcontainers (PostgreSQL 16)** | Endpoint-lər, tranzaksiyalar, paralel ssenarilər, DB məhdudiyyətləri, provisioning, tenant-lar arası izolyasiya |
| Arxitektura | ArchUnit | Modul sərhədləri, `dev` izolyasiyası, platforma və tenant paketlərinin ayrılığı, `Locale.ROOT` |
| Frontend unit | Vitest, React Testing Library | Komponentlər, i18n paritetı, cəm formaları, `format.ts` |
| E2E | Playwright (+ axe-core) | Əsas istifadəçi axınları, dil axını, əlçatanlıq, daşma |

### 14.1 Əsas test ssenariləri

**Multi-tenancy**
- `TenantIsolationIT`: iki tenant provisioning ilə yaradılır; A-nın cookie-si B-nin domenində `401`; A-nın resurs ID-ləri B-nin domenində `404`; tanınmayan domen `404`; Host/SNI uyğunsuzluğu `421` (nginx konfiqurasiya testi); `TenantContext` sorğudan sonra təmizlənir; tenant DB rolu başqa tenant bazasına qoşula bilmir.
- `TenantProvisioningIT`: provisioning idempotentdir; yarımçıq provisioning-in davamı; uğursuz miqrasiyada tenant `MIGRATION_FAILED` olur və sorğular rədd edilir.

**Çipin yoxlanması və replay qorunması**
- `Ntag424SunVerifierTest`: AN12196 vektorları; `picc`, `cmac` və ya UID-də bir bayt dəyişdikdə rədd.
- `TapReplayIT`: eyni URL iki dəfə açılır, ikincisi `COUNTER_REPLAY` alır; **20 paralel thread eyni URL-i göndərir, yalnız biri qəbul olunur**; kiçik sayğac rədd edilir; deaktiv çip rədd edilir; açar rotasiyası PENDING → ACTIVE.
- `ProdSafetyGuardTest`: `prod,dev` profilləri ilə kontekst başlamır.

**Möhürlər**
- `StampClaimIT`: cooldown; gündəlik limit (Bakı vaxtı ilə gecəyarısı sərhədi, idarə olunan `Clock`); işçi təsdiqi; idempotent claim; eyni tokenlə paralel claim; brauzer bağlanması.

**Autentifikasiya**
- `OtpIT`: DB-də açıq kod yoxdur; 5 dəqiqəlik ömür; 5 cəhddən sonra LOCKED; paralel cəhdlərlə limitin keçilməməsi; 15 dəqiqədə 3 və gündə 10 limiti; qeydiyyatlı və qeydiyyatsız nömrəyə eyni status və eyni body.
- `CustomerSessionIT`: cookie atributları; refresh rotasiyası; təkrar istifadədə sessiyanın ləğvi; race pəncərəsi; CSRF olmadıqda 403; cihazın ləğvi; hesabın silinməsi və anonimləşdirmə.
- `StaffAuthIT` və `PlatformAuthIT`: Argon2id; artan bloklanma; TOTP-siz OWNER və SUPER_ADMIN-in qeydiyyata yönləndirilməsi; TOTP kodunun təkrar istifadəsi; şifrə bərpasının birdəfəlik olması; 30 dəqiqəlik fəaliyyətsizlik və 8 saatlıq mütləq limit; platforma sessiyasının tenant domenində işləməməsi.
- `RateLimitIT`: bütün limitlər, `Retry-After`, `X-Forwarded-For` saxtalaşdırmasının təsirsizliyi.

**Filial izolyasiyası**
- Avtomatik endpoint inventarı (IDOR) və rol matrisi.

**Mükafatın istifadəsi**
- `RedemptionIT`: birdəfəlik; 2 dəqiqəlik ömür; paralel təsdiqdə yalnız bir uğur.

**Çoxdillilik**
- Backend və frontend açar paritetı; dilin prioritet sırası; Problem Details 3 dildə; SMS seqment testi; CSV başlıqları; şrift qlif testi.

**Təhlükəsizlik**
- `SecurityHeadersIT`, `ActuatorExposureIT`, `NoSecretsInLogsIT`, CSV injection testi.

**Playwright E2E** (dev domenləri: `mockcoffee.localhost`, `demo-cay.localhost`, `platform.localhost`)
1. Yeni müştəri: simulyator → toxunuş → telefon → OTP (dev outbox-dan oxunur) → qeydiyyat → fincan animasiyası.
2. Qayıdan müştəri: toxunuş → birbaşa profil → cooldown mesajı.
3. Replay: eyni toxunuş URL-i təkrar açılır və xəta səhifəsi görünür.
4. Mükafat: müştəri kodu yaradır → işçi təsdiqləyir → müştəri ekranında uğur.
5. İşçi girişi TOTP ilə (seed-dəki dev sirri ilə kod generasiya olunur) → panel → müştəri detalları → əl ilə düzəliş.
6. Tenant izolyasiyası: `mockcoffee.localhost`-da daxil olmuş müştəri `demo-cay.localhost`-da qonaq kimi görünür.
7. **Dil axını:** dil dəyişdirilir → səhifə yenilənir → dil yadda qalır → çıxış və giriş → profildəki dil tətbiq olunur; OTP formu yarımçıq ikən dil dəyişir və daxil edilmiş məlumat itmir.
8. 3 dil × əsas ekranlar: ekran görüntüləri, daşma testi, axe əlçatanlıq yoxlaması.

**Örtük hədəfi:** JaCoCo ilə təhlükəsizlik baxımından kritik paketlərdə (`tenancy`, `nfc`, `customer.auth`, `staff.auth`, `loyalty`) ən az 85% sətir örtüyü.

**Hər mərhələnin sonunda:** `mvnw verify`, `npm run lint`, `npm run typecheck`, `npm test`. Mərhələ 5-dən başlayaraq `docker compose` üzərində `npx playwright test` də işə salınır.

---

## 15. İnfrastruktur, konfiqurasiya və CI

### 15.1 Docker Compose servisləri

| Servis | Image və rol | Qeyd |
|---|---|---|
| `postgres` | `postgres:16-alpine` | `loyalty_platform` bazası və rollar init skripti ilə; tenant bazaları provisioning ilə yaradılır; port yalnız dev-də açılır |
| `backend` | multi-stage: Maven build → `eclipse-temurin:21-jre` | root olmayan istifadəçi; `postgres` sağlam olana qədər gözləyir |
| `frontend` | multi-stage: Node 24 build → `nginx-unprivileged` | yalnız statik fayllar, daxili 8080 portu |
| `nginx` | `nginx:stable-alpine` | TLS (SNI ilə sertifikat seçimi), marşrutlar, başlıqlar, `limit_req` |
| `certbot` (yalnız prod) | `certbot/certbot` | Reyestrdəki domenlər üçün Let's Encrypt sertifikatları (HTTP-01), avtomatik yeniləmə |

- **Dev domenləri:** `mockcoffee.localhost`, `demo-cay.localhost` (şirkətlər) və `platform.localhost` (platforma paneli). Chrome, Edge və Firefox `*.localhost` ünvanlarını avtomatik `127.0.0.1`-ə yönləndirir, `hosts` faylını dəyişmək lazım deyil. `scripts/gen-dev-certs` bu domenlər üçün sertifikat yaradır (brauzer xəbərdarlığı olmasın deyə mkcert tövsiyə olunur). HTTPS portu `.env`-də dəyişdirilə bilər (standart 443, məşğuldursa 8443).
- **Mock SMS konsolu:** `ConsoleSmsProvider` OTP-ni backend loguna yazır (`docker compose logs -f backend`) və `/admin/dev/outbox` səhifəsində göstərir. Şifrə bərpası emailləri də eyni yerdə görünür.
- **Real telefonla test:** NFC linki telefonda açılmalıdır, `*.localhost` isə yalnız kompüterdə işləyir. Bunun üçün Cloudflare Tunnel və ya test domeni + LAN variantları README-də izah olunacaq.
- **Hot-reload:** Vite dev server + proxy ilə (`npm run dev`).

### 15.2 `.env.example` (dəyişənlərin qrupları)

```
# Ümumi
SPRING_PROFILES_ACTIVE=dev
HTTPS_PORT=443
APP_PLATFORM_DOMAIN=platform.localhost
APP_TRUSTED_PROXIES=

# PostgreSQL
POSTGRES_PASSWORD=
DB_PLATFORM_OWNER_PASSWORD=
DB_PLATFORM_APP_PASSWORD=
DB_PROVISIONER_PASSWORD=

# Kriptoqrafiya (scripts/gen-secrets ilə yaradılır)
APP_CRYPTO_KEKS=                 # "kek-2026-10:<base64 32 bayt>", vergüllə bir neçə ola bilər
APP_CRYPTO_ACTIVE_KEK_ID=
APP_PHONE_HMAC_KEY=
APP_OTP_PEPPER=
APP_REDEMPTION_CODE_KEY=

# SMS və email
APP_SMS_PROVIDER=console         # prod: http
APP_SMS_HTTP_URL=
APP_SMS_HTTP_API_KEY=
APP_MAIL_PROVIDER=console        # prod: smtp
SMTP_HOST=
SMTP_PORT=
SMTP_USERNAME=
SMTP_PASSWORD=

# TLS (prod)
ACME_EMAIL=

# Yalnız dev
APP_DEV_SIMULATOR_KEY=
DEV_SEED_ENABLED=true
DEV_SEED_ADMIN_PASSWORD=          # README-də göstərilən dev şifrəsi

# Rate limit override-ları (istəyə bağlı)
APP_RATELIMIT_OTP_PER_PHONE_15M=3
APP_RATELIMIT_OTP_PER_PHONE_DAY=10

# CI
NVD_API_KEY=                     # OWASP dependency-check üçün
```

Kodda heç bir sirr yoxdur. `.env` faylı `.gitignore`-dadır. `scripts/gen-secrets.ps1` və `.sh` `.env` faylını kriptoqrafik təsadüfi dəyərlərlə doldurur. Tenant DB rollarının şifrələri provisioning zamanı avtomatik yaradılır və platforma bazasında şifrələnmiş saxlanılır.

### 15.3 Dev test məlumatları (seed)

Telefonları şifrələmək üçün tətbiqin açarları lazımdır, ona görə seed SQL deyil, Java-dadır (`DevDataSeeder`, yalnız `dev`). Seed tenant-ları **real provisioning servisi ilə** yaradır, beləliklə provisioning də hər dev başlanğıcında yoxlanılır. Seed idempotentdir (yalnız boş bazada işləyir) və **deterministikdir** (sabit təsadüfi toxum).

- **Platforma:** SUPER_ADMIN (TOTP-si məlum dev sirri ilə), domen `platform.localhost`.
- **Mock Coffee House** (domen `mockcoffee.localhost`), 2 filial: "Mərkəz — Nizami küçəsi" və "Gənclik". Ad, təsvir və ünvanlar 3 dildədir.
- **3 NFC çipi:** Mərkəz — "Piştaxta 1" (`SIMULATED`), "Piştaxta 2" (`NTAG424_SUN`, dev açarları ilə emulyasiya); Gənclik — "Piştaxta" (`SIMULATED`).
- **Mükafat qaydası:** 9 möhür → "Hədiyyə qəhvə" / "Free coffee" / "Подарочный кофе". **Limitlər:** 20 dəqiqə, gündə 3.
- **İşçilər:** OWNER (TOTP-si məlum dev sirri ilə), MANAGER (Gənclik), STAFF (Mərkəz). Giriş məlumatları yalnız README-nin dev bölməsində olacaq.
- **İkinci kiçik şirkət** ("Demo Çay Evi", domen `demo-cay.localhost`): tenant izolyasiyasını nümayiş etdirmək üçün.
- **~150 müştəri (Mock Coffee House), son 90 gün:**
  - dil bölgüsü təxminən 65% az, 20% ru, 15% en;
  - seqmentlər: daimi müştərilər, tez-tez gələnlər, ara-sıra gələnlər, "itirilmiş" müştərilər;
  - pik saatlar (səhər, nahar, axşam), həftəsonu fərqi;
  - tarixçə sui-istifadə qaydalarına tam uyğundur;
  - təxminən 40 qazanılmış, 30 istifadə olunmuş mükafat;
  - bir neçə fırıldaq hadisəsi (panel və detal səhifəsi canlı görünsün deyə).

### 15.4 Prod-da domenlərin və TLS-in idarəsi

1. SUPER_ADMIN platforma panelində şirkətə domen əlavə edir (`tls_status = PENDING`).
2. Şirkət DNS-də domeni serverimizin IP-sinə yönəldir.
3. `certbot` sidecar-ı reyestrdən (`/internal/tls/domains`) yeni domenləri götürür və HTTP-01 ilə sertifikat alır. nginx sertifikatı SNI adına görə avtomatik tapır, konfiqurasiyanı dəyişmək lazım gəlmir.
4. Status `ISSUED` olur. Sertifikatlar avtomatik yenilənir.

Alternativ: kənar proxy kimi Caddy ("on-demand TLS"). Bu, sertifikat idarəsini sadələşdirir, amma tələbdəki nginx-dən imtina deməkdir. Ətraflı təlimat `docs/tenant-onboarding.md` faylında olacaq.

### 15.5 CI (GitHub Actions)

1. **backend:** `mvnw verify` (unit + Testcontainers + ArchUnit + JaCoCo).
2. **frontend:** `npm ci` → lint → typecheck → Vitest → build → OpenAPI tiplərinin aktual olduğunun yoxlanması.
3. **e2e:** `docker compose up` → Playwright → hesabat artefaktı.
4. **security:** OWASP dependency-check (NVD keşi ilə, CVSS ≥ 9 olarsa dayanır), `npm audit --audit-level=critical`, Trivy image skanı, CycloneDX SBOM.

Dependency-check ilk dəfə NVD bazasını yükləyir və yavaş işləyir. Ona görə lokalda ayrıca Maven profili kimi (`-Psecurity-scan`), CI-da isə həmişə işləyir.

---

## 16. Mərhələlərə bölgü

| # | Mərhələ | Əsas nəticələr | Testlər və qəbul meyarı |
|---|---|---|---|
| 0 | **Plan** | Bu sənəd | ✅ təsdiq olunub |
| 1 | **Skelet, infrastruktur, ümumi qatlar** | Git repozitoriyası, Maven backend (Spring Boot 4.1), Vite (2 giriş nöqtəsi), Tailwind + shadcn tema (açıq və qaranlıq), Compose (4 servis + dev domenləri üçün TLS), `.env.example`, `gen-secrets`, Problem Details, i18n infrastrukturu (backend + tipli frontend + dil seçicisi), təhlükəsizlik başlıqları, actuator, `ProdSafetyGuard`, ArchUnit, CI skeleti, README qaralaması, `CLAUDE.md` | Kontekst yüklənir; başlıqlar; actuator-da yalnız health; prod qoruyucusu; i18n açar paritetı (BE + FE); `docker compose up` smoke testi (Docker quraşdırıldıqdan sonra) |
| 2 | **Multi-tenancy, verilənlər modeli, kriptoqrafiya** | Platforma və tenant miqrasiyaları, DB rolları, tenant reyestri və domenlər, Host ilə tenant-ın müəyyənləşdirilməsi, `TenantRoutingDataSource`, tenant üzrə connection pool-lar, provisioning servisi və CLI, envelope encryption, telefonun şifrələnməsi və HMAC, UUIDv7, dev seed (2 tenant) | Tenant-lar arası izolyasiya, provisioning-in idempotentliyi, `TenantContext` sızması, şifrələmə round-trip və AAD, seed-in deterministikliyi |
| 3 | **İşçi və platforma autentifikasiyası, RBAC** | Argon2id, giriş, TOTP (OWNER və SUPER_ADMIN üçün məcburi), bloklanma, şifrə bərpası, dəvətlər, sessiyalar, step-up, `AccessPolicy`; admin giriş UI və layout (3 dil), platforma panelinin əsası (tenant siyahısı, provisioning) | `StaffAuthIT`, `PlatformAuthIT`, rol matrisi, IDOR karkası |
| 4 | **Müştəri autentifikasiyası** | libphonenumber, OTP, `SmsProvider` + konsol, dev outbox, Bucket4j limitləri, AT/RT sessiyaları, rotasiya, CSRF, razılıqlar, cihazlar, hesabın silinməsi; müştərinin giriş, OTP və qeydiyyat UI-ı | `OtpIT`, `CustomerSessionIT`, `RateLimitIT`, SMS seqment testi |
| 5 | **NFC toxunuşu və möhürlər** | `TagVerifier`, `Ntag424SunVerifier`, `DevSimulatorVerifier`, simulyator səhifəsi, tap token axını, claim, sui-istifadə qaydaları, işçi təsdiqi, `tap_events` və fırıldaq işarələri; profil səhifəsi, animasiyalar, xəta vəziyyətləri | AN12196 vektorları, paralel `TapReplayIT`, `StampClaimIT`, ilk Playwright axını |
| 6 | **Mükafatlar və işçi konsolu** | Mükafatlar, redemption kodu və QR, işçi konsolu (planşet), QR skaneri, bayram animasiyası | Paralel `RedemptionIT`, mükafat E2E axını |
| 7 | **Admin panel** | Analitika (KPI, qrafiklər, istilik xəritəsi, geri qayıtma, dil bölgüsü, filtrlər), müştərilər cədvəli, detallar, düzəliş və ixrac, parametrlər (qayda, limitlər, çiplər və açar rotasiyası, komanda, 3 dilli məzmun), audit jurnalı, platforma panelinin tamamlanması (domenlər, statistika) | Analitika sorğularının testləri, CSV (lokallaşdırma, injection), tam endpoint inventarı |
| 8 | **PWA, dizayn cilası, E2E** | Şirkətə xas manifest, ikonlar, service worker, qaranlıq rejimin cilası, əlçatanlıq, 3 dildə vizual yoxlama, tam Playwright dəsti (dil axını və tenant izolyasiyası daxil) | Bütün Playwright axınları, axe, daşma testləri |
| 9 | **Sərtləşdirmə və sənədlər** | dependency-check, npm audit, SBOM, Trivy, CSP-nin sıxlaşdırılması, logların yoxlanması, prod TLS axını (certbot); README (az); `docs/` (arxitektura, NFC proqramlaşdırma, tenant qoşulması, i18n təlimatı, xəta kodları, metrikalar); OpenAPI təsvirlərinin yoxlanması; **yekun təhlükəsizlik icmalı** | Tam test dəsti yaşıldır, məlum kritik CVE yoxdur |

Hər mərhələnin sonunda: testlər işə salınır, nəticələr (çıxışı ilə birlikdə) Azərbaycan dilində hesabatda göstərilir, mərhələ ayrıca git commit-i kimi qeyd olunur.

---

## 17. İlkin risklər

Tam siyahı yekun təhlükəsizlik icmalında veriləcək.

| Risk | Təsiri | Azaldılması |
|---|---|---|
| Əvvəlcədən yığılmış və ya relay olunan toxunuş URL-ləri | Az sayda saxta möhür | Cooldown, gündəlik limit, `counter_gap` siqnalı, işçi təsdiqi rejimi, iş saatları qaydası |
| Çipin üstünə saxta stiker yapışdırılması (fişinq) | Müştərinin saxta sayta yönləndirilməsi | Fiziki montaj, işçilərin yoxlaması, şirkətin tanınan domeni |
| İşçinin sui-istifadəsi (dostlarına möhür, saxta mükafat) | Maddi itki | Audit, işçi üzrə analitika, rol məhdudiyyətləri |
| Bir şəxsin çoxlu SIM kartla çoxlu hesab açması | Möhürlərin "fermaçılığı" | Brauzer identifikatoru və IP siqnalları, çip üzrə limit |
| Tenant sayı artdıqca bağlantıların sayı (N baza × pool) | PostgreSQL bağlantı limiti | Kiçik, tənbəl pool-lar; boş pool-ların bağlanması; PgBouncer; böyük tenant-ların ayrıca serverə köçürülməsi |
| Miqrasiyaların N bazada icrası | Bir tenant-da uğursuz miqrasiya | Tenant `MIGRATION_FAILED` olur və xidmət göstərmir, digərləri işləyir; miqrasiya runbook-u |
| İşləyən tətbiqdə `loyalty_provisioner` hüququ (`CREATEDB`, `CREATEROLE`) | Tətbiq ələ keçirilsə, yeni baza və rol yaradıla bilər | Prod-da provisioning ayrıca birdəfəlik əmr kimi işlədilir və tətbiq bu hüquqsuz qalır |
| Öz domenlər və TLS | DNS səhvləri, sertifikatın alınmaması | Domen statusunun izlənməsi, xəbərdarlıqlar, onboarding təlimatı |
| Şirkətin domeni dəyişsə | Çiplər yenidən proqramlaşdırılmalıdır | Keçid dövründə köhnə domen reyestrdə saxlanılır |
| iOS-da PWA ilə Safari cookie-lərinin ayrı olması | Müştəri Safari-də ayrıca daxil olmalıdır | README-də izah, UX ipucu |
| Real SMS provayderi hələ seçilməyib | Prod-da OTP göndərilməsi | `SmsProvider` interfeysi, provayder seçimi ayrıca addımdır |
| Spring Boot 4.1-in pulsuz dəstəyi 31.07.2027-də bitir | Yeniləmə tələbi | 2027-ci ilin ortasına qədər növbəti xəttə keçid planlaşdırılmalıdır |
| CSP-də `style-src` üçün ehtiyat variant | Stil inyeksiyası riski (skript riskindən xeyli aşağıdır) | Əvvəlcə nonce ilə həll etməyə çalışacağam |
| Müştəri tətbiqi və işçi paneli eyni domendədir | Bir tərəfdəki XSS digərinə təsir edə bilər | Sərt CSP; gələcəkdə panel üçün ayrıca subdomen (`admin.<şirkətin domeni>`) |
| Hüquqi uyğunluq | Məlumatların emalı tələbləri | Production-dan əvvəl hüquqşünasla yoxlama |

---

## 18. Qərarlar jurnalı

| # | Sual | Qərar | Qeyd |
|---|---|---|---|
| 1 | İş mühiti | Docker Desktop quraşdırılacaq; JDK 21 artıq var | Docker gələnə qədər inteqrasiya testləri atlanır və bu, hesabatlarda göstərilir |
| 2 | Müştəri hesabının əhatəsi | **Hər şirkətdə ayrı hesab**; hər şirkətin öz saytı və **öz verilənlər bazası** var | Platforma bazasında yalnız reyestr saxlanılır |
| 3 | PostgreSQL RLS | Sizin cavabınız "bəli" idi. Ayrı bazalara keçid **RLS-dən daha güclü** izolyasiya verir (ayrı baza + ayrı DB rolu + `CONNECT` məhdudiyyəti), ona görə şirkətlər arası RLS artıq lazım deyil | Şirkət daxilində filial izolyasiyası tətbiq səviyyəsində (`AccessPolicy`) və testlərlə təmin olunur |
| 4 | Spring Boot versiyası | **4.1.x** (Spring Security 7, Hibernate 7, Jackson 3) | endoflife.date məlumatına görə 3.5-in pulsuz dəstəyi 30.06.2026-da bitib (ödənişli dəstək 2032-yə qədərdir); start.spring.io artıq 3.x təklif etmir; 4.1 pulsuz olaraq 31.07.2027-yə qədər dəstəklənir. Bu, ilkin tələbdəki "3.x / Security 6"-dan kənarlaşmadır və "məsləhət gördüyün" cavabınıza əsasən qəbul olunub |
| 5 | Git | Repozitoriya yaradılır, hər mərhələ ayrıca commit olunur | |
| 6 | Domenlər | **Yalnız şirkətin öz domeni** | Dev-də `*.localhost`; prod-da Let's Encrypt (certbot) |
| 7 | Tenant bağlantısının seçilməsi (Mərhələ 2) | Hibernate-in öz multi-tenancy mexanizmi əvəzinə Spring səviyyəsində `TenantRoutingDataSource` | Funksional olaraq eynidir, amma daha sadədir: JPA və JDBC eyni marşrutu işlədir, tranzaksiya meneceri platforma bazasının bağlantısını tenant bağlantısı ilə qarışdırmır. Hibernate başlanğıcda bazaya qoşulmadığı üçün sxem uyğunluğu `EntityMappingIT` testi ilə yoxlanılır |
| 8 | Sütun adları (Mərhələ 2) | `reward_rules.version` → `rule_version`, `legal_documents.version` → `doc_version` | Optimistik kilid sütunu (`version`) ilə qarışıqlığın qarşısını almaq üçün |
| 9 | Rate limiter və dev poçt qutusu (Mərhələ 3) | Bucket4j (platforma bazasında) və dev outbox Mərhələ 4-dən əvvəl, Mərhələ 3-də əlavə olundu | Admin girişi, şifrə bərpası və dəvətlər onlarsız tam yoxlanıla bilmirdi. Bucket4j `SELECT … FOR UPDATE` rejimində işləyir (açarlar mətn: `siyasət:əhatə:HMAC`), `advisory lock` rejimi ədədi açar tələb edir. Limitlər hər tenant üçün ayrıdır |
| 10 | Domen ayrılığı (Mərhələ 3) | Platforma API-si (`/api/v1/platform/**`) yalnız platforma domenində, şirkət API-si yalnız şirkət domenində mövcuddur; digər domendə 404 | `TenantResolutionFilter` səviyyəsində, təhlükəsizlik qaydalarından əvvəl. İşçi və platforma sessiyalarının cookie adları da fərqlidir |
| 11 | SPA üçün sessiyanın vəziyyəti (Mərhələ 3) | `GET /api/v1/{admin,platform}/auth/session` → `NONE / PENDING_MFA / MFA_ENROLLMENT / ACTIVE` | Səhifə yenilənəndə frontend hansı addımı (giriş, TOTP, TOTP qeydiyyatı, panel) göstərəcəyini bilir. Tam olmayan sessiya panel API-sinə 403 alır |
| 12 | İkinci faktor uğursuzluqları (Mərhələ 3) | Yanlış TOTP/bərpa kodu həm sessiyanın sayğacını (5-dən sonra sessiya ləğv olunur), həm də hesabın bloklanma sayğacını artırır | Bir neçə sessiya açaraq TOTP-ni sınamaq hesabın bloklanması ilə nəticələnir |
| 13 | OpenAPI tipləri (Mərhələ 3) | Sənəd `OpenApiContractIT` ilə ixrac olunur, `openapi-typescript` ilə tiplər yaradılır; CI hər ikisinin aktual olduğunu yoxlayır | `openapi-typescript` 7.x rəsmi olaraq TypeScript 5 tələb edir — `package.json`-da yalnız bu paket üçün `overrides` ilə TS 6 qəbul edilib. springdoc cavab sahələrini "məcburi" kimi işarələmir, ona görə TS tiplərində sahələr istəyə bağlıdır |
| 14 | Müştəri sessiyasının vəziyyəti (Mərhələ 4) | `GET /api/v1/auth/session` → `ACTIVE / REFRESH_REQUIRED / NONE` | Səhifə açılanda qonaq üçün 401 xətaları yaranmır; access token-in vaxtı keçibsə, frontend əvvəlcə bir dəfə yeniləmə edir. Refresh cookie-si yalnız `/api/v1/auth` yollarına getdiyi üçün bu endpoint onun mövcudluğunu görür |
| 15 | Step-up OTP (Mərhələ 4) | `otp_challenges.purpose` (`LOGIN` / `STEP_UP`) və `customer_id`, `customer_sessions.step_up_at` (V3 miqrasiyası) | Giriş kodu step-up üçün, step-up kodu giriş üçün keçərli deyil; step-up 10 dəqiqə etibarlıdır |
| 16 | Qayıdan müştəri demosu (Mərhələ 4) | Dev seed yeni müştəri yaratmır, Mock Coffee House-un kartında 4–7 möhür olan seed müştərisinə `+994501234567` nömrəsini verir | Seed-in sayları və invariantları (möhür = toxunuş və s.) dəyişmir |
| 17 | SMS mətni (Mərhələ 4) | Şirkətin adı uzundursa qısaldılır, lazım gələrsə adsız qısa şablon işlədilir; Web OTP sətri yalnız yer qalanda əlavə olunur | OTP SMS-i həmişə 1 seqmentdir (az və ru — UCS-2, 70 simvol); `SmsSegmentsTest` uzun adlarla yoxlayır. `sms_sender_name` varsa, ad əvəzinə o işlədilir |
| 18 | Universal məhsul (sifarişçinin qərarı, Mərhələ 5-dən) | Platforma yalnız kafe üçün deyil: avtoyuma, bərbər, restoran və s. UI müasir, açıq və rəngli; möhür hamı üçün eyni neytral ikondur (fincan yox), rəngi şirkətin brend rəngidir; şirkət adı və mükafatlar dinamikdir | Mətnlərdə və seed-də "qəhvə/fincan" ifadələri ümumi terminlərlə ("möhür", "hədiyyə", "ziyarət") əvəz olunur |
| 19 | Hədiyyə kartları (§9.3–9.4 əvəzinə) | Kart dolanda bayram animasiyası və "Hədiyyə kartları" bölməsində daimi unikal kodlu (məs. `HK-7K3M-9QX2`) və QR-lı kart yaranır. İşçi admin ekranında kodu yazır və ya skan edir → həqiqilik və status → "Təhvil ver" (birdəfəlik, idempotent). Müddəti şirkət təyin edir (müddətsiz və ya N gün) | 2 dəqiqəlik redemption kodu ləğv olunur. Kod `SecureRandom`, oxunaqlı əlifba; bazada HMAC-ı saxlanılır, yoxlama limitlidir (dəqiqədə 30) və auditə yazılır |
| 20 | Domenlər | Hər şirkət ortaq kök domenin subdomenidir: `gloriyajeans.mockcoffe.com`, `moyka.mockcoffe.com` (kök domen müvəqqətidir) | Tenant yenə `Host`-dan müəyyən olunur; prod-da wildcard TLS sertifikatı (`*.mockcoffe.com`, DNS-01). Ayrıca öz domeni olan şirkət də dəstəklənir |
| 21 | Toxunuşdan sonra qonağın möhürü (Mərhələ 5) | Möhür OTP təsdiqi/qeydiyyat tranzaksiyasında deyil, girişdən dərhal sonra eyni `POST /api/v1/taps/{token}/claim` ilə alınır. RESERVED token yalnız həmin OTP sorğusu VERIFIED olubsa və nömrə eynidirsə qəbul olunur | Bir kod yolu; müştəri cooldown, gündəlik limit kimi real xətanı görür. Möhürün vaxtı yenə toxunuşun vaxtıdır |
| 22 | Rədd qeydləri (Mərhələ 5) | Rədd halında jurnal (`tap_events`), fırıldaq işarəsi və token statusu əsas tranzaksiyada commit olunur, xəta isə tranzaksiyadan sonra atılır (`REQUIRES_NEW` əvəzinə) | Token sətri kilidli olduğu üçün ayrıca tranzaksiya özünü bloklayardı; nəticə eynidir — qeyd itmir |
| 23 | Çip üzrə toxunuş limiti (Mərhələ 5) | `max_taps_per_tag_per_minute` yalnız imzası doğru toxunuşları sayır; saxta sorğular IP limiti ilə məhdudlaşır | Hücumçu saxta linklərlə həqiqi müştərilərin limitini tükədə bilməz |
| 24 | `RAPID_MULTI_ACCOUNT` (Mərhələ 5) | Bir brauzerdən 24 saatda 3+ fərqli hesab | Bir çipdə çoxlu müştəri normaldır (növbə), ona görə çip əsaslı siqnal işlədilmir |
| 25 | Möhür claim cavabı | `200` (təkrar göndərişdə də eyni nəticə) | İdempotent əməliyyat üçün sadə müqavilə |
| 26 | Dizayn (Mərhələ 5) | Lora şrifti çıxarıldı, başlıqlar da Inter-dir; palitra müasir və rəngli, `--primary` şirkətin `brand_color`-undan gəlir (kontrasta görə mətn rəngi avtomatik) | Universal məhsul (qərar 18) |
| 27 | Hədiyyə kartının təhvili (Mərhələ 6) | İşçi kodu `POST /api/v1/admin/staff-console/gift-cards/check` ilə yoxlayır (yan təsiri yoxdur, status qaytarılır), `/redeem` ilə təhvil verir (sətir kilidi; eyni işçi + filial təkrarı idempotent, fərqli cəhd `GIFT_CARD_ALREADY_REDEEMED`). `redemptions` cədvəli istifadə olunmur | Daimi kod modelində (qərar 19) ayrıca 2 dəqiqəlik kod lazım deyil; müştərinin ekranı siyahını 2,5 saniyədən bir yoxlayır |
| 28 | Tarix formatı (Mərhələ 6) | Azərbaycan dilində tarix əl ilə qurulur ("7 oktyabr 2026") | Chromium/Edge-in ICU məlumatında az formatı natamamdır ("2026 M10 7") |
| 29 | 2FA siyasəti (istifadəçi qərarı) | TOTP hamı üçün **könüllüdür** (OWNER və SUPER_ADMIN daxil). Hesab səhifəsində: müvəqqəti söndürmə (sirr saxlanılır, `totp_disabled_at`; eyni tətbiqin kodu ilə geri qaytarılır) və tam silmə (sirr + bərpa kodları). Hər ikisi 2FA kodu ilə yox, **cari şifrə** ilə təsdiqlənir. Step-up: 2FA aktivdirsə TOTP, deyilsə şifrə | §9-dakı "OWNER/SUPER_ADMIN üçün məcburi TOTP" qaydasını əvəz edir; risk: şifrə sızarsa ikinci faktor yoxdur — UI qurmağı tövsiyə edir |
| 30 | İşçinin silinməsi | Yumşaq silinmə (soft delete): `status = 'DELETED'`, `deleted_at`, `deleted_by`; sessiyalar ləğv, girişdə və siyahıda görünmür. Email unikallığı yalnız silinməmiş hesablar arasındadır (qismən indeks) — eyni email yenidən dəvət oluna bilər. Özünü və sonuncu aktiv sahibi silmək olmaz | Audit və hədiyyə təhvili tarixçəsindəki istinadlar (`redeemed_by` və s.) qorunur |
| 31 | Analitika (Mərhələ 7) | Hesablamalar birbaşa SQL ilə, materiallaşdırılmış görünüş (materialized view) və ya ayrıca cədvəl yoxdur. Dövr ən çox 366 gün. Qrafiklər kitabxanasız SVG ilə çəkilir. Göstəricilərin tərifləri `docs/metrics.md`-də | Tenant bazaları kiçikdir (bir şirkət). Indekslər (`stamps_branch_created_idx` və s.) bəs edir. Qrafik kitabxanası paketin ölçüsünü artırardı |
| 32 | Filial çərçivəsi | `BranchScope`: sahib — bütün filiallar, menecer — öz filialları. Başqa filial istənilsə 404 qaytarılır. Müştəri menecer üçün yalnız onun filialında möhürü varsa "mövcuddur" | Obyekt səviyyəsində giriş nəzarəti (IDOR) analitika, müştərilər və çiplər üçün eyni qaydadır |
| 33 | Əl ilə düzəliş | `Idempotency-Key` məcburidir. Açar müştəri üzrə 24 saat `idempotency_keys`-də saxlanılır: eyni məzmun eyni cavabı alır, fərqli məzmun `IDEMPOTENCY_KEY_REUSED` alır. Silmə kartdakı saydan çoxdursa, istifadə olunmamış son hədiyyə kartı REVOKED olur (kart bərpa edilir). Təhvil verilmiş hədiyyə geri alınmır (`STAMP_ADJUSTMENT_TOO_LARGE`) | Plan §9.5 |
| 34 | CSV ixracı | Axınla (stream) yazılır, UTF-8 BOM ilə. Başlıqlar adminin dilindədir. Formula yeridilməsindən qorunur: `= + - @`, tab və CR ilə başlayan xanaya apostrof əlavə olunur. Tam telefon nömrəsi ixrac olunmur, yalnız maskalanmış | OWASP CSV injection tövsiyəsi. Tam nömrə yalnız bir müştəri üçün, ayrıca baxışla (audit) göstərilir |
| 35 | Çip açarları | Qeydiyyatda açarlar PENDING yaranır. Provisioning paketi yalnız bir dəfə yüklənir (`provisioning_exported_at`), yükləmə step-up tələb edir. Rotasiya ixrac olunmamış PENDING dəsti əvəzləyir. İxrac olunmuş dəst varkən rotasiya bloklanır (`TAG_ROTATION_IN_PROGRESS`) | Çipdəki açarlarla serverdəkilərin uyğunsuzluğunun qarşısı alınır. Proqramlaşdırma təlimatı `docs/nfc-ntag424-programming.md`-də |
| 36 | Domenlər | Domen əlavə etmək step-up tələb etmir. Əsas domeni dəyişmək və domeni silmək step-up tələb edir. Əsas domen silinmir (`DOMAIN_IS_PRIMARY`). Hər dəyişiklikdən sonra host→tenant keşi təmizlənir | Çiplərə yazılmış URL-lər köhnə domenə bağlıdır, ona görə köhnə domen keçid dövründə qalır |
| 37 | PWA (Mərhələ 8) | Manifest və ikonlar backend-də şirkətə görə yaradılır (Java2D: brend rəngində fon, ağ halqa və ulduz; şrift lazım deyil). Service worker əl ilə yazılıb (Workbox yoxdur): yalnız hash-li statik fayllar və oflayn ekran keşlənir; HTML həmişə şəbəkədən gəlir; `/api`, `/t/`, `/admin` heç vaxt keşlənmir | Möhür və hədiyyə kartı oflayn işləmir. Köhnə keşlənmiş məlumat (məs. istifadə olunmuş kod) göstərmək təhlükəli olardı |
| 38 | Brend rəngi və kontrast | Şirkətin rəngi ağ fona qarşı kontrastı 4.5-dən az olarsa, tonu saxlanılaraq tündləşdirilir (`--brand-light`). Rəngli nişanlarda mətn üçün ayrıca `*-ink` tokenləri var | WCAG 2.1 AA. Şirkət istənilən rəngi seçə bilər (məs. açıq sarı), interfeys yenə oxunaqlı qalır |
| 39 | Keyfiyyət testləri | Playwright + axe: əsas səhifələr 2 ölçüdə × 3 dildə yoxlanılır (üfüqi daşma və WCAG 2.1 AA). E2E ardıcıl işləyir (`workers: 1`). Dev-də yalnız IP üzrə limitlər artırılıb | Lokalda bütün trafik bir IP-dən gəlir. Dev hesablarının TOTP-si eyni addımda iki dəfə qəbul olunmur |
| 40 | Qaranlıq rejim (istifadəçi qərarı) | Qaranlıq rejim və onun düyməsi bütün tətbiqlərdən (müştəri, admin, platforma) çıxarıldı. Yalnız açıq (ağ) rejim var; brauzerin sistem ayarı nəzərə alınmır | Vahid, sadə görünüş. §13-dəki qaranlıq rejim palitrası və Mərhələ 1/8-dəki "qaranlıq rejim" bəndləri bu qərarla əvəz olunur |
| 41 | Asılılıq yamaqları (Mərhələ 9) | Trivy (CycloneDX SBOM və image skanı) tapıntılarına görə Tomcat 11.0.26, Jackson 3.1.7 / 2.21.7 `pom.xml`-də Spring Boot BOM-un üzərinə yazılıb. nginx image-i `apk upgrade` ilə qurulur. postgres image-indəki `gosu` (Go stdlib) tapıntıları qəbul edilmiş riskdir | Kritik Tomcat zəiflikləri (autentifikasiyanın keçilməsi). Spring Boot-un növbəti buraxılışında override-lar silinməlidir |
| 42 | Prod TLS | Hər domen üçün Let's Encrypt (HTTP-01, webroot): certbot konteyneri saatda bir dəfə domen siyahısını backend-in daxili endpoint-indən (`/internal/tls/domains`, nginx xaricə açmır, `APP_INTERNAL_TOKEN` tələb edir) alır; nginx 10 dəqiqədən bir yenidən oxuyur. Wildcard sertifikat əvəzinə domen üzrə sertifikat | DNS provayderinin API-si tələb olunmur; şirkətin öz domeni (CNAME) də eyni axınla işləyir |
| 43 | Loglar və prod qoruyucusu | `LogLeakIT` tam axında sirlərin loga düşmədiyini yoxlayır. `ProdSafetyGuard` sorğu limitlərinin standartdan yuxarı qaldırılmasını qadağan edir | OWASP A09; dev-də IP limitlərinin artırılması prod-a keçməsin |
| 44 | NFC çipləri platformada (istifadəçi qərarı) | Çipləri yalnız platforma admini əlavə edir və idarə edir (`/api/v1/platform/tenants/{id}/tags`): qeydiyyat, provisioning paketi, rotasiya, deaktivasiya. Sahib və menecer yalnız baxır. Hər şirkətdə ilk `app.nfc.free-tags-per-tenant` (standart 1) çip pulsuzdur, qalanları pulludur — yalnız say aparılır, ödəniş sistemdən kənardır. `created_by`/`disabled_by` boşdur (platforma admini işçi deyil), aktor auditdədir | Çiplər platformanın gəlir mənbəyidir; çipin quraşdırılması və proqramlaşdırılması platformanın xidmətidir |
| 45 | Hədiyyə həddinin dəyişməsi (istifadəçi qərarı) | Möhür həddi dəyişəndə standart olaraq yarımçıq kartlar köhnə qaydaya bağlanır (`loyalty_accounts.reward_rule_id`) və onunla bitir; hədiyyədən sonra kart aktiv qaydaya keçir. Sahib yadda saxlamazdan əvvəl təsiri görür (`GET /settings/reward-rule/impact`) və istəsə "hamıya dərhal" (`APPLY_NOW`) seçir — onda kartı dolmuşlara hədiyyə kartı verilir | Hədd azaldılanda kütləvi "pulsuz" hədiyyə, artırılanda müştərinin zərər görməsi olmasın |

---

## 19. Terminlər lüğəti

| Termin | Qısa izahı |
|---|---|
| AAD (Additional Authenticated Data) | Şifrələnməyən, amma imzaya daxil olan kontekst; şifrəli mətni başqa yerə köçürməyə imkan vermir |
| AES-GCM | Həm şifrələyən, həm də dəyişikliyi aşkarlayan simmetrik şifrələmə rejimi |
| Argon2id | Yaddaş tələb edən, GPU ilə sındırılması çətin şifrə hash alqoritmi |
| CMAC | AES əsaslı mesaj autentifikasiya kodu; açarı bilməyən onu saxtalaşdıra bilməz |
| Connection pool | Bazaya hazır bağlantılar dəsti; hər sorğuda yeni bağlantı açmamaq üçün |
| Crypto-shredding | Açarı silməklə həmin açarla şifrələnmiş məlumatı oxunmaz etmək |
| CSP | Brauzerə hansı mənbələrdən skript və resurs yükləməyə icazə verildiyini bildirən başlıq |
| CSRF | Başqa saytın istifadəçinin brauzeri vasitəsilə onun adından sorğu göndərməsi |
| Database-per-tenant | Hər şirkət üçün ayrıca verilənlər bazası |
| DEK / KEK | Məlumatı şifrələyən açar / DEK-ləri şifrələyən master açar |
| E.164 | Telefon nömrəsinin beynəlxalq standart formatı (`+994501234567`) |
| Enumeration | Cavablara baxaraq hansı hesabların mövcud olduğunu təxmin etmək |
| Envelope encryption | Zərf şifrələməsi: məlumat DEK ilə, DEK isə KEK ilə şifrələnir |
| Fail closed | Xəta olduqda əməliyyatı keçirmək yox, rədd etmək |
| HMAC | Gizli açarla hesablanan hash; açarı bilmədən eyni dəyəri yaratmaq olmur |
| HSTS | Brauzerə saytı yalnız HTTPS ilə açmağı əmr edən başlıq |
| IDOR | Başqasının obyektinə sadəcə onun ID-sini dəyişərək müraciət etmək |
| Idempotent | Eyni əməliyyatın təkrarı nəticəni dəyişmir |
| Keyset pagination | Ofset əvəzinə son elementin açarı ilə səhifələmə; böyük cədvəllərdə sürətlidir |
| Multi-tenant | Bir sistemdə bir neçə müstəqil şirkətin məlumatlarının izolyasiyalı saxlanması |
| Opaque token | Daxilində məlumat olmayan təsadüfi sətir; mənası yalnız serverdəki qeyddədir |
| OTP | Birdəfəlik təsdiq kodu (SMS ilə) |
| Pepper | Bazadan kənarda (env-də) saxlanılan gizli açar; hash-lərə əlavə olunur |
| PICC | NFC çipinin özü (Proximity Integrated Circuit Card); PICCData — çipin şifrələnmiş UID və sayğac məlumatı |
| Problem Details (RFC 7807) | HTTP xətaları üçün standart JSON formatı |
| Provisioning | Yeni şirkət üçün baza, rol və ilkin məlumatların avtomatik yaradılması |
| PWA | Brauzerdən telefona quraşdırıla bilən veb tətbiq |
| Race condition | Paralel sorğuların ardıcıllığından asılı olaraq səhv nəticənin yaranması |
| Rate limiting | Müəyyən vaxtda icazə verilən sorğu sayının məhdudlaşdırılması |
| Replay attack | Köhnə, etibarlı sorğunun təkrar göndərilməsi hücumu |
| RLS (Row Level Security) | PostgreSQL-in sətir səviyyəsində giriş nəzarəti |
| SameSite | Cookie-nin başqa saytdan gələn sorğularda göndərilib-göndərilməməsini idarə edən atribut |
| SBOM | Proqramın bütün asılılıqlarının siyahısı |
| Single-flight | Eyni anda yalnız bir yeniləmə sorğusunun getməsi, digərlərinin onun nəticəsini gözləməsi |
| SNI | TLS bağlantısında brauzerin hansı domenə qoşulduğunu bildirməsi; server buna görə sertifikat seçir |
| Step-up | Həssas əməliyyatdan əvvəl kimliyin əlavə (təkrar) təsdiqi |
| SUN / SDM | NTAG 424 DNA çipinin hər oxunuşda kriptoqrafik imzalı dinamik URL yaratması |
| TOTP | Vaxta əsaslanan birdəfəlik kod (Google Authenticator və s.) |
| UUIDv7 | Vaxta görə sıralanan UUID; indekslərdə səmərəlidir |
