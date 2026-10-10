# Serverin quraşdırılması: domen, VPS, DNS, poçt

Bu təlimat TapStamp-ı sıfırdan real serverə çıxarmaq üçündür. Texniki təfərrüatlar [deployment.md](deployment.md)-dədir; bu sənəd isə ardıcıl addımları və tez-tez verilən sualların cavablarını bir yerə toplayır.

Nümunə dəyərlər (özünüzünkü ilə əvəz edin):

| Nə | Nümunə |
|---|---|
| Domen | `tapstamp.az` |
| Server IP | `49.12.34.56` |
| Platforma paneli | `https://platform.tapstamp.az/admin` |
| Şirkət | `moyka.tapstamp.az` |

---

## 1. Nə almaq lazımdır

### VPS (server)

| Provayder | Paket | Qiymət | Qeyd |
|---|---|---|---|
| **Hetzner** (tövsiyə) | **CX33**: 4 vCPU, 8 GB RAM, 80 GB NVMe | ~€9/ay | Sabit sürət, yaxşı panel, snapshot, firewall. **CAX (ARM) yox, CX (Intel/AMD) seçin** |
| Contabo | Cloud VPS 4: 4 vCPU, 8 GB, 100 GB | ~€5/ay | Ucuz, amma nüvələr daha çox paylaşılır |
| AZEVPS | Standart: 2 vCPU, 4 GB, 40 GB | 40 AZN/ay | **Azərbaycan lokasiyası** — fərdi məlumatın ölkədə saxlanması tələb olunarsa |

- Yük testinə görə 50 şirkətə qədər **4 vCPU, 8 GB RAM** kifayətdir ([load-test.md](load-test.md)).
- Paylaşımlı (cPanel) hosting **uyğun deyil**: Docker lazımdır.
- Əməliyyat sistemi: **Ubuntu 24.04**.
- Virtualizasiya **KVM** olmalıdır (Docker üçün). Root/SSH girişi, statik IPv4, açıq 80/443 portları lazımdır.
- **Backup** seçimini aktivləşdirin (serverin tam surəti).
- Server Avropadadırsa: fərdi məlumatların (telefon nömrələri) ölkədə saxlanması tələbini hüquqşünasla dəqiqləşdirin.

### Domen
- İstənilən qeydiyyatçıdan (məs. azhosting.az). Domen və VPS **ayrı-ayrı yerlərdən** alına bilər — onları DNS yazıları birləşdirir.
- DNS paneli **wildcard (`*`) A yazısını** dəstəkləməlidir. Dəstəkləmirsə — DNS-i pulsuz Cloudflare-ə keçirin (bax §3).
- Domeni əvvəlcədən qəti seçin: NFC çiplərinə yazılan link şirkətin domeninə bağlıdır.

### Poçt (SMTP)
İşçi dəvətləri, şirkət sahibinə dəvət və şifrə bərpası üçün. Variantlar:
- domenin korporativ poçtu (hostinq, Zoho, Google Workspace) — `noreply@tapstamp.az`;
- transaksion servis (Brevo, Mailgun, Amazon SES) — böyüdükcə.

### SMS provayderi
Müştərilərin OTP kodları üçün. API ünvanı, açar və operatorlarda təsdiqlənmiş göndərən adı (alfa-ad) lazımdır. Provayder seçiləndə backend-in SMS adapteri onun API-sinə uyğunlaşdırılır. **Prod rejimi konsol SMS ilə başlamır** — SMS hazır olmadan canlıya çıxmaq olmaz.

---

## 2. Subdomain və wildcard necə işləyir

- Hər şirkət bir **subdomain** alır: `moyka.tapstamp.az`, `gloria.tapstamp.az`. Subdomainlər pulsuzdur və saysızdır.
- DNS-də **bir dəfə** `*` (wildcard) yazısı əlavə olunur: bundan sonra istənilən `xxx.tapstamp.az` sizin serverinizə gəlir — hər şirkət üçün DNS-ə girmək lazım deyil.
- Server şirkəti `Host` başlığından tanıyır: platforma bazasındakı **`tenant_domains`** cədvəlinə baxır. Cədvəldə olmayan subdomain heç bir şirkəti açmır.
- Cədvələ domeni yalnız **platforma admini** (siz) əlavə edir: **Yeni şirkət** və ya şirkətin **Domenlər** bölməsi.

**Wildcard təhlükəlidirmi?** Xeyr:
- DNS yazılarını yalnız domen panelinə girişi olan (siz) dəyişə bilər — başqası sizin subdomaininizi öz serverinə yönəldə bilməz;
- tanınmayan subdomain üçün sertifikat alınmır, nginx TLS bağlantısını qurmur; keçsə belə backend rədd edir;
- sertifikatlar yalnız cədvəldəki domenlər üçün alınır.

Əsl risk — **hesabların** ələ keçirilməsidir. Bunları güclü şifrə və 2FA ilə qoruyun: domen qeydiyyatçısı, Hetzner, Cloudflare (varsa), GitHub, platforma admini.

**Sorğu hansı qatlardan keçir:**
1. firewall (ufw + Hetzner Firewall) — yalnız 22, 80, 443;
2. nginx — sertifikat yoxdursa TLS qurulmur; Host ≠ SNI → 421; IP limitləri;
3. backend — Host `tenant_domains`-də axtarılır; tapılmasa rədd; dayandırılmış şirkət → 503;
4. baza — hər şirkətin ayrıca bazası və DB istifadəçisi.

**Portlar:** kodda görünən `8080` konteynerlərin daxili portudur, xaricə açılmır. Prod-da xaricə yalnız nginx-in 80 və 443 portları açıqdır. PostgreSQL internetə bağlı olmayan daxili şəbəkədədir.

**HTTPS sertifikatları:** serverdəki certbot hər domen üçün Let's Encrypt sertifikatını avtomatik alır (≤ 1 saat) və yeniləyir (90 gündən bir). Wildcard sertifikat istifadə olunmur — DNS provayderinin API-si lazım deyil.

**Şirkətin öz domeni** (`bonus.moyka.az`): şirkət öz DNS-ində `CNAME bonus → moyka.tapstamp.az` yazır, siz isə platformada həmin şirkətin **Domenlər** bölməsinə əlavə edirsiniz.

---

## 3. Addım-addım quraşdırma

### 3.1. SSH açarı (öz kompüterinizdə, bir dəfə)
PowerShell:
```
ssh-keygen -t ed25519 -C "mirmurad@tapstamp"
type $env:USERPROFILE\.ssh\id_ed25519.pub
```
Çıxan `ssh-ed25519 AAAA...` sətrini kopyalayın (ictimai açar — paylaşmaq təhlükəsizdir). Gizli açar (`id_ed25519`) heç kimə verilmir.

### 3.2. Hetzner-də server
Cloud Console → **Add Server**:
- Location: **Falkenstein** və ya **Nürnberg** ("not available" yazılıbsa — başqa lokasiya);
- Image: **Ubuntu 24.04**; Type: **CX33**;
- SSH keys: kopyaladığınız sətir;
- Backups: aktiv; Name: `tapstamp-prod`.

**Firewalls → Create**: daxil olan TCP **22, 80, 443** — serverə tətbiq edin.

### 3.3. DNS (domen qeydiyyatçısının paneli)
**Domenlərim → tapstamp.az → DNS idarəetməsi**. Köhnə A yazılarını silin, bunları əlavə edin:

| Tip | Ad | Dəyər |
|---|---|---|
| A | `@` | `49.12.34.56` |
| A | `*` | `49.12.34.56` |
| A | `platform` | `49.12.34.56` |

Panel `*` qəbul etmirsə: Cloudflare-də domeni əlavə edin, qeydiyyatçıda **NS** ünvanlarını Cloudflare-inkilərlə əvəz edin, yazıları Cloudflare-də **"DNS only" (boz bulud)** rejimində əlavə edin. (Proxied — narıncı bulud — rejimi indiki quruluşda istifadə olunmur: real IP, server sertifikatı və firewall üçün əlavə dəyişiklik tələb edir.)

Yoxlama (10–60 dəqiqə sonra; ikisi də server IP-ni qaytarmalıdır):
```
nslookup platform.tapstamp.az
nslookup test123.tapstamp.az
```

### 3.4. Serverin hazırlanması
```
ssh root@49.12.34.56
```
Serverdə:
```
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw --force enable
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab
sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/' /etc/ssh/sshd_config && systemctl restart ssh
```
Sonuncu sətir SSH-a şifrə ilə girişi bağlayır (yalnız açarla).

### 3.5. Kod (private GitHub repo)
Serverdə **deploy key** yaradın:
```
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" && cat ~/.ssh/github_deploy.pub
```
GitHub → **TapStamp → Settings → Deploy keys → Add deploy key** (yazma icazəsi vermədən). Sonra:
```
printf 'Host github.com\n  IdentityFile ~/.ssh/github_deploy\n' >> ~/.ssh/config
git clone git@github.com:mirmurad1/TapStamp.git /opt/tapstamp && cd /opt/tapstamp
```

### 3.6. `.env`
```
bash scripts/gen-secrets.sh && nano .env
```
Şifrələr və açarlar avtomatik yaranır. Bunları doldurun:
```
APP_PLATFORM_DOMAIN=platform.tapstamp.az
ACME_EMAIL=sizin@email.az
APP_PROVISIONING_ENABLED=true
BACKEND_MEM_LIMIT=2g
APP_SMS_PROVIDER=http
APP_SMS_HTTP_URL=...
APP_SMS_HTTP_API_KEY=...
APP_MAIL_PROVIDER=smtp
APP_MAIL_FROM=noreply@tapstamp.az
SMTP_HOST=...
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
```
- `APP_PROVISIONING_ENABLED=true` — platformada domeni yazmaqla şirkət yaratmaq üçün (baza, miqrasiyalar, domen, HTTPS avtomatik). **Mütləq yazın**: standart dəyər `false`-dur.
- `BACKEND_MEM_LIMIT` — 8 GB server: `2g`; 4 GB server: `1280m`.
- `APP_NFC_FREE_TAGS` (standart 1) — hər şirkətə neçə NFC çipi pulsuzdur.

`.env`-in surətini serverdən kənarda saxlayın (öz kompüterinizdə işlədin):
```
scp root@49.12.34.56:/opt/tapstamp/.env ./tapstamp-env-backup
```
Telefon nömrələri, çip açarları və 2FA sirləri bu açarlarla şifrələnib — `.env` itərsə, baza nüsxəsi də oxunmaz olur.

### 3.7. İlk işə salma
```
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
```
"Started" görünəndə `Ctrl+C`. Konfiqurasiya təhlükəlidirsə (dev imkanları, boş sirr, konsol SMS və s.), backend başlamır və səbəbi loga yazır.

### 3.8. Platforma admini
```
PLATFORM_ADMIN_PASSWORD='Güclü-Şifrə-2026!' docker compose -f docker-compose.yml -f docker-compose.prod.yml run --rm backend --spring.main.web-application-type=none --create-platform-admin=admin@tapstamp.az --display-name="Ad Soyad"
```
10–60 dəqiqə sonra **https://platform.tapstamp.az/admin** açılır. Daxil olun və **Hesabım → İki addımlı yoxlama**-da 2FA qurun (prod-da hesablar 2FA-sız yaranır; 2FA könüllüdür, amma platforma admini üçün mütləq tövsiyə olunur).

### 3.9. Gündəlik backup
```
(crontab -l 2>/dev/null; echo "0 3 * * * cd /opt/tapstamp && ./scripts/backup.sh >> /var/log/tapstamp-backup.log 2>&1") | crontab -
```
Nüsxələr `backups/` qovluğunda 14 gün saxlanılır. Müntəzəm olaraq serverdən kənara köçürün.

### 3.10. Poçtun DNS yazıları (spam-a düşməmək üçün)
Poçt provayderi (məs. Zoho) SPF, DKIM, DMARC yazılarını verir — onları **eyni DNS panelinə** əlavə edin:

| Tip | Ad | Dəyər (provayder verir) |
|---|---|---|
| TXT | `@` | `v=spf1 include:zoho.com ~all` |
| TXT | `zmail._domainkey` | `v=DKIM1; k=rsa; p=...` |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:siz@email.az` |
| MX | `@` | `mx.zoho.com` (poçt qutusu da lazımdırsa) |

SPF yazısı yalnız **bir** dənə olmalıdır (bir neçə provayder varsa, `include:`-lar birləşdirilir).

### 3.11. İlk şirkət
Platforma → **Yeni şirkət** → domen `moyka.tapstamp.az` (DNS-də heç nə etmək lazım deyil) → sahibə dəvət. Sertifikat ≤ 1 saata alınır (Domenlər bölməsində "Sertifikat var"). NFC çiplərini də siz əlavə edirsiniz: şirkətin səhifəsi → **NFC çipləri** ([nfc-ntag424-programming.md](nfc-ntag424-programming.md)).

---

## 4. Gündəlik iş

### Yeniləmə (sıfır fasilə)
```
cd /opt/tapstamp && git pull && ./scripts/deploy.sh --build
```
Backup → yeni versiya köhnənin yanında qalxır → sağlamdırsa nginx keçir → köhnə yumşaq dayanır. Sorğular itmir. Uğursuzdursa, köhnə versiya işləməyə davam edir. (Adi `docker compose up` 20–40 saniyəlik 502 verir — yalnız ilk quraşdırmada.)

### Loglar
Ən asan yol — `scripts/logs.sh` (oxunaqlı format üçün bir dəfə `apt install -y jq`):
```
cd /opt/tapstamp
./scripts/logs.sh                  # backend, canlı (Ctrl+C — çıxış)
./scripts/logs.sh nginx            # başqa servis: frontend, nginx, postgres, certbot
./scripts/logs.sh --errors         # son 24 saatın xəta və xəbərdarlıqları
./scripts/logs.sh --trace <traceId>
./scripts/logs.sh --status         # konteynerlərin vəziyyəti
```
Eyni şey docker compose ilə:
```
alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'
dc logs -f backend                 # canlı
dc logs --since 1h backend         # son 1 saat
dc logs --since 24h backend | grep -i '"log.level":"ERROR"'
dc logs backend | grep <traceId>   # istifadəçinin gördüyü xətanın izi
dc logs certbot                    # sertifikatlar
dc ps                              # konteynerlərin vəziyyəti
```
Loglar JSON formatındadır; OTP, token, şifrə və tam telefon nömrəsi yazılmır. Hər konteynerin logu ən çox 5 × 20 MB saxlanılır.

### Portların yoxlanması
```
ss -tlnp
```
Xarici ünvanda yalnız 22, 80, 443 görünməlidir.

### Monitorinq
Pulsuz **UptimeRobot**: `https://platform.tapstamp.az/actuator/health` — hər 5 dəqiqədən bir; server düşəndə email/Telegram bildirişi.

---

## 5. Yoxlama siyahısı
- [ ] `https://platform.tapstamp.az/admin` 🔒 ilə açılır
- [ ] Platforma admini hesabında 2FA qurulub
- [ ] Yeni şirkətin domeni HTTPS ilə açılır
- [ ] Dəvət emaili gəlir (spam qovluğunu da yoxlayın)
- [ ] Real nömrəyə SMS kodu gəlir
- [ ] `ss -tlnp` — yalnız 22, 80, 443
- [ ] `scripts/backup.sh` əl ilə işlədilib, nüsxə yaranıb
- [ ] `.env` serverdən kənarda saxlanılır
- [ ] Domen qeydiyyatçısı, Hetzner, GitHub hesablarında 2FA
- [ ] UptimeRobot qurulub

## 6. Sonrakı addımlar
- **GitHub Actions auto-deploy:** image-lər ghcr.io-da qurulur, serverdə SSH ilə `deploy.sh` çağırılır (istəyə bağlı təsdiq düyməsi ilə).
- **Cloudflare proxied rejimi** (DDoS qorunması, IP-nin gizlədilməsi) — real IP, origin sertifikatı və firewall dəyişiklikləri ilə.
- **Böyümə:** 20+ aktiv şirkətdə resursları izləyin; lazım olanda paketi böyüdün (Hetzner-də bir kliklə), sonra bazanı ayrıca serverə köçürün. Bağlantı limiti: şirkət sayı × `APP_TENANT_POOL_MAX` + 20 < `POSTGRES_MAX_CONNECTIONS`.
