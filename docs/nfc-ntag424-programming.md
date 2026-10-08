# NTAG 424 DNA çipinin proqramlaşdırılması

Bu təlimat admin panelindən alınan **provisioning paketi** (çipi proqramlaşdırmaq üçün məlumat dəsti) ilə NXP NTAG 424 DNA çipinin necə hazırlanacağını izah edir. Nəticədə çip hər toxunuşda unikal və imzalı link yaradır (SUN — Secure Unique NFC). Bu link həm Android, həm iPhone-da brauzerdə açılır, tətbiq quraşdırmaq lazım deyil.

## Lazım olanlar

- NTAG 424 DNA çipi: stiker və ya kart.
- PC/SC NFC oxuyucusu, məsələn ACR1252U.
- NXP **TagXplorer** proqramı (NXP saytından pulsuz).
- **Platforma admini** hesabı (Platforma → şirkət → **NFC çipləri**). Şirkət sahibi çipləri yalnız görür. Paketi yükləmək üçün kimlik yenidən təsdiqlənir (step-up): 2FA aktivdirsə kodla, deyilsə şifrə ilə.

## 1. Çipi qeydiyyatdan keçirin

1. Admin panelində **NFC çipləri → Çip əlavə et** açın.
2. Çipin UID-ini daxil edin. Bu, 7 baytdır və TagXplorer-də görünür.
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

> **Diqqət:** Açarlar yalnız bir dəfə göstərilir. Paketin yüklənməsi audit jurnalına yazılır. Proqramlaşdırmadan sonra JSON faylını silin.

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

## 3. Çipi TagXplorer ilə proqramlaşdırın

1. Çipi oxuyucuya qoyun və **ISO 7816 / NTAG 424 DNA** bölməsində tətbiqi seçin (`D2760000850101`).
2. Standart açarla (`00…00`) autentifikasiya edin.
3. **NDEF faylına** (fayl 02) paketdəki `url`-i URI qeydi kimi yazın.
4. **Change File Settings** (fayl 02) bölməsində SDM-i aktivləşdirin:
   - *SDM enabled*, *UID mirroring*, *SDMReadCtr mirroring* — açıq;
   - *SDMMetaRead* açarı → **K1** (açar nömrəsi 1);
   - *SDMFileRead* açarı → **K2** (açar nömrəsi 2);
   - *PICCDataOffset* → `piccDataOffset`;
   - *SDMMACInputOffset* və *SDMMACOffset* → paketdəki dəyərlər;
   - faylın oxunması hamıya açıq (free read), yazılması yalnız K0 ilə.
5. **Change Key** ilə açarları dəyişin. Əvvəlcə K1 və K2, sonda **K0**.
6. Çipi telefonla oxuyun. Link `picc` və `cmac` dəyərləri dolu halda açılmalıdır.

## 4. Aktivləşdirmə

Proqramlaşdırılmış çipə ilk toxunuş açarları **PENDING**-dən **ACTIVE**-ə keçirir. Admin panelində çipin kartında "Aktiv: v1" görünür. Bundan sonra hər toxunuşda:
- sayğac artır;
- köhnə link təkrar işləmir (replay qorunması).

## Açarın yenilənməsi (rotasiya)

1. Çipin kartında **Açarı yenilə** düyməsini basın. Yeni PENDING dəst yaranır.
2. Yeni provisioning paketini yükləyin.
3. Çipi **cari K0** ilə autentifikasiya edib K1, K2 və K0-ı yeni dəyərlərlə dəyişin.
4. Yeni açarla ilk toxunuşda keçid avtomatik olur: köhnə dəst RETIRED olur.

Keçidə qədər köhnə açar işləməyə davam edir — xidmət dayanmır.

İxrac olunmuş, amma hələ toxunulmamış PENDING dəst varkən yeni rotasiya bloklanır (`TAG_ROTATION_IN_PROGRESS`). Bu, çipdəki açarlarla serverdəkilərin uyğunsuzluğunun qarşısını alır.

## Deaktivasiya

Çip itəndə və ya oğurlananda **Deaktiv et** düyməsini basın. Bütün toxunuşlar dərhal `TAG_DISABLED` ilə rədd olunur. Çipin toxunuş jurnalı (**Jurnal**) rədd edilmiş cəhdləri səbəbləri ilə göstərir.

## Domen dəyişəndə

URL çipin içinə yazılıb. Şirkətin əsas domeni dəyişsə, çiplər yenidən proqramlaşdırılmalıdır. Bunun üçün açarı yeniləyin və yeni paketdəki URL-i yazın. Keçid dövründə köhnə domen də reyestrdə saxlanılır.
