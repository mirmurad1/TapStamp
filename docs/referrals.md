# Dost dəvəti (referral)

Müştəri dostunu dəvət edir və hər ikisi bonus möhür qazanır. Bonusun vaxtını şirkət seçir:

- **Dostun ilk gəlişində** (default, tövsiyə olunur): dost kafeyə gəlib NFC ilə ilk möhürünü alanda — həmin möhürlə
  yanaşı hər ikisinə bonus. Saxta nömrələrlə qeydiyyat heç nə qazandırmır.
- **Qeydiyyat anında**: dost linklə qeydiyyatdan keçən kimi hər ikisinə dərhal bonus. Gəliş tələb olunmadığı üçün
  başqa nömrələrlə bonus yığmaq mümkündür — admin panelində xəbərdarlıq göstərilir, aylıq limit bu riski məhdudlaşdırır.

## Axın

1. Sahib **Dost dəvəti** bölməsində proqramı aktiv edir (default olaraq söndürülüb).
2. Müştəri tətbiqinin ana səhifəsində "Dostunu dəvət et" kartı görünür: şəxsi link
   (`https://<şirkətin domeni>/r/K7P2QXAB`), "Paylaş" (telefonun paylaşma menyusu) və "Linki kopyala".
   Kod 8 simvoldur, ilk dəfə istəniləndə yaranır və dəyişmir.
3. Dost linki açır: "dostun səni dəvət edir" səhifəsi; kod brauzerdə 7 gün saxlanılır (dost linki açıb sonra
   kafedə NFC toxunuşu ilə qeydiyyatdan keçə bilər).
4. Qeydiyyatda kod serverə göndərilir; **yalnız yeni müştəri** üçün dəvət yaranır (`referrals`, status `PENDING`).
   Kod etibarsızdırsa və ya proqram söndürülübsə, qeydiyyat adi qaydada davam edir.
5. "Qeydiyyat anında" rejimində bonus qeydiyyat tranzaksiyasında verilir (filial: dəvət edənin son aktiv filialı,
   yoxdursa ilk aktiv filial). "İlk gəlişdə" rejimində — dostun **ilk təsdiqlənmiş NFC möhürü** ilə eyni tranzaksiyada (işçi təsdiqi rejimində — təsdiq anında):
   dəvət atomik olaraq `REWARDED` olur, dosta və dəvət edənə bonus möhürlər (`stamps.source = REFERRAL`) yazılır.
   Kart dolarsa, hədiyyə kartı adi qayda ilə yaranır.

## Parametrlər (sahib)

| Parametr | Dəyərlər | Default |
|---|---|---|
| Aktiv | bəli/xeyr | xeyr |
| Bonus nə vaxt verilsin | ilk gəlişdə / qeydiyyat anında | ilk gəlişdə |
| Dəvət edənə bonus möhür | 1–3 | 1 |
| Dosta bonus möhür | 1–3 | 1 |
| Aylıq limit (bir dəvət edən üçün) | 1–50 | 5 |
| Dəvətin müddəti (yalnız "ilk gəlişdə") | 7–90 gün | 30 |

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

- Miqrasiyalar: `tenant/V14__referral_reward_on.sql` (bonusun vaxtı), `tenant/V13__referrals.sql` (`referral_settings`, `referrals`, `customers.referral_code`,
  `stamps.referral_id` və `REFERRAL` mənbəyi).
- Backend: `az.tapstamp.referral` (`ReferralService`, `ReferralMapper`); bağlanma nöqtələri —
  `RegistrationService.register`, `StampClaimService` (təsdiqlənmiş möhür) və `StampApprovalService.confirm`.
- API: `GET /api/v1/me/referral`, `GET /api/v1/public/referral-program`, `GET|PUT /api/v1/admin/referrals/settings`,
  `GET /api/v1/admin/referrals/summary`, `GET /api/v1/admin/referrals` (səhifələmə ilə).
- Testlər: `ReferralIT` (tam axın, qorunma halları, müddət, işçi təsdiqi rejimi, parametrlərin yoxlanması).
