# Paketlər və sınaq müddəti

Qərar: `architecture-plan.md` §18, #57.

## Paketlər

| | Basic | Standard | Premium |
|---|---|---|---|
| Filial (paketə daxil) | 1 | 3 | 5 + əlavə filiallar |
| İşçi (sahiblərdən başqa) | 3 | 10 | limitsiz |
| NFC möhür kartı, müştəri tətbiqi, hədiyyə kartları, müştərilər, işçi konsolu | ✓ | ✓ | ✓ |
| SMS kampaniyaları (hər SMS ayrıca ödənilir) | ✓ | ✓ | ✓ |
| Göstəricilər paneli: əsas göstəricilər | ✓ | ✓ | ✓ |
| Göstəricilər paneli: qrafiklər, geri qayıtma, dillər (`FULL_ANALYTICS`) | — | ✓ | ✓ |
| Telefona bildirişlər — web push (`PUSH`) | — | ✓ | ✓ |
| Dost dəvəti (`REFERRALS`) | — | ✓ | ✓ |
| Müştərilərin CSV ixracı (`CSV_EXPORT`) | — | ✓ | ✓ |
| Bonus qaydaları — xoşbəxt saatlar, tarixli kampaniyalar (`BONUS_RULES`) | — | ✓ | ✓ |
| Müştəri səviyyələri — ziyarət sayına görə, imtiyaz əlavə möhür (`TIERS`) | — | ✓ | ✓ |
| Möhür kampaniyası (`STAMP_CAMPAIGNS`) | — | — | ✓ |
| Avtomatik xatırlatmalar (`REMINDERS`) | — | — | ✓ |
| Audit jurnalı (`AUDIT_LOG`) | — | — | ✓ |
| Filiallar üzrə müqayisə — sahibin filial seçməsi (`BRANCH_COMPARISON`) | — | — | ✓ |
| Dəvət statistikası Göstəricilər panelində (`REFERRAL_STATS`) | — | — | ✓ |

Paketlər və imkanlar kodda bir yerdədir: `be-tapstamp/.../plan/Plan.java`. Frontend-də hər imkanın ilk olduğu paket
`fe-tapstamp/src/shared/plan/plan.ts`-dədir (`FEATURE_PLAN`) — yalnız "hansı paketə keçmək lazımdır" mətni üçün.

## Harada saxlanılır

Platforma bazasında:

- `tenant_subscriptions` — cari paket: `plan`, `status` (`TRIAL` — sınaq, `ACTIVE` — ödənişli), `trial_ends_at`,
  `extra_branches` (yalnız Premium). Sətir yoxdursa — Premium, ödənişli (paketlərdən əvvəl yaradılmış şirkətlər;
  miqrasiya V8 mövcud şirkətlərə belə sətir yazır).
- `tenant_plan_trials` — istifadə olunmuş sınaqlar: (şirkət, paket) cütü üçün **yalnız bir** sətir.

Paket hər sorğuda lazımdır, ona görə `PlanService` onu 30 saniyə keşləyir (dəyişəndə keş dərhal təmizlənir).

## Yoxlama (backend)

- **Bölmələr:** controller-də `@RequiresFeature(Feature.X)` — imkan yoxdursa sorğu controller-ə çatmır,
  403 `PLAN_FEATURE_UNAVAILABLE`.
- **Servislər:** möhür kampaniyasının yaradılması, sahibin analitikada filial seçməsi (`BRANCH_COMPARISON`).
- **Səssiz söndürmə:** paketdə push yoxdursa `GET /api/v1/public/push` `enabled = false` qaytarır və bildirişlər
  göndərilmir; dəvət yoxdursa proqram söndürülmüş sayılır (parametrlər saxlanılır); xatırlatmalar işləmir.
- **Limitlər:** yeni filial (və bağlı filialın yenidən açılması) aktiv filialların sayına görə; işçi dəvəti — sahib
  olmayan işçilər + gözləyən dəvətlər. Doludursa 409 `PLAN_LIMIT_REACHED`. Sahib dəvəti limitə düşmür.

Paket aşağı salınanda heç nə silinmir: limitdən artıq filial və işçilər işləməyə davam edir (yalnız yenisi əlavə
olunmur), Premium parametrləri saxlanılır və yuxarı paketə qayıdanda yenidən işləyir.

## Sınaq müddəti

- Platforma admini yeni şirkət yaradanda paketi seçir — şirkət onun **14 günlük pulsuz sınağı** ilə başlayır.
- Hər paketin sınağı şirkətə **bir dəfə** verilir (sui-istifadənin qarşısı). Məs. Basic-in sınağından sonra
  Premium-un sınağı verilə bilər, Basic-inki isə yox — `PLAN_TRIAL_ALREADY_USED` (409).
- Davam edən sınaqda eyni paket yenidən saxlananda müddət dəyişmir (məs. yalnız əlavə filiallar dəyişir).
- Ödəniş qeyd olunanda paket `ACTIVE` olur, sınaq bitir.

### Sınaq bitəndə

Ödəniş qeyd olunmayıbsa, şirkətin xidməti dayandırılır (`TrialExpiredFilter`):

- şirkətin domenində bütün `/api/...` sorğuları 402 `PLAN_TRIAL_EXPIRED` qaytarır: işçinin girişi, admin paneli,
  müştəri tətbiqi, möhür alma;
- açıq qalanlar — yalnız dayandırılma ekranı üçün: `/api/v1/context`, `/api/v1/csrf`, `/api/v1/public/plan`,
  `/api/v1/public/company` (ad, loqo), `/api/v1/public/icons/`;
- fon işləri (kampaniya göndərişi, xatırlatmalar) bu şirkəti keçir — növbədəki kampaniyalar ödənişdən sonra gedir;
- frontend (`PlanGate`) tətbiqin yerinə ekran göstərir: işçilərə "Pulsuz sınaq müddəti bitib", müştərilərə
  "Xidmət müvəqqəti dayandırılıb" (möhürlər və hədiyyələr saxlanılır).

Məlumat silinmir. Platforma ödənişi qeyd edən kimi (`ACTIVE`) xidmət bərpa olunur.

## API

| Endpoint | Kim | Nə edir |
|---|---|---|
| `GET /api/v1/public/plan` | hamı (şirkət domeni) | paket, vəziyyət, sınağın sonu, açıq imkanlar, limitlər |
| `GET /api/v1/platform/tenants/{id}/plan` | platforma admini | paket, əlavə filiallar, istifadə olunmuş sınaqlar |
| `PUT /api/v1/platform/tenants/{id}/plan` | platforma admini | `{plan, status: TRIAL/ACTIVE, extraBranches}`; audit `TENANT_PLAN_CHANGED` |
| `POST /api/v1/platform/tenants` | platforma admini | yeni şirkət; `plan` (standart `PREMIUM`) — sınaq dərhal başlayır |

## İnterfeys

- **Platforma paneli:** şirkətlər siyahısında "Paket" sütunu (paket, sınaq/aktiv/sınaq bitib, sınağın sonu);
  şirkətin səhifəsində "Paket" kartı — paket, vəziyyət (14 günlük sınaq / ödənişli), əlavə filiallar, istifadə
  olunmuş sınaqlar; yeni şirkət formasında paket seçimi.
- **Şirkətin paneli:** Parametrlərdə "Paketiniz" kartı (sınağın bitmə tarixi, neçə gün qalıb, limitlər); menyuda
  paketdə olmayan bölmələrin yanında kilid; belə bölmə açılanda "Bu imkan Premium paketindədir" izahı;
  Göstəricilər panelində qrafiklər, filial seçimi və dəvət statistikası paketə görə gizlədilir.
