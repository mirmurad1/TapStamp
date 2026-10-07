# Yekun təhlükəsizlik icmalı (Mərhələ 9)

Tarix: 2026-10-07. Əhatə: backend (Spring Boot), müştəri tətbiqi və admin/platforma panelləri (React), nginx, PostgreSQL, Docker konfiqurasiyası.

Bu sənəddə üç şey var:
- OWASP Top 10 (2021) üzrə tətbiqin vəziyyəti;
- asılılıq skanının nəticələri;
- qəbul edilmiş və açıq qalan risklər.

## 1. Asılılıqlar və image-lər

| Yoxlama | Alət | Nəticə |
|---|---|---|
| Frontend paketləri | `npm audit`, Trivy (SBOM) | **0** zəiflik |
| Backend kitabxanaları | Trivy (CycloneDX SBOM) | Əvvəl: **3 kritik, 10 yüksək** (aşağıdakı cədvəl). Yeniləmədən sonra **0** |
| `cafe-loyalty-backend`, `-frontend` image-ləri | Trivy | **0** yüksək/kritik |
| nginx image | Trivy | Əvvəl 2 yüksək (libexpat, pcre2). `apk upgrade` əlavə olundu (`infra/nginx/Dockerfile`), indi **0** |
| postgres:16-alpine | Trivy | 22 tapıntı, hamısı `gosu` utilitində (Go stdlib). Qəbul edilmiş risk, bax §4 |

Yenilənən kitabxanalar (`backend/pom.xml`):

| Kitabxana | Köhnə → yeni | Zəifliklər |
|---|---|---|
| Tomcat (embedded) | 11.0.24 → 11.0.26 | CVE-2026-65182, CVE-2026-65905, CVE-2026-68525 (kritik: təhlükəsizlik məhdudiyyətinin və autentifikasiyanın keçilməsi) |
| Jackson 3 (`tools.jackson`) | 3.1.5 → 3.1.7 | CVE-2026-89407, -89425, -68497, -91776, -91777 (DoS) |
| Jackson 2 | 2.21.5 → 2.21.7 | eyni CVE-lər |

Spring Boot-un növbəti buraxılışı bu versiyaları özü gətirəndə `pom.xml`-dəki override-lar silinməlidir.

**CI** (`.github/workflows/ci.yml`) hər PR-da və həftəlik bunları işlədir:
- OWASP dependency-check (CVSS ≥ 9 olarsa dayanır, `NVD_API_KEY` sirri lazımdır);
- CycloneDX SBOM;
- `npm audit`;
- öz image-lərimizin Trivy skanı (yüksək/kritik olarsa dayanır).

## 2. OWASP Top 10

| Risk | Tətbiqdə | Yoxlanılır |
|---|---|---|
| **A01 Giriş nəzarətinin pozulması** | Tenant yalnız `Host`-dan, hər şirkətin ayrıca bazası və DB rolu var. Obyekt səviyyəsində yoxlama (`AccessPolicy`, `BranchScope`): menecer başqa filialın obyektini görmür (404). Rollar: `@PreAuthorize`. Dev endpoint-ləri yalnız `dev` profilində | `TenantIsolationIT`, `TenantDatabaseIsolationIT`, `AdminAccessIT` (bütün endpoint-lərin inventarı), `CustomerAdminIT`, `TagAdminIT`, E2E `isolation.spec.ts` |
| **A02 Kriptoqrafiya** | TLS 1.2/1.3, HSTS (prod). AES-256-GCM sütun şifrələməsi və envelope encryption (KEK → DEK); AAD sətrə və tenant-a bağlıdır. Telefon axtarışı HMAC ilə. Şifrələr Argon2id ilə. OTP, sessiya və bərpa kodları yalnız hash kimi saxlanılır. Hədiyyə kodu yalnız HMAC kimi saxlanılır | `EnvelopeCipherTest`, `Ntag424SunTest`, `StaffAuthIT` (şifrə və bloklanma) |
| **A03 İnyeksiya** | Bütün SQL parametrlidir. Sıralama sahələri ağ siyahıdan seçilir. CSV-də formula yeridilməsindən qorunma var. React mətni HTML kimi göstərmir (hüquqi sənədlər də). CSP-də inline skript yoxdur | `CustomerAdminIT` (CSV injection), ArchUnit |
| **A04 Təhlükəsiz olmayan dizayn** | NFC: NTAG 424 SUN, sayğac replay qorunması, 60 saniyəlik brauzerə bağlı token. Möhür limitləri bütün filiallar üzrə hesablanır. Təhvil idempotentdir, sətir kilidi var. Əl ilə düzəliş `Idempotency-Key` və audit tələb edir | `TapReplayIT`, `StampClaimIT`, `RedemptionIT` |
| **A05 Səhv konfiqurasiya** | `ProdSafetyGuard` prod-da tətbiqin başlamasına imkan vermir, əgər: dev profili, konsol SMS/email, nümunə və ya boş sirr, söndürülmüş `Secure` cookie/HSTS var və ya limitlər standartdan yüksəkdir (yeni). Actuator-da yalnız `health` açıqdır. Stack trace istifadəçiyə qaytarılmır. Konteynerlər: `read_only`, `cap_drop: ALL`, `no-new-privileges`. Baza daxili şəbəkədədir | `ProdSafetyGuardTest`, `ProblemDetailsIT`, smoke test |
| **A06 Köhnəlmiş komponentlər** | Bax §1 | CI |
| **A07 Autentifikasiya** | Müştəri: SMS OTP (hash, 5 dəqiqə, 5 cəhd, atomik sayğac). Nömrənin qeydiyyatda olub-olmadığı açıqlanmır. Opaque tokenlər `HttpOnly; Secure; SameSite` cookie-lərdə saxlanılır; refresh rotasiyası və təkrar istifadənin aşkarlanması var. İşçi: Argon2id, artan gözləmə ilə bloklanma, TOTP (könüllü, qərar 29), həssas əməliyyatlarda step-up, 30 dəqiqə fəaliyyətsizlik və 8 saat mütləq limit | `StaffAuthIT`, `CustomerSessionIT`, `RateLimitIT`, `PasswordResetIT` |
| **A08 Məlumatın bütövlüyü** | NFC linkləri CMAC ilə imzalanıb. Çip açarları yalnız bir dəfə göstərilir. Audit jurnalı yalnız əlavə olunur: tətbiq rolunun `UPDATE/DELETE` hüququ yoxdur | `TagAdminIT`, DB hüquqları |
| **A09 Loglama və monitorinq** | Həssas əməliyyatlar auditə yazılır: giriş, 2FA, tam nömrəyə baxış, CSV, düzəliş, çip açarları, domenlər. Loglarda OTP, token, şifrə və tam telefon nömrəsi yoxdur (maskalanır) | **`LogLeakIT`** (yeni): tam axında sirlərin loga düşmədiyini yoxlayır |
| **A10 SSRF** | Tətbiq istifadəçinin verdiyi URL-ə sorğu göndərmir. SMS provayderinin ünvanı yalnız konfiqurasiyadan gəlir | — |

**Əlavə qorunma:**
- **Sorğu limitləri:** nginx `limit_req` və tətbiq səviyyəsində Bucket4j (IP, nömrə, email, şirkət, çip üzrə).
- **CSRF:** cookie və başlıq yoxlanılır.
- **CSP:** `default-src 'self'`; `unsafe-inline` və `eval` yoxdur. 3D möhür (WebGL) əlavə icazə tələb etmir.
- **Brauzer qorunması:** `__Host-` prefiksli cookie-lər; `Permissions-Policy`: kamera yalnız `/admin`-də; `COOP`/`CORP`.
- **Service worker:** API, NFC linkləri və admin paneli keşlənmir.

## 3. Bu mərhələdə edilənlər

- **Asılılıqlar:** Tomcat və Jackson-un zəif versiyaları yeniləndi; nginx image-inə Alpine yamaqları əlavə olundu.
- **`ProdSafetyGuard`:** prod-da sorğu limitlərini standartdan yuxarı qaldırmaq qadağandır. Dev-də yalnız IP limitləri testlər üçün artırılıb.
- **`LogLeakIT`:** OTP kodu, telefon nömrəsi, access/refresh tokenləri, NFC tokeni, işçi sessiyası və şifrə tam axın boyu loglarda görünmür.
- **TLS:** daxili TLS domen siyahısı (`/internal/tls/domains`) iki qatla qorunur: nginx onu xaricə açmır, endpoint özü sirr token tələb edir (sabit vaxtlı müqayisə). Prod-da certbot avtomatik sertifikat alır (`docker-compose.prod.yml`).
- **Ehtiyat nüsxə:** `scripts/backup.sh`. Şifrələmə açarlarının ayrıca saxlanması sənədləşdirildi.
- **CI:** öz image-lərimizin Trivy skanı əlavə olundu.

## 4. Qəbul edilmiş və açıq risklər

| Risk | Təsir | Qərar / tövsiyə |
|---|---|---|
| **2FA könüllüdür** (istifadəçi qərarı 29) | Sahibin şifrəsi sızarsa, ikinci faktor yoxdur | Hesab səhifəsində qurmaq tövsiyə olunur; bloklanma və limitlər var. Sahiblərdən 2FA-nı qurmağı xahiş edin |
| **postgres image-ində `gosu` (Go stdlib CVE-ləri)** | `gosu` yalnız konteyner başlayanda bir dəfə işləyir, şəbəkə ilə işləmir | Qəbul edildi. Rəsmi image yenilənəndə `docker compose pull`. Baza xaricə açıq deyil |
| **Prod TLS axını real domendə sınaqdan keçməyib** | Konfiqurasiya xətası olarsa, domen HTTPS-siz qala bilər (fail closed: HTTP-yə düşmür) | İlk quraşdırmada `docs/deployment.md` §7 ilə yoxlanılmalıdır |
| **SMS adapteri ümumidir** | Konkret provayderin API formatı fərqli ola bilər | Provayder seçiləndə adapter onun sənədinə uyğunlaşdırılmalıdır |
| **Fərdi məlumatların lokallaşdırılması** | Qanunvericilik vətəndaşların məlumatının ölkədə saxlanmasını tələb edə bilər | Hüquqşünasla dəqiqləşdirin; lazım olarsa Azərbaycan lokasiyalı server |
| **Açarların idarəsi** | `.env` itərsə, şifrəli məlumat bərpa oluna bilməz; sızarsa, şifrələmə mənasını itirir | `.env`-in ayrıca, şifrəli nüsxəsi; serverə giriş yalnız SSH açarı ilə. Gələcəkdə KMS/Vault (envelope encryption buna hazırdır) |
| **Hüquqi sənədlərin mətni** | Nümunə mətndir | Hüquqşünas yoxlamalıdır; yeni versiya admin paneldən dərc olunur |
| **NVD açarı olmadan dependency-check** | CI-də OWASP dependency-check çox yavaş işləyir | GitHub-da `NVD_API_KEY` sirrini təyin edin (pulsuz) |

## 5. Nəticə

Məlum kritik və yüksək zəiflik qalmayıb. Postgres image-indəki `gosu` tapıntıları qəbul edilmiş riskdir.

Bütün testlər yaşıldır:
- backend unit və inteqrasiya testləri;
- frontend testləri;
- Playwright E2E;
- axe əlçatanlıq yoxlaması;
- smoke test.

Real serverə çıxmazdan əvvəl `docs/deployment.md`-dəki yoxlama siyahısı keçilməlidir.
