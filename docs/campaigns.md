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
     cəmi kampaniya yaradılanda alıcıların adları ilə dəqiqləşir. Önizləmədə bir mətn göstərilir:
     `Kafe: Hörmətli <müştərinin adı>, <mətn>`;
   - **Latın olmayan simvollar** — bildiriş yalnız sahibin öz mətnində ASCII olmayan simvol olanda çıxır ("Hörmətli"
     müraciəti nəzərə alınmır): "bir SMS-ə 160 yox, 70 simvol sığır". Mətn latına çevrilə bilirsə (Azərbaycan
     hərfləri), latın variantı, onun SMS sayı və **"Latın hərfləri ilə göndər"** düyməsi də göstərilir; çevrilə
     bilmirsə (məs. kiril), yalnız bildiriş. Seçiləndə bütün mesaj — müraciət (`Hormetli`), müştərinin adı və mətn —
     göndəriləndə latına çevrilir (160 simvol/SMS); "Yazıldığı kimi göndər" ilə geri qaytarılır;
   - auditoriya: bütün müştərilər / uzun müddət gəlməyənlər (30, 60, 90 gün) / hədiyyəyə 1–2 möhür qalanlar /
     istifadə olunmamış hədiyyə kartı olanlar; istəyə bağlı filial (həmin filialda möhürü olanlar);
   - **önizləmə**: alıcı sayı, tam mətn, simvol sayı, hər müştəriyə neçə SMS, **cəmi SMS**.
2. **Təsdiqə göndər** — alıcılar və SMS sayı həmin an müəyyənləşir (`campaign_recipients`), status
   `PENDING_APPROVAL`. Təsdiqə qədər sahib kampaniyanı **dəyişə** (mətn, seçimlər, auditoriya — alıcılar və SMS sayı
   yenidən hesablanır; tarixçədə "Dəyişdirilib: <vaxt>") və ya **silə** bilər (tarixçədə "Ləğv edilib" kimi qalır).
   Təsdiqdən sonra heç biri mümkün deyil (`CAMPAIGN_STATE_CHANGED`).
3. **Platforma admini** — platforma paneli → **Kampaniyalar**: şirkət, mətn, auditoriya, alıcı sayı və cəmi SMS.
   Ödəniş alındıqdan sonra **Təsdiqlə** (`APPROVED`) və ya səbəb yazaraq **Ləğv et** (`REJECTED`, səbəb sahibə
   görünür). Platforma kampaniyanı dəyişə bilməz.
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

## SMS kodlaşdırması

Mətndə yalnız ASCII simvollar (latın hərfləri, rəqəmlər, işarələr) varsa, SMS provayderə `LATIN` ilə gedir (160
simvol/SMS). Hər hansı başqa simvol (ə, ş, ğ, ı, ç, ö, ü və s.) olsa, `UNICODE` ilə gedir (70 simvol/SMS) — `LATIN`
ilə göndərilsəydi, provayder bu hərfləri öz tərəfində latına çevirərdi. SMS sayı da bu qayda ilə hesablanır
(GSM-7 cədvəlindəki ü, ö, Ç kimi hərflər də UCS-2 sayılır). **Provayderdə hesab üçün UNICODE icazəsi açılmalıdır**;
açılmayıbsa, belə SMS-lər "Göndərilə bilməyən SMS-lər" siyahısına düşür.

## Açıq məsələlər

- **SMS növü (Purpose).** Hazırda bütün SMS-lər Poçt Göyərçininə `INF` ilə gedir. Reklam üçün provayder `ADV`
  tələb edərsə, `PostaGuverciniSmsProvider`-də kampaniya SMS-ləri üçün ayrıca növ əlavə olunmalıdır.
