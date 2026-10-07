# Göstəricilərin tərifləri

İdarəetmə panelinin analitikası (`/api/v1/admin/analytics/*`) bu təriflərlə hesablanır. Kod: `AnalyticsService`.

## Ümumi qaydalar

- **Gün** şirkətin saat qurşağındadır (`company_profile.timezone`, standart `Asia/Baku`). Möhürün günü onun `local_date` sütunudur.
- Yalnız **təsdiqlənmiş** (`CONFIRMED`) möhürlər sayılır. Gözləyən, rədd edilən və ləğv olunan möhürlər sayılmır.
- **Dövr** `from` və `to` tarixləri ilə verilir, hər iki gün daxildir. Standart dövr son 30 gündür (bu gün daxil), ən uzun dövr 366 gündür.
- **Filial çərçivəsi** (`branchId`):
  - sahib filial seçmədikdə bütün şirkəti görür;
  - menecer yalnız öz filiallarını görür, başqa filialı istəsə 404 alır;
  - filial seçildikdə müştəri göstəriciləri yalnız həmin filialda ən azı bir möhürü olan müştəriləri əhatə edir.

## Göstəricilər

| Göstərici | Tərif |
|---|---|
| Müştərilər | Silinməmiş müştərilər; filial seçilibsə, yalnız o filialda möhürü olanlar |
| Aktiv müştəri (N gün) | Son təsdiqlənmiş möhürü son N gün ərzində (bu gün daxil) olan müştəri. N = 7, 30, 90 |
| Yeni müştərilər | Qeydiyyat tarixi (`registered_at`, yerli gün) dövrə düşən müştərilər. Ayrıca "bu həftə" (bazar ertəsindən) və "bu ay" |
| Möhürlər | Dövrdə təsdiqlənmiş möhürlərin sayı |
| Qazanılan hədiyyələr | `earned_at` günü dövrə düşən hədiyyə kartları. Filial — kartı yaradan möhürün filialı |
| Təhvil verilən hədiyyələr | `redeemed_at` günü dövrə düşən və `REDEEMED` statuslu kartlar. Filial — təhvil verildiyi filial |
| İstifadə faizi | Təhvil verilən ÷ qazanılan (eyni dövrdə). Qazanılan sıfırdırsa, göstərilmir |
| Geri qayıtma | Dövrdə qeydiyyatdan keçmiş və ən azı bir möhürü olan müştərilərdən ilk möhür günündən sonrakı 30 gün ərzində **başqa bir gündə** yenidən möhür alanların payı |
| İstilik xəritəsi | Möhürlərin həftənin günü (ISO: 1 — bazar ertəsi) və saat (0–23, yerli vaxt) üzrə sayı |
| Dil bölgüsü | Müştərilərin interfeys dili (az, en, ru); dövrdən asılı deyil |
