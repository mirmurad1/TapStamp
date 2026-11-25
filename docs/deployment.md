# Prod-a yerləşdirmə

Bu təlimat tətbiqi real serverə (VPS) qurmaq üçündür. Nəticədə:
- hər şirkət öz alt domenində işləyir (`moyka.sizinbrend.az`);
- HTTPS sertifikatları avtomatik alınır və yenilənir;
- bazanın gündəlik ehtiyat nüsxəsi götürülür.

## 1. Lazım olanlar

| Nə | Tövsiyə |
|---|---|
| **Server (VPS)** | Ubuntu 24.04, ən azı **2 vCPU, 4 GB RAM**, 40 GB disk, sabit IPv4. Paylaşımlı (cPanel) hosting uyğun deyil: Docker, Java və PostgreSQL lazımdır. Fərdi məlumatların ölkədə saxlanması tələb olunursa, Azərbaycan lokasiyalı VPS seçin. |
| **Domen** | Neytral brend adı, məs. `sizinbrend.az`. Domen NFC çiplərinə yazılır, sonradan dəyişmək çətindir. |
| **DNS** | Wildcard (`*`) A yazısını dəstəkləyən panel (domen qeydiyyatçısı və ya Cloudflare). |
| **SMS provayderi** | API ünvanı və açarı; göndərən adı (alfa-ad) operatorlarda təsdiqlənmiş olmalıdır. |
| **SMTP** | İşçi dəvətləri və şifrə bərpası emailləri üçün (məs. korporativ poçt). |

## 2. DNS yazıları

| Tip | Ad | Dəyər |
|---|---|---|
| A | `@` | server IP |
| A | `*` | server IP — bütün şirkətlərin alt domenləri |
| A | `platform` | server IP — platforma paneli |

Cloudflare istifadə olunursa, proxy söndürülməlidir ("DNS only"). Sertifikatları serverdəki certbot alır, HTTP-01 yoxlaması birbaşa serverə gəlməlidir.

Yoxlama: `nslookup test.sizinbrend.az` serverin IP-sini qaytarmalıdır.

## 3. Serverin hazırlanması

```bash
# Docker (rəsmi skript) və firewall
curl -fsSL https://get.docker.com | sh
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw enable

# Kod
git clone <repo> /opt/tapstamp && cd /opt/tapstamp
```

Firewall-da yalnız SSH, 80 və 443 portları açıq olmalıdır. PostgreSQL xaricə açılmır: o, Docker-in daxili şəbəkəsindədir.

## 4. Sirlər (`.env`)

```bash
bash scripts/gen-secrets.sh        # .env yaradılır: bütün açarlar və şifrələr təsadüfi
nano .env
```

`.env`-də prod üçün dəyişdirilməli dəyərlər:

| Dəyişən | Dəyər |
|---|---|
| `APP_PLATFORM_DOMAIN` | `platform.sizinbrend.az` |
| `APP_PROVISIONING_ENABLED` | **`true`** — platforma panelindən yeni şirkət yaratmaq üçün (domeni yazmaq kifayətdir: baza, miqrasiyalar, domen və HTTPS avtomatik). Standart `false`-dur |
| `ACME_EMAIL` | sertifikat bildirişləri üçün email |
| `APP_SMS_PROVIDER`, `APP_SMS_PG_URL`, `APP_SMS_PG_PUBLIC_KEY`, `APP_SMS_PG_PRIVATE_KEY` | `postaguvercini` və Poçt Göyərçininin API ünvanı (yolsuz), PublicKey, PrivateKey. Başlıq (`APP_SMS_SENDER`, standart `TrendStamp`) provayderdə qeydiyyatdan keçmiş olmalıdır. Ümumi JSON adapteri üçün: `http`, `APP_SMS_HTTP_URL`, `APP_SMS_HTTP_API_KEY` |
| `APP_SMS_TENANT_DAILY_NEW`, `APP_SMS_PLATFORM_DAILY`, `APP_SMS_ALERT_EMAIL` | Giriş kodlarının gündəlik SMS büdcəsi: şirkət üzrə **yeni nömrələrə** (standart 100; platforma panelində şirkət üçün dəyişdirilə bilər) və platforma üzrə **bütün kodlar** (standart 1500). 80% və 100%-də `APP_SMS_ALERT_EMAIL`-ə xəbərdarlıq (boşdursa yalnız platforma auditi). Bax: qərar #53 |
| `APP_PUSH_VAPID_PUBLIC_KEY`, `APP_PUSH_VAPID_PRIVATE_KEY`, `APP_PUSH_SUBJECT` | Web push bildirişləri. Açarlar **bir dəfə** yaradılır: `scripts/gen-vapid-keys.sh` (və ya `.ps1`) iki sətri çap edir — `.env`-ə əlavə edin. Dəyişdirilsə, bütün müştərilərin abunələri etibarsız olur. Boşdursa push söndürülür. `APP_PUSH_SUBJECT` — `mailto:` ünvanı (boşdursa `APP_MAIL_FROM`). Bax: qərar #54 |
| `APP_MAIL_PROVIDER`, `APP_MAIL_FROM`, `SMTP_*` | `smtp` və poçt serverinin məlumatları (Google Workspace üçün aşağıdakı bölməyə baxın) |
| `APP_SITE_DOMAIN`, `APP_SITE_INBOX` | Şirkət saytı: `trendsolutions.az` və demo müraciətlərinin gəldiyi poçt qutusu (`info@trendsolutions.az`). Saytın faylları `site/` qovluğundadır, nginx onları birbaşa verir; `www.` əsas domenə yönləndirilir, sertifikatları certbot avtomatik alır. DNS-də `@` və `www` A yazıları serverə yönəlməlidir. `APP_SITE_INBOX` boşdursa forma 503 qaytarır. Bax: qərar #58 |
| `APP_DEV_SIMULATOR_KEY`, `DEV_SEED_*` | boş saxlayın (prod-da istifadə olunmur) |

**`.env` faylının ehtiyat nüsxəsini ayrıca, təhlükəsiz yerdə saxlayın** (məs. parol meneceri). Telefon nömrələri, çip açarları və TOTP sirləri bu açarlarla şifrələnib. Açarlar itərsə, bazanın nüsxəsi də oxunmaz olur.

## 5. İşə salma

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
```

Backend `prod` profilində başlayır. **ProdSafetyGuard** konfiqurasiya təhlükəli olanda tətbiqin başlamasına imkan vermir. Təhlükəli sayılan hallar:
- dev imkanları (simulyator, test hesabları, Swagger) açıqdır;
- konsol SMS/email provayderi seçilib;
- boş və ya nümunə sirr var;
- `Secure` cookie söndürülüb;
- sorğu limitləri standartdan yüksəkdir.

Səbəb loglarda yazılır.

## 6. Platforma admini və ilk şirkət

```bash
# Platforma admini (şifrə yalnız mühit dəyişənindən oxunur)
PLATFORM_ADMIN_PASSWORD='<ən azı 12 simvol>' docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  run --rm -e PLATFORM_ADMIN_PASSWORD backend --spring.main.web-application-type=none \
  --create-platform-admin=admin@sizinbrend.az --display-name="Ad Soyad"
```

Sonra https://platform.sizinbrend.az/admin ünvanına daxil olun. 2FA-nı **Hesabım** bölməsindən qurmaq tövsiyə olunur.

Şirkətin qoşulması: [tenant-onboarding.md](tenant-onboarding.md).

## 7. HTTPS sertifikatları

`certbot` konteyneri hər 30 saniyədə backend-dən domen siyahısını alır (daxili endpoint, `APP_INTERNAL_TOKEN` ilə) və:
1. sertifikatı olmayan yeni domen üçün Let's Encrypt sertifikatı alır — domen təxminən **1 dəqiqə ərzində** HTTPS ilə açılır, restart lazım deyil;
2. alınmayan sertifikatı (məs. DNS hələ yönəlməyib) 10 dəqiqədən bir yenidən sınayır (Let's Encrypt limitinə düşməmək üçün);
3. paneldən silinmiş domenin sertifikatını silir — köhnə domen dərhal bağlanır, serverdə əl ilə heç nə etmək lazım deyil;
4. saatda bir dəfə vaxtı yaxınlaşan sertifikatları yeniləyir.

Backend cavab vermirsə, heç bir sertifikat silinmir. Sertifikat alınana qədər domenə TLS bağlantısı qurulmur (fail closed). Müddətlər `CERTBOT_POLL_SECONDS`, `CERTBOT_RETRY_SECONDS`, `CERTBOT_INTERVAL_SECONDS` ilə dəyişdirilə bilər.

Yoxlama:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs certbot
```

## 8. Ehtiyat nüsxə

```bash
crontab -e
# hər gün 03:00-da
0 3 * * * cd /opt/tapstamp && ./scripts/backup.sh >> /var/log/tapstamp-backup.log 2>&1
```

- Nüsxələr `backups/<tarix>/` qovluğunda saxlanılır, 14 gündən köhnələri silinir.
- Nüsxələri müntəzəm olaraq serverdən kənara (başqa yerə) köçürün.
- Bərpa əmri `scripts/backup.sh`-in sonunda yazılıb.
- Bərpanı ən azı bir dəfə sınaq serverində yoxlayın.

## 9. Yeniləmə

```bash
cd /opt/tapstamp && git pull
./scripts/deploy.sh --build
```

`scripts/deploy.sh` **sıfır fasilə ilə** yeniləyir: istifadəçilər 502 görmür, sorğular itmir. Ardıcıllıq belədir:
1. Bazanın ehtiyat nüsxəsi götürülür.
2. Köhnə backend işləyərkən yanında yeni versiya qalxır.
3. Yeni versiya sağlam olana qədər gözlənilir. Sağlam olmasa, silinir və köhnə versiya işləməyə davam edir.
4. nginx yeni konteynerə keçirilir (`reload` açıq bağlantıları kəsmir). Eyni `reload`-da nginx konfiqurasiyası (`infra/nginx/`) da yenilənir. Konfiqurasiyada səhv varsa (`nginx -t`), heç nə dəyişmir və köhnə versiya işləməyə davam edir.
5. Köhnə konteyner yumşaq dayandırılır: yeni sorğu almır, başlanmış sorğuları 30 saniyəyə qədər bitirir.

Frontend də eyni qaydada yenilənir.

nginx üçün əlavə seçimlər:
- `./scripts/deploy.sh --only nginx` — yalnız nginx konfiqurasiyası dəyişibsə (məs. limitlər, başlıqlar). Fasiləsizdir.
- `./scripts/deploy.sh --build --nginx-image` — nginx-in öz image-i yenilənir (`infra/nginx/Dockerfile`, təhlükəsizlik yamaqları). İki nginx eyni portu tuta bilmədiyi üçün **1–3 saniyəlik fasilə** olur. Az yüklü vaxtda edin.

Adi `docker compose up -d --build` da işləyir, amma backend yenidən başlayana qədər (20–40 san) istifadəçilər **502** görür. Ondan yalnız ilk quraşdırmada istifadə edin.

**Miqrasiyalar geriyə uyğun olmalıdır.** Keçid zamanı köhnə və yeni versiya qısa müddət eyni bazada işləyir. Ona görə:
- sütun və ya cədvəl iki deploy-da silinir: əvvəl kod ondan istifadəni dayandırır, növbəti deploy-da silinir;
- yeni `NOT NULL` sütun standart dəyərlə əlavə olunur;
- ad dəyişikliyi əvəzinə yeni sütun yaradılır və məlumat köçürülür.

Baza miqrasiyaları (Flyway) başlanğıcda avtomatik işləyir: əvvəl platforma bazasında, sonra hər şirkətin bazasında. Bir şirkətin miqrasiyası uğursuz olsa, yalnız o şirkət dayanır (`MIGRATION_FAILED`), digərləri işləməyə davam edir.

**Yeniləmədən əvvəl ehtiyat nüsxə götürün.**

### GitHub-dan deploy (bir kliklə)

GitHub → **Actions** → **Deploy** → **Run workflow** (`main`). Workflow ([`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml)):
1. `main`-in son commit-i üçün CI-in (testlərin) uğurla bitdiyini yoxlayır. Bitməyibsə və ya uğursuzdursa, deploy olmur.
2. Serverə SSH ilə qoşulur. Serverdə [`scripts/remote-deploy.sh`](../scripts/remote-deploy.sh) işləyir: kilid (eyni anda bir deploy) → həmin commit-ə `git merge --ff-only` → `./scripts/deploy.sh --build` (ehtiyat nüsxə daxil).
3. Uğursuz olarsa, köhnə versiya işləməyə davam edir, GitHub-da isə xəta görünür.

SSH bağlantısı kəsilsə də deploy serverdə sona qədər gedir. Log: `/var/log/tapstamp-deploy.log`.

**Bir dəfəlik quraşdırma.**

1. Serverdə deploy açarı. Açar `authorized_keys`-də **yalnız** deploy skriptinə bağlanır (shell, port yönləndirmə yoxdur):
   ```bash
   chmod +x /opt/tapstamp/scripts/remote-deploy.sh
   ssh-keygen -t ed25519 -N '' -C github-deploy -f /root/github_deploy
   echo "command=\"/opt/tapstamp/scripts/remote-deploy.sh\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $(cat /root/github_deploy.pub)" >> /root/.ssh/authorized_keys
   ```
2. GitHub → repo → **Settings** → **Secrets and variables** → **Actions**:
   - **Secrets** → `DEPLOY_SSH_KEY`: `cat /root/github_deploy` çıxışının hamısı (`-----BEGIN` … `END-----` daxil);
   - **Secrets** → `DEPLOY_KNOWN_HOSTS`: `echo "169.58.189.40 $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)"` çıxışı (GitHub yalnız bu serverə qoşulur — saxta server ola bilməz);
   - **Variables** → `DEPLOY_HOST`: `169.58.189.40`.
3. Serverdə açarın gizli hissəsini silin (artıq yalnız GitHub-dadır): `rm /root/github_deploy /root/github_deploy.pub`.

Açarı ləğv etmək üçün `authorized_keys`-dən `github-deploy` sətrini silmək kifayətdir.

## Bazaya qoşulmaq (DBeaver, SSH tuneli)

PostgreSQL daxili şəbəkədədir; `db-tunnel` konteyneri (socat) onu serverdə yalnız `127.0.0.1:5432`-də açır (internetdən görünmür). Kompüterdən **SSH tuneli** (SSH bağlantısı
üzərindən portun ötürülməsi) ilə qoşulun:

1. DBeaver → *Yeni bağlantı* → PostgreSQL.
2. **SSH** bölməsi: *Use SSH tunnel*, host — serverin IP-si, port 22, SSH istifadəçisi və açarı (və ya şifrəsi).
3. **Main** bölməsi: host `localhost`, port `5432`, baza `loyalty_platform`, istifadəçi `postgres`, şifrə — `.env`-dəki
   `POSTGRES_PASSWORD`. *Show all databases* işarələnsin ki, şirkətlərin bazaları da görünsün.

Şirkətin bazasının adı `loyalty_platform.tenants` cədvəlinin `db_name` sütunundadır. Telefon nömrələri bazada şifrəlidir
(`phone_enc`). Cədvəlləri əl ilə dəyişməyin (Flyway miqrasiyaları idarə edir); məlumatı dəyişməzdən əvvəl ehtiyat nüsxə
götürün (§8). Serverdə 5432 portu başqa proqramla məşğuldursa, `.env`-də `POSTGRES_HOST_PORT` ilə başqa port seçin.

Terminaldan: `docker compose -f docker-compose.yml -f docker-compose.prod.yml exec postgres psql -U postgres -d loyalty_platform`.

## 10. Yoxlama siyahısı

- [ ] `https://platform.sizinbrend.az/admin` açılır, sertifikat etibarlıdır.
- [ ] Yeni şirkətin domeni HTTPS ilə açılır.
- [ ] Real nömrəyə SMS kodu gəlir.
- [ ] İşçi dəvəti emaili gəlir.
- [ ] `https://<şirkət>/actuator/env` → 404, `/actuator/health` → `UP`.
- [ ] `scripts/backup.sh` əl ilə işlədilib, nüsxə yaranıb.
- [ ] `.env` serverdən kənarda təhlükəsiz saxlanılır.

## Email: Google Workspace (smtp.gmail.com)

Sistem emailləri (işçi dəvəti, şifrə bərpası, SMS xəbərdarlığı) və saytın demo müraciətləri Google Workspace poçtu ilə göndərilir.

1. Göndərən hesabda (məs. `info@trendsolutions.az`) **2 addımlı doğrulamanı** açın: Google hesabı → Təhlükəsizlik.
2. Həmin bölmədə **Tətbiq parolları** (App passwords) → yeni parol yaradın (ad: `TapStamp server`). 16 simvollu parolu kopyalayın — bir dəfə göstərilir.
   Bu bölmə görünmürsə, Workspace admin panelində (admin.google.com → Security → Authentication → 2-step verification) istifadəçilərə 2 addımlı doğrulamanın icazəli olduğunu yoxlayın.
3. Serverdə `.env`:
   ```
   APP_MAIL_PROVIDER=smtp
   APP_MAIL_FROM=info@trendsolutions.az
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USERNAME=info@trendsolutions.az
   SMTP_PASSWORD=<tətbiq parolu, boşluqsuz>
   APP_SITE_DOMAIN=trendsolutions.az
   APP_SITE_INBOX=info@trendsolutions.az
   ```
4. `./scripts/deploy.sh --only backend` (sayt faylları və nginx üçün tam `./scripts/deploy.sh`).

`APP_MAIL_FROM` SMTP hesabının özü və ya onun təsdiqlənmiş ləqəbi (alias) olmalıdır, əks halda Gmail göndərəni dəyişdirir. Gmail gündə ~2000 məktub limitinə malikdir — bu layihənin həcmi üçün kifayətdir.
