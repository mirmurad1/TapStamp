# Web push bildirişləri

Müştəri tətbiqi (PWA) telefona **pulsuz** bildiriş göndərir — SMS kimi hər mesaj üçün ödəniş yoxdur. Giriş kodları
SMS-də qalır (push-un çatdırılması zəmanətli deyil).

## Necə işləyir

1. Müştəri tətbiqdə "Bildirişləri aç" basır → brauzer icazə soruşur (yalnız müştərinin kliki ilə).
2. Brauzer push xidmətində (Google, Apple, Mozilla, Microsoft) bu cihaz üçün ünvan yaradır; ünvan və şifrələmə
   açarları serverə göndərilir (`push_subscriptions`, şirkətin bazası).
3. Hadisə baş verəndə server mesajı **şifrələyib** (RFC 8291 — push xidməti mətni oxuya bilmir) və **VAPID** ilə
   imzalayıb (RFC 8292) həmin ünvana göndərir.
4. Telefonda service worker (`public/sw.js`) bildirişi göstərir; klikdə tətbiq lazımi səhifədə açılır.

| Cihaz | Vəziyyət |
|---|---|
| Android (Chrome, Samsung) | Brauzerdə də işləyir |
| iPhone (iOS 16.4+) | Yalnız tətbiq **ana ekrana əlavə olunubsa** — tətbiq bunu izah edir |
| Kompüter | Bütün əsas brauzerlər |

## Bildirişlər

| Hadisə | Kimə | Mətn (az) |
|---|---|---|
| İşçi möhürü təsdiqlədi | müştəriyə | "Möhürün təsdiqləndi ✅ — Kartında X / Y möhür var." |
| Kart doldu (təsdiq və ya bonusla) | müştəriyə | "Hədiyyən hazırdır 🎁" (klikdə hədiyyələr səhifəsi) |
| Dostu ilk dəfə gəldi | dəvət edənə | "Dostun gəldi 🎉 — kartına N bonus möhür əlavə olundu." |

Mətnlər müştərinin dilində (`push.*` açarları, `messages_{az,en,ru}.properties`). Bildiriş tranzaksiya commit
olandan sonra arxa planda gedir; möhür ləğv olunarsa bildiriş getmir.

## Müştəri tətbiqində

- Ana səhifədə bir dəfəlik təklif ("Hədiyyəni qaçırma"): push dəstəklənir, icazə hələ soruşulmayıb, müştəri
  bağlamayıb; iPhone-da yalnız ana ekrandakı tətbiqdə.
- Parametrlər → **Bildirişlər**: bu cihazda aç/söndür; bloklanıbsa və ya iPhone-da quraşdırma lazımdırsa izah.
- "Başqa nömrə ilə daxil ol" (çıxış) bu cihazın abunəsini də silir — ortaq telefonda əvvəlki şəxsin bildirişləri
  gəlməsin.

## Təhlükəsizlik və məxfilik

- Ünvan yalnız tanınmış push xidmətlərinə ola bilər (https, standart port): `fcm.googleapis.com`,
  `updates.push.services.mozilla.com`, `push.services.mozilla.com`, `web.push.apple.com`, `*.push.apple.com`,
  `*.notify.windows.com`. Əks halda müştəri istənilən URL göndərib serveri oraya sorğu atmağa məcbur edə bilərdi
  (SSRF).
- Push xidməti abunənin etibarsız olduğunu bildirəndə (404/410) abunə silinir. Müştərinin ən çox 10 cihazı saxlanılır.
- Hesab silinəndə və müştəri bloklananda bütün abunələri silinir.

## Quraşdırma

VAPID açarları **bir dəfə** yaradılır: `scripts/gen-vapid-keys.sh` (və ya `.ps1`) iki sətri çap edir — `.env`-ə
əlavə edin (`APP_PUSH_VAPID_PUBLIC_KEY`, `APP_PUSH_VAPID_PRIVATE_KEY`). Açar dəyişdirilsə, bütün abunələr etibarsız
olur. Açar yoxdursa push söndürülür (tətbiqdə bölmə görünmür). Dev-də açar yoxdursa, hər başlanğıcda müvəqqəti açar
yaranır.

## Texniki

- Kriptoqrafiya əlavə kitabxanasız, JDK ilə: `WebPushCrypto` (P-256 ECDH, HKDF, AES-128-GCM), `WebPushSender` (ES256
  JWT). `WebPushCryptoTest` RFC 8291-in rəsmi nümunəsi ilə bayt-bayt yoxlayır.
- API: `GET /api/v1/public/push`, `POST /api/v1/me/push-subscriptions`, `POST /api/v1/me/push-subscriptions/remove`.
- Testlər: `PushIT` (abunə, şifrəli və imzalı bildiriş — test "brauzeri" deşifrə edir, 410 ilə silinmə, söndürmə).

## Növbəti mərhələ

Kampaniyalarda "push varsa push, yoxdursa SMS" və avtomatik xatırlatmalar ("hədiyyəyə 1 möhür qaldı", "30 gündür
gəlməmisən").
