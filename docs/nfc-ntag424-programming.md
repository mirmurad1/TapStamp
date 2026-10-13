# NTAG 424 DNA çipinin proqramlaşdırılması

Bu təlimat admin panelindən alınan **provisioning paketi** (çipi proqramlaşdırmaq üçün məlumat dəsti) ilə NXP NTAG 424 DNA çipinin necə hazırlanacağını izah edir. Nəticədə çip hər toxunuşda unikal və imzalı link yaradır (SUN — Secure Unique NFC). Bu link həm Android, həm iPhone-da brauzerdə açılır, tətbiq quraşdırmaq lazım deyil.

## Lazım olanlar

- NTAG 424 DNA çipi: stiker və ya kart.
- PC/SC NFC oxuyucusu, məsələn ACR1252U.
- **JDK 21** (Java) — repodakı proqramlaşdırma aləti [`tools/nfc/ProgramTag.java`](../tools/nfc/ProgramTag.java) bununla işləyir; başqa heç nə quraşdırmaq lazım deyil. (NXP-nin TagXplorer proqramı dayandırılıb.)
- **Platforma admini** hesabı (Platforma → şirkət → **NFC çipləri**). Şirkət sahibi çipləri yalnız görür. Paketi yükləmək üçün kimlik yenidən təsdiqlənir (step-up): 2FA aktivdirsə kodla, deyilsə şifrə ilə.

## 1. Çipi qeydiyyatdan keçirin

1. Admin panelində **NFC çipləri → Çip əlavə et** açın.
2. Çipin UID-ini daxil edin (7 bayt, 14 hex simvol). Oxumaq üçün çipi oxuyucuya qoyun və repo qovluğunda işlədin:
   ```
   java tools/nfc/ProgramTag.java --uid
   ```
3. Etiketi (məs. "Piştaxta 1") və filialı seçin.

Server 3 təsadüfi AES-128 açarı yaradır və onları şifrəli saxlayır. Açarların statusu **PENDING**-dir:

| Açar | Təyinatı |
|---|---|
| **K0** (AppMasterKey) | Çipin tətbiqinin əsas açarı |
| **K1** (SDMMetaReadKey) | UID və sayğacı şifrələyir (PICCData) |
| **K2** (SDMFileReadKey) | CMAC imzası üçün |

## 2. Provisioning paketini yükləyin

1. Çipin kartında **Provisioning paketi** düyməsini basın.
2. Kimliyinizi təsdiqləyin (kod və ya şifrə).
3. Açılan pəncərədə URL, açarlar və ofsetlər göstərilir. **JSON yüklə** ilə faylı saxlayın.

> **Diqqət:** Açarlar yalnız bir dəfə göstərilir. Paketin yüklənməsi audit jurnalına yazılır. Proqramlaşdırmadan sonra JSON faylını silin (açarı gələcəkdə yeniləmək istəyirsinizsə, şifrəli yerdə saxlayın — bax: "Açarın yenilənməsi").

Paketdəki sahələr:

| Sahə | Məna |
|---|---|
| `url` | Çipə yazılacaq link, məs. `https://gloriyajeans.mockcoffe.com/t/04A1B2C3D4E5F6?picc=000…&cmac=000…`. Sıfırlar çipin hər toxunuşda dolduracağı yerlərdir |
| `piccDataOffset` | NDEF faylında `picc=`-dən sonrakı 32 hex simvolun başlanğıcı (bayt) |
| `sdmMacInputOffset` = `sdmMacOffset` | `cmac=`-dən sonrakı 16 hex simvolun başlanğıcı. MAC boş girişdən hesablanır |

Ofsetlər NDEF faylının əvvəlindən sayılır:
- 2 bayt NLEN;
- 5 bayt URI qeydinin başlığı (`D1 01 <len> 55 04`, `04` = `https://`);
- `https://`-suz URL-də yerin indeksi.

## 3. Çipi proqramlaşdırın

Çipi oxuyucuya qoyun və işlədin:
```
java tools/nfc/ProgramTag.java paket.json
```

Alət ardıcıl olaraq:
1. çipin UID-ini paketdəki ilə müqayisə edir (başqa çipə yazmağın qarşısını alır);
2. tətbiqi (`D2760000850101`) seçir və zavod açarı (`00…00`) ilə autentifikasiya edir (AuthenticateEV2First);
3. NDEF faylına (fayl 02) paketdəki `url`-i URI qeydi kimi yazır;
4. SDM-i aktivləşdirir (ChangeFileSettings): UID və sayğac güzgüsü, PICCData **K1** ilə, CMAC **K2** ilə, ofsetlər paketdən; faylın oxunması hamıya açıq, yazılması və ayarların dəyişməsi yalnız **K0** ilə;
5. açarları dəyişir: K1, K2, sonda **K0**;
6. çipi açarsız (telefon kimi) oxuyur və linkdəki imzanı yeni açarlarla yoxlayır.

Sonda `HAZIRDIR` görünməlidir. Sonra çipə telefonla toxunun — link `picc` və `cmac` dolu halda açılmalıdır.

Xəta olarsa, alət səbəbi yazır və dayanır. Ən çox rast gəlinənlər:

| Mesaj | Səbəb |
|---|---|
| `NFC oxuyucu tapılmadı` | oxuyucu qoşulmayıb və ya drayveri yoxdur |
| `Bu çip paketdəki çip deyil` | oxuyucuda başqa çip var |
| `Çipin açarı uyğun gəlmir` | çip artıq proqramlaşdırılıb — rotasiya kimi `--old` ilə köhnə paketi verin |
| `SW=…` | çip əmri rədd etdi (məs. çip NTAG 424 DNA deyil) |

Kriptoqrafiyanın düzgünlüyünü oxuyucusuz yoxlamaq olar (NXP AN12196 və RFC 4493 rəsmi test vektorları):
```
java tools/nfc/ProgramTag.java --self-test
```

Windows konsolunda Azərbaycan hərfləri `?` kimi görünürsə, əvvəlcə `chcp 65001` işlədin.

## 4. Aktivləşdirmə

Proqramlaşdırılmış çipə ilk toxunuş açarları **PENDING**-dən **ACTIVE**-ə keçirir. Admin panelində çipin kartında "Aktiv: v1" görünür. Bundan sonra hər toxunuşda:
- sayğac artır;
- köhnə link təkrar işləmir (replay qorunması).

## Açarın yenilənməsi (rotasiya)

1. Çipin kartında **Açarı yenilə** düyməsini basın. Yeni PENDING dəst yaranır.
2. Yeni provisioning paketini yükləyin.
3. Çipi yeni və **köhnə** paketlə proqramlaşdırın (çipdəki cari açarlar köhnə paketdədir):
   ```
   java tools/nfc/ProgramTag.java yeni.json --old kohne.json
   ```
   NTAG 424 DNA-da K1 və K2-ni dəyişmək üçün onların köhnə dəyərləri lazımdır. Ona görə rotasiya planlaşdırırsınızsa, köhnə paketi silmək əvəzinə təhlükəsiz yerdə (şifrəli parol meneceri) saxlayın; köhnə paket yoxdursa, çipi rotasiya etmək mümkün deyil — onu deaktiv edib yeni çip qoyun.
4. Yeni açarla ilk toxunuşda keçid avtomatik olur: köhnə dəst RETIRED olur.

Keçidə qədər köhnə açar işləməyə davam edir — xidmət dayanmır.

İxrac olunmuş, amma hələ toxunulmamış PENDING dəst varkən yeni rotasiya bloklanır (`TAG_ROTATION_IN_PROGRESS`). Bu, çipdəki açarlarla serverdəkilərin uyğunsuzluğunun qarşısını alır.

## Deaktivasiya

Çip itəndə və ya oğurlananda **Deaktiv et** düyməsini basın. Bütün toxunuşlar dərhal `TAG_DISABLED` ilə rədd olunur. Çipin toxunuş jurnalı (**Jurnal**) rədd edilmiş cəhdləri səbəbləri ilə göstərir.

## Domen dəyişəndə

URL çipin içinə yazılıb. Şirkətin əsas domeni dəyişsə, çiplər yenidən proqramlaşdırılmalıdır. Bunun üçün açarı yeniləyin və yeni paketdəki URL-i yazın. Keçid dövründə köhnə domen də reyestrdə saxlanılır.
