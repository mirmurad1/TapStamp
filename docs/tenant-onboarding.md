# Yeni şirkətin qoşulması

Hər şirkətin ayrıca bazası (database-per-tenant) və öz domeni var. Şirkət yalnız domenə görə tanınır: `moyka.sizinbrend.az` açılanda "Moyka"-nın məlumatı göstərilir.

## 1. Domeni seçin

| Variant | Nümunə | Nə lazımdır |
|---|---|---|
| **Alt domen (tövsiyə)** | `moyka.sizinbrend.az` | Heç nə: wildcard DNS yazısı artıq var |
| **Şirkətin öz domeni** | `bonus.moyka.az` | Şirkət öz DNS-ində yazı əlavə edir: `bonus` → `CNAME` → `moyka.sizinbrend.az` |

Alt domen adında yalnız latın kiçik hərfləri, rəqəmlər və tire olmalıdır: `ç`, `ə`, `ş` və boşluq olmaz.

**Domeni əvvəlcədən dəqiqləşdirin.** NFC çipinə yazılan link şirkətin əsas domeninə bağlıdır. Çip proqramlaşdırılandan sonra domen dəyişsə, çipləri yenidən proqramlaşdırmaq lazım gələcək.

## 2. Şirkəti yaradın

**Platforma panelindən.** https://platform.sizinbrend.az/admin → **Yeni şirkət**.
- Bu yol `APP_PROVISIONING_ENABLED=true` tələb edir (prod-da standart olaraq söndürülüb).
- Kimliyin təsdiqi (step-up) istənir.

**Və ya serverdə əmrlə:**

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml run --rm backend \
  --spring.main.web-application-type=none \
  --provision-tenant=moyka --domain=moyka.sizinbrend.az \
  --name-az="Moyka" --name-en="Moyka" --name-ru="Мойка"
```

Bu proses aşağıdakıları edir:
- ayrıca baza (`loyalty_t_moyka`) və iki DB rolu yaradır;
- miqrasiyaları işə salır;
- domeni qeydiyyata alır.

Yarımçıq qalsa, eyni əmri təkrar işə salmaq kifayətdir.

## 3. HTTPS

Əlavə iş lazım deyil: `certbot` yeni domeni növbəti dövrdə (≤ 1 saat) görür və sertifikat alır. Yoxlamaq üçün:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs certbot | tail
```

## 4. Sahibə dəvət

1. Platforma panelində şirkətin səhifəsinə daxil olun.
2. **Sahibi dəvət et** bölməsinə sahibin emailini yazın.
3. Sahib emaildəki linklə (72 saat etibarlıdır) hesab yaradır.

## 5. Sahibin ilk addımları (admin paneli: `https://moyka.sizinbrend.az/admin`)

1. **Parametrlər**:
   - şirkətin adı və təsviri 3 dildə;
   - brend rəngi, saat qurşağı;
   - SMS göndərən adı (operatorda təsdiqlənmiş olmalıdır);
   - filiallar: ad, ünvan, iş saatları;
   - möhür qaydası: möhürlər arası vaxt, gündəlik limit;
   - hədiyyə: neçənci möhürdə verilir, adı 3 dildə, kartın müddəti.
2. **Komanda**: menecer və işçilərin dəvəti.
3. **NFC çipləri**: bu addımı **siz** (platforma admini) edirsiniz, şirkətin səhifəsində: hər piştaxta üçün çipin qeydiyyatı → provisioning paketi → çipin proqramlaşdırılması ([nfc-ntag424-programming.md](nfc-ntag424-programming.md)) → ilk toxunuşla aktivləşmə. Hər şirkətdə ilk çip pulsuzdur, qalanları pulludur (say kartda görünür; limit `APP_NFC_FREE_TAGS`).
4. **Hesabım**: 2FA-nı qurmaq tövsiyə olunur.
5. **Menyu və xidmətlər** (yalnız əlavə alınıbsa): əvvəlcə **siz** platforma panelində şirkətin səhifəsində **Əlavələr → Menyu və xidmətlər → Aç** edirsiniz. Sonra sahib admin panelində bölmənin adını seçir (Menyu, Xidmətlər və ya Qiymətlər), kateqoriyaları və məhsulları qiymət və şəkillə əlavə edir, **"Stol üçün QR kod"**dan QR-ı yükləyib çap edir. Müştəri QR-ı skan edəndə `https://moyka.sizinbrend.az/menu` açılır (giriş tələb etmir).

## 6. Domenlərin idarəsi

Platforma panelində şirkətin səhifəsində **Domenlər** bölməsi var:
- domen əlavə etmək;
- əsas domeni dəyişmək (step-up tələb edir);
- köhnə domeni silmək. Əsas domen silinə bilməz.

Domen dəyişikliyi dərhal qüvvəyə minir.

## 7. Şirkəti dayandırmaq

Platforma panelində **Şirkəti dayandır** (step-up tələb edir). Şirkətin domeni dərhal `503` qaytarır, məlumat silinmir. **Bərpa et** ilə yenidən açılır.
