# Ekran görüntüləri

Hər mərhələdə əsas ekranların görüntüləri. Fayl adı: `phase<N>-<sıra>-<ekran>.png`. Yeni görüntülər bu qovluğa əlavə olunur.

Görüntülər işləyən dev mühitində (`docker compose`) avtomatik çəkilir:

```bash
node scripts/screenshots/customer-flow.cjs
```
```bash
node scripts/screenshots/admin-settings.cjs
node scripts/screenshots/account-team.cjs
node scripts/screenshots/admin-pages.cjs
node scripts/screenshots/platform-pages.cjs
node scripts/screenshots/stamp-animation.cjs
node scripts/screenshots/phone-and-languages.cjs
```

`customer-flow.cjs` test müştərisi yaradır və kartını dev bazasında doldurur — yalnız dev mühiti üçündür.

## Mərhələ 5 — NFC toxunuşu, möhürlər, hədiyyə kartları

| Fayl | Ekran |
|---|---|
| `phase5-01-admin-nfc-simulator.png` | Admin: NFC simulyatoru (imzalı link və QR) |
| `phase5-02-tap-guest.png` | Toxunuşdan sonra, daxil olmamış müştəri |
| `phase5-03-stamp-added-first-login.png` | İlk girişdən sonra möhür (izah və "Fərqli nömrə ilə yoxla") |
| `phase5-04-card-complete-celebration.png` | Kart doldu: konfeti və təbrik |
| `phase5-05-gift-card-code-qr.png` | Hədiyyə kartı: kod və QR |
| `phase5-06-home-returning.png` | Ana səhifə (sonrakı açılış) |
| `phase5-07-other-tenant-guest.png` | Başqa şirkət (Demo Çay Evi), qonaq |
| `phase5-08-admin-settings.png` | Admin: Parametrlər — möhür qaydası və hədiyyə |

## Mərhələ 6 — hədiyyə kartının təhvili

Skript: `node scripts/screenshots/gift-card-redemption.cjs` (sahib vaxt limitini müvəqqəti söndürür, sonda bərpa edir).

| Fayl | Ekran |
|---|---|
| `phase6-01-stamp-confetti.png` | Möhür vurulanda kiçik konfeti |
| `phase6-02-staff-gift-card-valid.png` | İşçi konsolu: kod yoxlandı — həqiqidir |
| `phase6-03-staff-gift-card-redeemed.png` | İşçi konsolu: hədiyyə təhvil verildi |
| `phase6-04-customer-redeemed.png` | Müştərinin ekranı: "Hədiyyəniz təhvil verildi!" |
| `phase6-05-staff-gift-card-used.png` | Eyni kod ikinci dəfə: "İstifadə olunub" (tarix, filial) |
| `phase6-06-customer-gift-card-history.png` | Müştəri: hədiyyə kartları tarixçəsi |
| `phase6-08-admin-settings.png` | Admin: Parametrlər (yenilənmiş) |
| `phase6-09-admin-confirm-dialog.png` | Tətbiqin öz təsdiq dialoqu (brauzerin `confirm` pəncərəsi əvəzinə) |
| `phase6-10-account-2fa.png` | Hesabım: 2FA — müvəqqəti söndür və sil düymələri |
| `phase6-11-account-2fa-remove-dialog.png` | 2FA-nın silinməsi: cari şifrə ilə təsdiq |
| `phase6-12-team-delete.png` | Komanda: "Sil" düyməsi |
| `phase6-13-team-delete-confirm.png` | İşçinin silinməsinin təsdiqi (soft delete) |
| `phase7-01-dashboard.png` | Admin: idarəetmə paneli — göstəricilər, qrafik, istilik xəritəsi, dillər |
| `phase7-02-dashboard-mobile.png` | Göstəricilər paneli telefonda |
| `phase7-03-customers.png` | Admin: müştərilər — axtarış, filtrlər, sıralama |
| `phase7-04-customer-detail.png` | Müştərinin detalları: göstəricilər, tarixçə, fırıldaq işarələri |
| `phase7-05-customer-adjust.png` | Möhürlərin əl ilə düzəlişi (səbəb məcburi) |
| `phase7-06-tags.png` | Admin: NFC çipləri — açar vəziyyəti, son toxunuş, jurnal, rotasiya |
| `phase7-07-audit.png` | Admin: audit jurnalı |
| `phase7-08-settings.png` | Parametrlər: möhür qaydası, hədiyyə, şirkət (3 dil, brend rəngi), filiallar |
| `phase7-09-branch-dialog.png` | Filialın düzəlişi: ad/ünvan 3 dildə, iş saatları |
| `phase7-10-platform-tenant-domains.png` | Platforma: şirkətin səhifəsi — göstəricilər, sahiblər, domenlər |
| `phase7-11-platform-audit.png` | Platforma audit jurnalı |
| `phase7-12-stamp-animation-1.png` | Möhür ekranı: damğa enir |
| `phase7-13-stamp-animation-2.png` | Damğa vurulur (mürəkkəb dalğası, konfeti) |
| `phase7-14-stamp-animation-3.png` | Son vəziyyət: möhür izi, "Möhür əlavə olundu!" |
| `phase8-01-app-icon.png` | PWA: şirkətin brend rəngində yaradılan tətbiq ikonu |
| `phase8-02-offline.png` | Oflayn ekran (3 dildə) |
| `phase8-03-customer-home.png` | Müştəri: ana səhifə (telefon) |
| `phase8-04-customer-settings.png` | Müştəri: parametrlər, "Ana ekrana əlavə et" |
| `phase8-05-login-ru.png` | Giriş səhifəsi rus dilində (telefon) |
| `phase8-06-dashboard.png` | Admin paneli (masaüstü) |
| `phase8-07-dashboard-phone.png` | Admin paneli telefonda (üfüqi daşma yoxdur) |
| `phase8-09-terms-dynamic.png` | İstifadə şərtləri: möhür limiti və hədiyyə şirkətin parametrlərindən |
| `phase8-08-legal.png` | Məxfilik siyasəti: nümunə qeydi və versiya sətri olmadan |
| `phase8-10-site-icons.png` | Sayt ikonları: admin (ümumi), şirkətlərin öz brend rəngində ikonu |
| `phase10-platform-tenant-tags.png` | Platforma → şirkət → NFC çipləri: pulsuz/pullu say, qeydiyyat və idarə |
| `phase10-owner-tags-readonly.png` | Şirkət sahibi: çiplər yalnız baxış üçün, "Pulsuz/Pullu" nişanı |
| `phase10-reward-threshold-dialog.png` | Hədiyyə həddi dəyişəndə: yarımçıq kartlar köhnə qayda ilə bitsin və ya yeni qayda hamıya dərhal |
| `phase10-reward-apply-now-password.png` | "Hamıya dərhal" seçiləndə kimliyin təsdiqi (2FA yoxdursa şifrə) |
