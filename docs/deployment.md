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
| `APP_SMS_PROVIDER`, `APP_SMS_HTTP_URL`, `APP_SMS_HTTP_API_KEY` | `http` və provayderin məlumatları |
| `APP_MAIL_PROVIDER`, `APP_MAIL_FROM`, `SMTP_*` | `smtp` və poçt serverinin məlumatları |
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
  run --rm backend --spring.main.web-application-type=none \
  --create-platform-admin=admin@sizinbrend.az --display-name="Ad Soyad"
```

Sonra https://platform.sizinbrend.az/admin ünvanına daxil olun. 2FA-nı **Hesabım** bölməsindən qurmaq tövsiyə olunur.

Şirkətin qoşulması: [tenant-onboarding.md](tenant-onboarding.md).

## 7. HTTPS sertifikatları

`certbot` konteyneri saatda bir dəfə bunları edir:
1. backend-dən domen siyahısını alır (daxili endpoint, `APP_INTERNAL_TOKEN` ilə);
2. sertifikatı olmayan domen üçün Let's Encrypt sertifikatı alır;
3. vaxtı yaxınlaşanları yeniləyir.

nginx sertifikatları 10 dəqiqədən bir yenidən oxuyur. Nəticədə yeni şirkət domeni təxminən **1 saat ərzində** HTTPS ilə açılır. Sertifikat alınana qədər bu domenə TLS bağlantısı qurulmur (fail closed).

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
4. nginx yeni konteynerə keçirilir (`reload` açıq bağlantıları kəsmir).
5. Köhnə konteyner yumşaq dayandırılır: yeni sorğu almır, başlanmış sorğuları 30 saniyəyə qədər bitirir.

Frontend də eyni qaydada yenilənir.

Adi `docker compose up -d --build` da işləyir, amma backend yenidən başlayana qədər (20–40 san) istifadəçilər **502** görür. Ondan yalnız ilk quraşdırmada istifadə edin.

**Miqrasiyalar geriyə uyğun olmalıdır.** Keçid zamanı köhnə və yeni versiya qısa müddət eyni bazada işləyir. Ona görə:
- sütun və ya cədvəl iki deploy-da silinir: əvvəl kod ondan istifadəni dayandırır, növbəti deploy-da silinir;
- yeni `NOT NULL` sütun standart dəyərlə əlavə olunur;
- ad dəyişikliyi əvəzinə yeni sütun yaradılır və məlumat köçürülür.

Baza miqrasiyaları (Flyway) başlanğıcda avtomatik işləyir: əvvəl platforma bazasında, sonra hər şirkətin bazasında. Bir şirkətin miqrasiyası uğursuz olsa, yalnız o şirkət dayanır (`MIGRATION_FAILED`), digərləri işləməyə davam edir.

**Yeniləmədən əvvəl ehtiyat nüsxə götürün.**

## 10. Yoxlama siyahısı

- [ ] `https://platform.sizinbrend.az/admin` açılır, sertifikat etibarlıdır.
- [ ] Yeni şirkətin domeni HTTPS ilə açılır.
- [ ] Real nömrəyə SMS kodu gəlir.
- [ ] İşçi dəvəti emaili gəlir.
- [ ] `https://<şirkət>/actuator/env` → 404, `/actuator/health` → `UP`.
- [ ] `scripts/backup.sh` əl ilə işlədilib, nüsxə yaranıb.
- [ ] `.env` serverdən kənarda təhlükəsiz saxlanılır.
