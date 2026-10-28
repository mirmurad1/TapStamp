# Xəta kodları

> Bu fayl avtomatik yaradılır: `node scripts/gen-error-codes.cjs`. Əl ilə dəyişməyin.

API xətaları **Problem Details** (RFC 9457) formatında qaytarılır:

```json
{
  "type": "urn:tapstamp:problem:stamp-cooldown-active",
  "title": "Bir az gözləyin",
  "status": 409,
  "detail": "Növbəti möhürü 14 dəqiqədən sonra ala bilərsiniz.",
  "code": "STAMP_COOLDOWN_ACTIVE",
  "retryAfterSeconds": 840,
  "traceId": "4f1c2a…"
}
```

- `code` sabitdir — frontend mətni bu koda görə öz tərcüməsindən göstərir (`src/locales/*/errors.json`).
- `title` və `detail` sorğunun dilindədir (`Accept-Language`, istifadəçinin profili və ya şirkətin standart dili).
- Daxili xətalarda (500) stack trace və ya texniki detal heç vaxt qaytarılmır, yalnız `traceId` (loglarda axtarış üçün).
- Validasiya xətalarında əlavə olaraq `errors: [{field, code, message}]` massivi gəlir.

Aşağıdakı cədvəldə mətnlər Azərbaycan dilindədir (`messages_az.properties`); `…` — dinamik dəyər.

| Kod | HTTP | Başlıq | İzah |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Məlumatlar düzgün deyil | Bəzi sahələr səhv doldurulub. Yoxlayıb yenidən cəhd edin. |
| `MALFORMED_REQUEST` | 400 | Sorğu oxuna bilmədi | Sorğunun formatı düzgün deyil. |
| `UNAUTHENTICATED` | 401 | Daxil olmaq lazımdır | Davam etmək üçün hesabınıza daxil olun. |
| `FORBIDDEN` | 403 | İcazə yoxdur | Bu əməliyyat üçün icazəniz yoxdur. |
| `CSRF_INVALID` | 403 | Təhlükəsizlik yoxlaması alınmadı | Səhifəni yeniləyib yenidən cəhd edin. |
| `NOT_FOUND` | 404 | Tapılmadı | Axtardığınız məlumat tapılmadı. |
| `METHOD_NOT_ALLOWED` | 405 | Əməliyyat dəstəklənmir | Bu ünvan üçün belə sorğu dəstəklənmir. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Format dəstəklənmir | Sorğunun məzmun formatı dəstəklənmir. |
| `PAYLOAD_TOO_LARGE` | 413 | Sorğu çox böyükdür | Göndərilən məlumatın həcmi icazə veriləndən çoxdur. |
| `RATE_LIMITED` | 429 | Bir az gözləyin | Çox sayda sorğu göndərildi. Bir azdan yenidən cəhd edin. |
| `SESSION_EXPIRED` | 401 | Sessiyanın vaxtı bitib | Təhlükəsizlik üçün sessiyanız bağlandı. Yenidən daxil olun. |
| `LOGIN_FAILED` | 401 | Giriş alınmadı | Email və ya şifrə yanlışdır. |
| `LOGIN_TEMPORARILY_BLOCKED` | 429 | Giriş müvəqqəti bloklanıb | Çox sayda uğursuz cəhd oldu. Bir neçə dəqiqədən sonra yenidən cəhd edin. |
| `MFA_INVALID` | 401 | Kod yanlışdır | Autentifikator tətbiqindəki kodu və ya bərpa kodunu yoxlayıb yenidən daxil edin. |
| `MFA_ALREADY_ENABLED` | 409 | İki addımlı yoxlama artıq aktivdir | Hesabınızda iki addımlı yoxlama artıq qurulub. |
| `MFA_NOT_ENABLED` | 409 | İki addımlı yoxlama aktiv deyil | Bu əməliyyat üçün iki addımlı yoxlamanın vəziyyəti uyğun deyil. |
| `STEP_UP_REQUIRED` | 403 | Təsdiq tələb olunur | Bu əməliyyat üçün autentifikator tətbiqindəki kodu yenidən daxil edin. |
| `PASSWORD_POLICY_VIOLATION` | 400 | Şifrə tələblərə uyğun deyil | Ən azı 12 simvoldan ibarət, asan təxmin edilməyən şifrə seçin. |
| `CURRENT_PASSWORD_INVALID` | 400 | Cari şifrə yanlışdır | Cari şifrənizi düzgün daxil edin. |
| `TOKEN_INVALID` | 400 | Link etibarsızdır | Linkin vaxtı keçib və ya artıq istifadə olunub. Yenisini istəyin. |
| `ALREADY_EXISTS` | 409 | Artıq mövcuddur | Bu məlumatla qeyd artıq mövcuddur. |
| `PROVISIONING_DISABLED` | 409 | Şirkət yaratmaq söndürülüb | Bu serverdə yeni şirkət yaratmaq söndürülüb. Əmr sətri vasitəsilə yaradın. |
| `PHONE_INVALID` | 400 | Telefon nömrəsi düzgün deyil | Mobil nömrənizi daxil edin, məsələn: 050 123 45 67. |
| `PHONE_REGION_NOT_ALLOWED` | 400 | Bu ölkənin nömrəsi qəbul edilmir | Bu şirkətin loyallıq proqramında yalnız icazə verilən ölkələrin mobil nömrələri istifadə oluna bilər. |
| `OTP_INVALID` | 400 | Kod yanlışdır | SMS-dəki 6 rəqəmli kodu yoxlayıb yenidən daxil edin. |
| `OTP_EXPIRED` | 400 | Kodun vaxtı keçib | Yeni kod istəyin. |
| `OTP_ATTEMPTS_EXCEEDED` | 429 | Çox sayda yanlış cəhd | Bu kod artıq qəbul olunmur. Bir az sonra yeni kod istəyin. |
| `CONSENT_REQUIRED` | 403 | Razılıq tələb olunur | Davam etmək üçün məxfilik siyasəti və istifadə şərtləri ilə razılaşın. |
| `REFRESH_RACE` | 409 | Sessiya yenilənir | Sorğunu təkrarlayın. |
| `CUSTOMER_BLOCKED` | 403 | Hesab bloklanıb | Hesabınız bloklanıb. Ətraflı məlumat üçün şirkətlə əlaqə saxlayın. |
| `TAP_MALFORMED` | 400 | Link düzgün deyil | Bu toxunuş linki natamamdır. Telefonu çipə yenidən yaxınlaşdırın. |
| `TAG_UNKNOWN` | 404 | Çip tanınmadı | Bu çip qeydiyyatda deyil. İşçiyə müraciət edin. |
| `TAG_DISABLED` | 403 | Çip aktiv deyil | Bu çip hazırda işləmir. İşçiyə müraciət edin. |
| `TAP_SIGNATURE_INVALID` | 400 | Toxunuş təsdiqlənmədi | Toxunuşu yoxlamaq mümkün olmadı. Telefonu çipə yenidən yaxınlaşdırın. |
| `TAP_REPLAYED` | 409 | Bu link artıq istifadə olunub | Hər toxunuş yalnız bir dəfə keçərlidir. Telefonu çipə yenidən yaxınlaşdırın. |
| `TAP_TOKEN_EXPIRED` | 410 | Vaxt bitdi | Toxunuşun vaxtı keçib. Telefonu çipə yenidən yaxınlaşdırın. |
| `TAP_TOKEN_USED` | 409 | Toxunuş artıq istifadə olunub | Bu toxunuşa görə möhür artıq verilib. Yeni möhür üçün çipə yenidən toxunun. |
| `TAP_BINDING_MISMATCH` | 403 | Başqa cihaz | Möhürü yalnız çipə toxunduğunuz telefonda almaq olar. |
| `STAMP_COOLDOWN_ACTIVE` | 409 | Bir az gözləyin | Növbəti möhürü bir az sonra ala bilərsiniz. |
| `STAMP_DAILY_LIMIT_REACHED` | 409 | Bugünkü limit doldu | Bu gün üçün maksimum möhür sayına çatmısınız. Sabah yenidən gəlin! |
| `OUTSIDE_BUSINESS_HOURS` | 409 | Hazırda bağlıyıq | Möhürlər yalnız iş saatlarında verilir. |
| `STAMP_NOT_PENDING` | 409 | Möhür artıq emal olunub | Bu möhür artıq təsdiqlənib, rədd edilib və ya vaxtı keçib. |
| `GIFT_CARD_NOT_FOUND` | 404 | Kart tapılmadı | Belə hədiyyə kartı yoxdur. Kodu yoxlayıb yenidən daxil edin. |
| `GIFT_CARD_ALREADY_REDEEMED` | 409 | Kart artıq istifadə olunub | Bu hədiyyə kartı əvvəllər təhvil verilib. |
| `GIFT_CARD_EXPIRED` | 410 | Kartın müddəti bitib | Bu hədiyyə kartının istifadə müddəti başa çatıb. |
| `GIFT_CARD_REVOKED` | 409 | Kart ləğv edilib | Bu hədiyyə kartı ləğv edilib və istifadə oluna bilməz. |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Sorğu artıq fərqli məzmunla göndərilib | Bu əməliyyat açarı başqa məlumatla istifadə olunub. Səhifəni yeniləyib yenidən cəhd edin. |
| `STAMP_ADJUSTMENT_TOO_LARGE` | 409 | Bu qədər möhür silmək olmaz | Müştərinin kartında və istifadə olunmamış hədiyyə kartlarında bu qədər möhür yoxdur. Təhvil verilmiş hədiyyəni geri almaq mümkün deyil. |
| `TAG_ROTATION_IN_PROGRESS` | 409 | Açar yenilənməsi davam edir | Yeni açarlar artıq ixrac olunub. Əvvəlcə proqramlaşdırılmış çipə toxunun ki, keçid tamamlansın. |
| `PROVISIONING_ALREADY_EXPORTED` | 409 | Açarlar artıq göstərilib | Təhlükəsizlik üçün çip açarları yalnız bir dəfə göstərilir. Yenidən lazımdırsa, açarı yeniləyin. |
| `DOMAIN_IS_PRIMARY` | 409 | Əsas domen silinə bilməz | Əvvəlcə başqa domeni əsas edin. Çiplərdəki linklər əsas domenə yazılıb. |
| `CAMPAIGN_NO_RECIPIENTS` | 409 | Alıcı yoxdur | Seçilmiş auditoriyada kampaniya SMS-i ala bilən müştəri yoxdur. |
| `CAMPAIGN_STATE_CHANGED` | 409 | Kampaniyanın vəziyyəti dəyişib | Kampaniya artıq təsdiqlənib, rədd edilib və ya ləğv olunub. Səhifəni yeniləyin. |
| `PLAN_FEATURE_UNAVAILABLE` | 403 | Paketinizdə yoxdur | Bu imkan cari paketinizə daxil deyil. Paketi yüksəltmək üçün bizimlə əlaqə saxlayın. |
| `PLAN_LIMIT_REACHED` | 409 | Paketin limiti dolub | Paketinizin limiti dolub. Daha çoxu üçün paketi yüksəldin. |
| `TIER_THRESHOLD_TAKEN` | 409 | Bu hədd artıq var | Bu ziyarət sayı ilə başqa səviyyə artıq var. Fərqli hədd seçin. |
| `TIER_IN_USE` | 409 | Səviyyə istifadə olunub | Bu səviyyə artıq bonus möhür verib, ona görə silinə bilməz. Adını, həddini və ya bonusunu dəyişə bilərsiniz. |
| `PLAN_TRIAL_EXPIRED` | 402 | Sınaq müddəti bitib | Pulsuz sınaq müddəti bitib, xidmət dayandırılıb. |
| `PLAN_TRIAL_ALREADY_USED` | 409 | Sınaq artıq istifadə olunub | Bu paketin pulsuz sınağı bu şirkət üçün artıq istifadə olunub. |
| `SITE_INBOX_UNAVAILABLE` | 503 | Müraciət göndərilmədi | Müraciətiniz hazırda göndərilə bilmədi. Bir azdan yenidən cəhd edin və ya info@trendsolutions.az ünvanına yazın. |
| `TENANT_UNAVAILABLE` | 503 | Xidmət müvəqqəti əlçatmazdır | Bu şirkətin loyallıq xidməti hazırda işləmir. Bir az sonra yenidən cəhd edin. |
| `INTERNAL_ERROR` | 500 | Gözlənilməz xəta | Nəsə alınmadı. Bir azdan yenidən cəhd edin. |
