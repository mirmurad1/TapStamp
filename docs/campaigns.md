# Kampaniya SMS-ləri

Şirkət sahibi müştərilərinə SMS kampaniyası hazırlayır, platforma admini ödənişi aldıqdan sonra təsdiqləyir, sistem
göndərir. Qərar: `architecture-plan.md` §18 #49.

## Axın

1. **Sahib** — admin panel → **Kampaniyalar** → **Yeni kampaniya**:
   - mətn (1000 simvola qədər); əvvəlinə şirkətin "SMS-də görünən adı" avtomatik əlavə olunur: `Kafe: <mətn>`
     (göndərən adı bütün şirkətlər üçün `TrendStamp`-dır — müştəri SMS-in kimdən olduğunu adla bilir);
   - **"Müraciətlə başla"** (istəyə bağlı): hər SMS `Kafe: Hörmətli <ad>, <mətn>` olur, adı olmayan müştəriyə
     `Kafe: Hörmətli istifadəçi, <mətn>`. "Hörmətli" sözündə `ö` olduğu üçün mesaj UCS-2-yə keçir (70 simvol/SMS);
     SMS sayı hər müştərinin öz adı ilə hesablanır (önizləmədə "hər müştəriyə 1–2 SMS" kimi aralıq görünə bilər),
     cəmi kampaniya yaradılanda alıcıların adları ilə dəqiqləşir. Önizləmədə adı olan (auditoriyadan) və adı
     olmayan müştəri üçün nümunə göstərilir;
   - auditoriya: bütün müştərilər / uzun müddət gəlməyənlər (30, 60, 90 gün) / hədiyyəyə 1–2 möhür qalanlar /
     istifadə olunmamış hədiyyə kartı olanlar; istəyə bağlı filial (həmin filialda möhürü olanlar);
   - **önizləmə**: alıcı sayı, tam mətn, simvol sayı, hər müştəriyə neçə SMS, **cəmi SMS**. Mətndə Azərbaycan
     hərfləri varsa (bir SMS 160 yox, 70 simvol tutur), latın variantı və onun SMS sayı göstərilir; bir kliklə
     latın variantına keçmək olar.
2. **Təsdiqə göndər** — alıcılar və SMS sayı həmin an müəyyənləşir (`campaign_recipients`), status
   `PENDING_APPROVAL`. Sahib təsdiqə qədər ləğv edə bilər.
3. **Platforma admini** — platforma paneli → **Kampaniyalar**: şirkət, mətn, auditoriya, alıcı sayı və cəmi SMS.
   Ödəniş alındıqdan sonra **Təsdiqlə** (`APPROVED`) və ya səbəb yazaraq **Rədd et** (`REJECTED`, səbəb sahibə
   görünür).
4. **Göndəriş** — fon işi (`CampaignSender`, hər 30 saniyə): yalnız şirkətin saatı ilə **09:00–21:00**, hər
   kampaniya üçün addımda ən çox 100 SMS. `SENDING` → `SENT`; göndərilmiş və xətalı sayları kampaniyada görünür.
5. **Hesabat** — hər SMS platformanın SMS jurnalına `purpose = CAMPAIGN` ilə yazılır; SMS hesabatında giriş kodları
   və kampaniyalar ayrıca sütundadır.

## Müştəri

- Razılıq məxfilik siyasəti ilə alınır; müştəri tətbiqində ayrıca açar və söndürmə imkanı yoxdur (istifadəçi
  qərarı). Bazadakı `customers.marketing_opt_out_at` sütunu gələcək üçün saxlanılıb (hazırda həmişə boşdur).
- Bloklanmış, silinmiş müştəriyə SMS getmir; təsdiqlənmiş kampaniyada göndərişə qədər bloklanan müştəri `SKIPPED`
  olur.

## Təhlükəsizlik və etibarlılıq

- Alıcı göndərişdən əvvəl `SENDING` kimi götürülür (`FOR UPDATE SKIP LOCKED`): deploy zamanı iki backend eyni anda
  işləsə də, bir müştəriyə SMS iki dəfə getmir. Proses SMS-in ortasında kəsilsə, alıcı 10 dəqiqə sonra `FAILED`
  olur — təkrar göndərmək riskinə girmirik.
- Kampaniyanın yaradılması, ləğvi, təsdiqi və rəddi şirkətin audit jurnalına, təsdiq və rədd həm də platformanın
  audit jurnalına yazılır.

## Açıq məsələlər

- **SMS növü (Purpose).** Hazırda bütün SMS-lər Poçt Göyərçininə `INF` ilə gedir. Reklam üçün provayder `ADV`
  tələb edərsə, `PostaGuverciniSmsProvider`-də kampaniya SMS-ləri üçün ayrıca növ əlavə olunmalıdır.
