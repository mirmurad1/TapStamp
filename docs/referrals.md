# Dost dəvəti (referral)

Müştəri dostunu dəvət edir; dost **ilk dəfə kafeyə gəlib NFC ilə möhür alanda** hər ikisi bonus möhür qazanır.
Bonus qeydiyyata yox, real gəlişə verilir — saxta nömrələrlə qeydiyyat heç nə qazandırmır.

## Axın

1. Sahib **Dost dəvəti** bölməsində proqramı aktiv edir (default olaraq söndürülüb).
2. Müştəri tətbiqinin ana səhifəsində "Dostunu dəvət et" kartı görünür: şəxsi link
   (`https://<şirkətin domeni>/r/K7P2QXAB`), "Paylaş" (telefonun paylaşma menyusu) və "Linki kopyala".
   Kod 8 simvoldur, ilk dəfə istəniləndə yaranır və dəyişmir.
3. Dost linki açır: "dostun səni dəvət edir" səhifəsi; kod brauzerdə 7 gün saxlanılır (dost linki açıb sonra
   kafedə NFC toxunuşu ilə qeydiyyatdan keçə bilər).
4. Qeydiyyatda kod serverə göndərilir; **yalnız yeni müştəri** üçün dəvət yaranır (`referrals`, status `PENDING`).
   Kod etibarsızdırsa və ya proqram söndürülübsə, qeydiyyat adi qaydada davam edir.
5. Dostun **ilk təsdiqlənmiş NFC möhürü** ilə eyni tranzaksiyada (işçi təsdiqi rejimində — təsdiq anında):
   dəvət atomik olaraq `REWARDED` olur, dosta və dəvət edənə bonus möhürlər (`stamps.source = REFERRAL`) yazılır.
   Kart dolarsa, hədiyyə kartı adi qayda ilə yaranır.

## Parametrlər (sahib)

| Parametr | Dəyərlər | Default |
|---|---|---|
| Aktiv | bəli/xeyr | xeyr |
| Dəvət edənə bonus möhür | 1–3 | 1 |
| Dosta bonus möhür | 1–3 | 1 |
| Aylıq limit (bir dəvət edən üçün) | 1–50 | 5 |
| Dəvətin müddəti | 7–90 gün | 30 |

Dəyişikliklər audit jurnalına yazılır (`REFERRAL_SETTINGS_UPDATED`).

## Qorunma

- Yalnız yeni müştəri: bu şirkətdə qeydiyyatı olan nömrə dəvət oluna bilməz (`referrals.friend_id` unikaldır).
- Özünü dəvət etmək mümkün deyil; bloklanmış/silinmiş müştərinin kodu işləmir, bloklanmış dəvət edən bonus almır.
- Aylıq limit (şirkətin təqvim ayı): limitdən sonrakı dəvətlər `LIMIT_REACHED` kimi qeyd olunur, bonus verilmir.
- Müddət: dost vaxtında gəlməsə, dəvət bonussuz bağlanır (hesabatda "Müddəti bitib").
- Bonus yalnız bir dəfə: `UPDATE … WHERE status = 'PENDING' RETURNING` — paralel toxunuşlarda da təkrarlanmır.
- Sahibin proqramı söndürməsi gözləyən dəvətlərə bonus verilməsini dayandırır.

## Hesabat

**Dost dəvəti** səhifəsində: dəvətlərin sayı, gəlib bonus qazananlar, gözləyənlər, verilən bonus möhürlər, müddəti
bitənlər və limitə düşənlər; dəvətlərin siyahısı (dəvət edən, dost — maskalı nömrə ilə, vəziyyət, tarix). Müştərinin
tarixçəsində bonus möhürlər "Möhür (dost dəvəti)" kimi görünür.

## Texniki

- Miqrasiya: `tenant/V13__referrals.sql` (`referral_settings`, `referrals`, `customers.referral_code`,
  `stamps.referral_id` və `REFERRAL` mənbəyi).
- Backend: `az.tapstamp.referral` (`ReferralService`, `ReferralMapper`); bağlanma nöqtələri —
  `RegistrationService.register`, `StampClaimService` (təsdiqlənmiş möhür) və `StampApprovalService.confirm`.
- API: `GET /api/v1/me/referral`, `GET /api/v1/public/referral-program`, `GET|PUT /api/v1/admin/referrals/settings`,
  `GET /api/v1/admin/referrals/summary`, `GET /api/v1/admin/referrals` (səhifələmə ilə).
- Testlər: `ReferralIT` (tam axın, qorunma halları, müddət, işçi təsdiqi rejimi, parametrlərin yoxlanması).
