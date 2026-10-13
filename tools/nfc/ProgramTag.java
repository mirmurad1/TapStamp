import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * NTAG 424 DNA çipini admin panelindən yüklənən provisioning paketi (JSON) ilə proqramlaşdırır. İstənilən PC/SC NFC
 * oxuyucusu ilə işləyir (məs. ACR1252U); JDK 21-dən başqa heç nə tələb etmir.
 *
 * <pre>
 *   java ProgramTag.java --uid                      # çipin UID-ini oxuyur (paneldə qeydiyyat üçün)
 *   java ProgramTag.java paket.json                 # yeni (zavod) çip: açarlar sıfırdır
 *   java ProgramTag.java yeni.json --old kohne.json # açarın yenilənməsi (rotasiya): çipdə köhnə paketin açarları var
 *   java ProgramTag.java --self-test                # kriptoqrafiyanı NXP-nin rəsmi test vektorları ilə yoxlayır
 * </pre>
 *
 * Addımlar: UID yoxlanır (paketdəki ilə eyni olmalıdır) → K0 ilə autentifikasiya (EV2) → NDEF faylına link yazılır →
 * SDM (imzalı link) aktivləşdirilir → K1, K2, sonda K0 dəyişdirilir → çip oxunur və imza yerli olaraq yoxlanılır.
 * Ətraflı: docs/nfc-ntag424-programming.md. Açarlar ekrana çıxarılmır.
 */
public class ProgramTag {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final byte[] ZERO_KEY = new byte[16];
    private static final byte[] APP_DF_NAME = HEX.parseHex("D2760000850101");
    private static final int NDEF_FILE = 0x02;

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (ToolException e) {
            System.err.println("XƏTA: " + e.getMessage());
            System.exit(1);
        } catch (CardException e) {
            System.err.println("XƏTA: oxuyucu ilə əlaqə alınmadı (" + e.getMessage() + "). Oxuyucunu və çipi yoxlayın.");
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            selfTest();
            return;
        }
        if (args.length == 1 && args[0].equals("--uid")) {
            CardTerminal terminal = terminal();
            System.out.println("Çipi oxuyucuya qoyun…");
            if (!terminal.waitForCardPresent(60_000)) {
                fail("60 saniyə ərzində çip tapılmadı");
            }
            Card card = terminal.connect("*");
            try {
                System.out.println("UID: " + HEX.formatHex(check(card.getBasicChannel()
                        .transmit(new CommandAPDU(0xFF, 0xCA, 0x00, 0x00, 256)), 0x9000)));
            } finally {
                card.disconnect(false);
            }
            return;
        }
        if (args.length != 1 && !(args.length == 3 && args[1].equals("--old"))) {
            System.err.println("İstifadə: java ProgramTag.java --uid | paket.json [--old kohne-paket.json] | --self-test");
            System.exit(2);
        }
        Map<String, String> pkg = readJson(Path.of(args[0]));
        Map<String, String> old = args.length == 3 ? readJson(Path.of(args[2])) : null;
        String uid = pkg.get("uid").toUpperCase(Locale.ROOT);
        if (old != null && !old.get("uid").equalsIgnoreCase(uid)) {
            fail("Köhnə və yeni paket fərqli çiplər üçündür (UID uyğun deyil)");
        }

        CardTerminal terminal = terminal();
        System.out.println("Oxuyucu: " + terminal.getName());
        System.out.println("Çipi oxuyucuya qoyun…");
        if (!terminal.waitForCardPresent(60_000)) {
            fail("60 saniyə ərzində çip tapılmadı");
        }
        Card card = terminal.connect("*");
        try {
            program(card.getBasicChannel(), pkg, old, uid);
        } finally {
            card.disconnect(false);
        }
    }

    private static void program(CardChannel channel, Map<String, String> pkg, Map<String, String> old, String uid)
            throws Exception {
        String cardUid = HEX.formatHex(check(channel.transmit(new CommandAPDU(0xFF, 0xCA, 0x00, 0x00, 256)), 0x9000));
        if (!cardUid.equals(uid)) {
            fail("Bu çip paketdəki çip deyil: oxunan UID " + cardUid + ", paketdə " + uid);
        }
        System.out.println("UID uyğundur: " + uid);

        byte[] k0 = HEX.parseHex(pkg.get("appMasterKey"));
        byte[] k1 = HEX.parseHex(pkg.get("sdmMetaReadKey"));
        byte[] k2 = HEX.parseHex(pkg.get("sdmFileReadKey"));
        byte[] oldK0 = old == null ? ZERO_KEY : HEX.parseHex(old.get("appMasterKey"));
        byte[] oldK1 = old == null ? ZERO_KEY : HEX.parseHex(old.get("sdmMetaReadKey"));
        byte[] oldK2 = old == null ? ZERO_KEY : HEX.parseHex(old.get("sdmFileReadKey"));
        int version = Integer.parseInt(pkg.get("keyVersion")) & 0xFF;
        String url = pkg.get("url");
        int piccOffset = Integer.parseInt(pkg.get("piccDataOffset"));
        int macInputOffset = Integer.parseInt(pkg.get("sdmMacInputOffset"));
        int macOffset = Integer.parseInt(pkg.get("sdmMacOffset"));

        selectApplication(channel);
        Session session = Session.authenticate(channel, 0, oldK0);
        System.out.println("Autentifikasiya: OK");

        byte[] ndef = ndefFile(url);
        session.plain(0x8D, concat(new byte[] {NDEF_FILE}, le3(0), le3(ndef.length), ndef));
        System.out.println("Link yazıldı (" + ndef.length + " bayt)");

        session.full(0x5F, new byte[] {NDEF_FILE}, fileSettings(piccOffset, macInputOffset, macOffset), true);
        System.out.println("SDM (imzalı link) aktivləşdirildi");

        session.changeKey(1, oldK1, k1, version);
        session.changeKey(2, oldK2, k2, version);
        session.changeKey(0, oldK0, k0, version);
        System.out.println("Açarlar dəyişdirildi (K1, K2, K0), versiya " + version);

        verify(channel, uid, k1, k2, piccOffset, macOffset);
        System.out.println();
        System.out.println("HAZIRDIR. Telefonla toxunun: ilk toxunuş açarları ACTIVE edir. Paket faylını indi silin.");
    }

    /** Çipi açarsız oxuyur (müştərinin telefonu kimi) və linkdəki imzanı yeni açarlarla yoxlayır. */
    private static void verify(CardChannel channel, String uid, byte[] k1, byte[] k2, int piccOffset, int macOffset)
            throws Exception {
        selectApplication(channel);
        check(channel.transmit(new CommandAPDU(0x00, 0xA4, 0x00, 0x0C, HEX.parseHex("E104"))), 0x9000);
        byte[] file = check(channel.transmit(new CommandAPDU(0x00, 0xB0, 0x00, 0x00, 256)), 0x9000);
        int nlen = ((file[0] & 0xFF) << 8) | (file[1] & 0xFF);
        String picc = new String(file, piccOffset, 32, StandardCharsets.US_ASCII);
        String mac = new String(file, macOffset, 16, StandardCharsets.US_ASCII);
        System.out.println("Çipdən oxunan link: " + new String(file, 7, nlen - 5, StandardCharsets.US_ASCII)
                .replace(picc, "<picc>").replace(mac, "<cmac>"));
        byte[] plain = aesCbc(Cipher.DECRYPT_MODE, k1, new byte[16], HEX.parseHex(picc));
        if ((plain[0] & 0xFF) != 0xC7 || !HEX.formatHex(plain, 1, 8).equals(uid)) {
            fail("Yoxlama alınmadı: PICCData K1 ilə açılmır");
        }
        int counter = (plain[8] & 0xFF) | (plain[9] & 0xFF) << 8 | (plain[10] & 0xFF) << 16;
        if (!Arrays.equals(sdmMac(k2, HEX.parseHex(uid), counter), HEX.parseHex(mac))) {
            fail("Yoxlama alınmadı: CMAC K2 ilə uyğun gəlmir");
        }
        System.out.println("İmza yoxlandı: OK (sayğac " + counter + ")");
    }

    // ---------------------------------------------------------------- NDEF və fayl ayarları

    /** NDEF faylı: NLEN (2) + URI qeydi (D1 01 len 55 04 + "https://"-suz URL). Ofsetlər backend-dəki ilə eynidir. */
    static byte[] ndefFile(String url) {
        if (!url.startsWith("https://")) {
            fail("Paketdəki link https:// ilə başlamır");
        }
        byte[] uri = url.substring("https://".length()).getBytes(StandardCharsets.US_ASCII);
        int recordLength = 5 + uri.length;
        return concat(new byte[] {(byte) (recordLength >> 8), (byte) recordLength, (byte) 0xD1, 0x01,
                (byte) (uri.length + 1), 0x55, 0x04}, uri);
    }

    /**
     * ChangeFileSettings (fayl 02): SDM açıq, oxuma hamıya açıq, yazma və dəyişmə yalnız K0; UID və sayğac güzgüsü,
     * ASCII; PICCData K1 ilə, CMAC K2 ilə; sayğacın ayrıca oxunması bağlı.
     */
    static byte[] fileSettings(int piccOffset, int macInputOffset, int macOffset) {
        return concat(new byte[] {
                0x40,             // FileOption: SDM enabled, CommMode plain
                0x00, (byte) 0xE0, // AccessRights: RW=0, Change=0 | Read=E (hamı), Write=0
                (byte) 0xC1,      // SDMOptions: UID mirror, SDMReadCtr mirror, ASCII
                (byte) 0xFF, 0x12 // SDMAccessRights: RFU=F, CtrRet=F | MetaRead=1 (K1), FileRead=2 (K2)
        }, le3(piccOffset), le3(macInputOffset), le3(macOffset));
    }

    // ---------------------------------------------------------------- EV2 sessiyası

    /** AuthenticateEV2First sessiyası: şifrələnmiş (Full) və açıq (Plain) əmrlər, CmdCtr. */
    static final class Session {
        private final CardChannel channel;
        private final byte[] ti;
        private final byte[] encKey;
        private final byte[] macKey;
        private int counter;

        Session(CardChannel channel, byte[] ti, byte[] encKey, byte[] macKey) {
            this.channel = channel;
            this.ti = ti;
            this.encKey = encKey;
            this.macKey = macKey;
        }

        static Session authenticate(CardChannel channel, int keyNo, byte[] key) throws Exception {
            ResponseAPDU first = channel.transmit(new CommandAPDU(0x90, 0x71, 0x00, 0x00, new byte[] {(byte) keyNo, 0x00}, 256));
            byte[] rndB = aesCbc(Cipher.DECRYPT_MODE, key, new byte[16], check(first, 0x91AF));
            byte[] rndA = new byte[16];
            new SecureRandom().nextBytes(rndA);
            byte[] answer = aesCbc(Cipher.ENCRYPT_MODE, key, new byte[16], concat(rndA, rotateLeft(rndB)));
            ResponseAPDU second = channel.transmit(new CommandAPDU(0x90, 0xAF, 0x00, 0x00, answer, 256));
            if (second.getSW() == 0x91AE) {
                fail("Çipin açarı uyğun gəlmir. Çip artıq proqramlaşdırılıbsa, köhnə paketi --old ilə verin");
            }
            byte[] plain = aesCbc(Cipher.DECRYPT_MODE, key, new byte[16], check(second, 0x9100));
            if (!Arrays.equals(Arrays.copyOfRange(plain, 4, 20), rotateLeft(rndA))) {
                fail("Autentifikasiya alınmadı: çipin cavabı yanlışdır");
            }
            byte[][] keys = sessionKeys(key, rndA, rndB);
            return new Session(channel, Arrays.copyOfRange(plain, 0, 4), keys[0], keys[1]);
        }

        /** Açıq rejimli əmr (sessiya daxilində: yalnız sayğac artır). */
        void plain(int command, byte[] data) throws CardException {
            check(channel.transmit(new CommandAPDU(0x90, command, 0x00, 0x00, data, 256)), 0x9100);
            counter++;
        }

        /** Şifrələnmiş və MAC-lı (Full) əmr; cavabın MAC-ı yoxlanılır. */
        void full(int command, byte[] header, byte[] data, boolean responseMac) throws Exception {
            byte[] iv = aesEcb(encKey, concat(new byte[] {(byte) 0xA5, 0x5A}, ti, le2(counter), new byte[8]));
            byte[] encrypted = aesCbc(Cipher.ENCRYPT_MODE, encKey, iv, pad(data));
            byte[] mac = truncate(cmac(macKey, concat(new byte[] {(byte) command}, le2(counter), ti, header, encrypted)));
            byte[] response = check(channel.transmit(new CommandAPDU(0x90, command, 0x00, 0x00,
                    concat(header, encrypted, mac), 256)), 0x9100);
            counter++;
            if (responseMac) {
                byte[] expected = truncate(cmac(macKey, concat(new byte[] {0x00}, le2(counter), ti,
                        Arrays.copyOf(response, response.length - 8))));
                if (response.length < 8 || !Arrays.equals(expected, Arrays.copyOfRange(response, response.length - 8, response.length))) {
                    fail("Çipin cavabının MAC-ı yanlışdır");
                }
            }
        }

        /** ChangeKey: autentifikasiya açarı (0) üçün yeni açar + versiya; digərləri üçün XOR + versiya + CRC32NK. */
        void changeKey(int keyNo, byte[] oldKey, byte[] newKey, int version) throws Exception {
            byte[] data = keyNo == 0 ? concat(newKey, new byte[] {(byte) version})
                    : concat(xor(newKey, oldKey), new byte[] {(byte) version}, crc32nk(newKey));
            full(0xC4, new byte[] {(byte) keyNo}, data, keyNo != 0);
        }
    }

    /** SesAuthENCKey və SesAuthMACKey (NT4H2421, 9.1.7). */
    static byte[][] sessionKeys(byte[] key, byte[] rndA, byte[] rndB) throws GeneralSecurityException {
        byte[] context = concat(Arrays.copyOfRange(rndA, 0, 2), xor(Arrays.copyOfRange(rndA, 2, 8),
                Arrays.copyOfRange(rndB, 0, 6)), Arrays.copyOfRange(rndB, 6, 16), Arrays.copyOfRange(rndA, 8, 16));
        byte[] sv1 = concat(HEX.parseHex("A55A00010080"), context);
        byte[] sv2 = concat(HEX.parseHex("5AA500010080"), context);
        return new byte[][] {cmac(key, sv1), cmac(key, sv2)};
    }

    /** SUN: SDMMAC = CMAC(KSesSDMFileReadMAC, boş) — KSes = CMAC(K2, 3CC3 0001 0080 UID sayğac). */
    static byte[] sdmMac(byte[] k2, byte[] uid, int counter) throws GeneralSecurityException {
        byte[] sv2 = concat(HEX.parseHex("3CC300010080"), uid, le3(counter));
        return truncate(cmac(cmac(k2, sv2), new byte[0]));
    }

    // ---------------------------------------------------------------- kriptoqrafiya

    static byte[] aesEcb(byte[] key, byte[] block) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(block);
    }

    static byte[] aesCbc(int mode, byte[] key, byte[] iv, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    /** AES-CMAC (RFC 4493). */
    static byte[] cmac(byte[] key, byte[] message) throws GeneralSecurityException {
        byte[] k1 = shiftSubkey(aesEcb(key, new byte[16]));
        byte[] k2 = shiftSubkey(k1);
        int blocks = Math.max(1, (message.length + 15) / 16);
        boolean complete = message.length > 0 && message.length % 16 == 0;
        byte[] last = Arrays.copyOfRange(message, (blocks - 1) * 16, blocks * 16);
        if (complete) {
            last = xor(last, k1);
        } else {
            last[message.length - (blocks - 1) * 16] = (byte) 0x80;
            last = xor(last, k2);
        }
        byte[] state = new byte[16];
        for (int i = 0; i < blocks - 1; i++) {
            state = aesEcb(key, xor(state, Arrays.copyOfRange(message, i * 16, i * 16 + 16)));
        }
        return aesEcb(key, xor(state, last));
    }

    private static byte[] shiftSubkey(byte[] input) {
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            out[i] = (byte) ((input[i] << 1) | (i < 15 ? (input[i + 1] & 0xFF) >>> 7 : 0));
        }
        if ((input[0] & 0x80) != 0) {
            out[15] ^= (byte) 0x87;
        }
        return out;
    }

    /** MAC-ın 8 baytlıq qısa forması: tək indeksli baytlar (1, 3, …, 15). */
    static byte[] truncate(byte[] mac) {
        byte[] out = new byte[8];
        for (int i = 0; i < 8; i++) {
            out[i] = mac[2 * i + 1];
        }
        return out;
    }

    /** ISO/IEC 9797-1 üsul 2: 0x80 və sıfırlar (həmişə əlavə olunur). */
    static byte[] pad(byte[] data) {
        byte[] out = Arrays.copyOf(data, (data.length / 16 + 1) * 16);
        out[data.length] = (byte) 0x80;
        return out;
    }

    /** CRC32NK: JAMCRC (son XOR-suz CRC32), little-endian. */
    static byte[] crc32nk(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        long value = ~crc.getValue() & 0xFFFFFFFFL;
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)};
    }

    // ---------------------------------------------------------------- köməkçilər

    private static void selectApplication(CardChannel channel) throws CardException {
        check(channel.transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0x0C, APP_DF_NAME, 256)), 0x9000);
    }

    private static CardTerminal terminal() throws CardException {
        List<CardTerminal> terminals;
        try {
            terminals = TerminalFactory.getDefault().terminals().list();
        } catch (CardException e) {
            terminals = List.of(); // Windows: oxuyucu yoxdursa siyahı xəta ilə qayıdır
        }
        if (terminals.isEmpty()) {
            fail("NFC oxuyucu tapılmadı (USB-yə qoşulduğunu və drayverin quraşdırıldığını yoxlayın)");
        }
        // ACR1252U kimi oxuyucular iki "terminal" göstərir (PICC və SAM); NFC olanı seçirik
        return terminals.stream().filter(t -> t.getName().toUpperCase(Locale.ROOT).contains("PICC")).findFirst()
                .orElse(terminals.getFirst());
    }

    private static byte[] check(ResponseAPDU response, int expectedSw) {
        if (response.getSW() != expectedSw) {
            fail(String.format("Çip xəta qaytardı: SW=%04X (gözlənilən %04X)", response.getSW(), expectedSw));
        }
        return response.getData();
    }

    /** Sadə JSON oxuyucu: paket düz obyektdir (sətir və ədəd sahələri). */
    static Map<String, String> readJson(Path path) throws Exception {
        String json = Files.readString(path);
        Map<String, String> values = new HashMap<>();
        Matcher m = Pattern.compile("\"(\\w+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(-?\\d+))").matcher(json);
        while (m.find()) {
            values.put(m.group(1), m.group(2) != null
                    ? m.group(2).replace("\\/", "/").replace("\\\"", "\"").replace("\\\\", "\\") : m.group(3));
        }
        for (String key : List.of("uid", "keyVersion", "url", "appMasterKey", "sdmMetaReadKey", "sdmFileReadKey",
                "piccDataOffset", "sdmMacInputOffset", "sdmMacOffset")) {
            if (!values.containsKey(key)) {
                fail(path + ": provisioning paketi deyil (\"" + key + "\" sahəsi yoxdur)");
            }
        }
        return values;
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, position, part.length);
            position += part.length;
        }
        return out;
    }

    static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    private static byte[] rotateLeft(byte[] data) {
        return concat(Arrays.copyOfRange(data, 1, data.length), new byte[] {data[0]});
    }

    private static byte[] le2(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8)};
    }

    private static byte[] le3(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16)};
    }

    private static void fail(String message) {
        throw new ToolException(message);
    }

    static final class ToolException extends RuntimeException {
        ToolException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- özünü yoxlama

    /** Rəsmi test vektorları: RFC 4493 (CMAC) və NXP AN12196 (SUN, EV2 sessiya açarları). */
    private static void selfTest() throws Exception {
        expect("CMAC (RFC 4493, boş)", cmac(HEX.parseHex("2B7E151628AED2A6ABF7158809CF4F3C"), new byte[0]),
                "BB1D6929E95937287FA37D129B756746");
        expect("CMAC (RFC 4493, 16 bayt)", cmac(HEX.parseHex("2B7E151628AED2A6ABF7158809CF4F3C"),
                HEX.parseHex("6BC1BEE22E409F96E93D7E117393172A")), "070A16B46B4D4144F79BDD9DD04A287C");

        byte[] picc = aesCbc(Cipher.DECRYPT_MODE, ZERO_KEY, new byte[16], HEX.parseHex("EF963FF7828658A599F3041510671E88"));
        expect("SUN PICCData (AN12196)", Arrays.copyOfRange(picc, 0, 11), "C704DE5F1EACC0403D0000");
        expect("SUN SDMMAC (AN12196)", sdmMac(ZERO_KEY, HEX.parseHex("04DE5F1EACC040"), 0x3D), "94EED9EE65337086");

        byte[][] keys = sessionKeys(ZERO_KEY, HEX.parseHex("13C5DB8A5930439FC3DEF9A4C675360F"),
                HEX.parseHex("B9E2FC789B64BF237CCCAA20EC7E6E48"));
        expect("EV2 SesAuthENCKey (AN12196)", keys[0], "1309C877509E5A215007FF0ED19CA564");
        expect("EV2 SesAuthMACKey (AN12196)", keys[1], "4C6626F5E72EA694202139295C7A7FC7");

        byte[] ndef = ndefFile("https://kafe.trendsolutions.az/t/04DE5F1EACC040?picc=" + "0".repeat(32) + "&cmac=" + "0".repeat(16));
        // Backend-in ofset formulu (7 + "https://"-suz URL-də yer) NDEF faylındakı real yerlə üst-üstə düşməlidir
        int piccOffset = 7 + "kafe.trendsolutions.az/t/04DE5F1EACC040?picc=".length();
        int actual = new String(ndef, StandardCharsets.US_ASCII).indexOf("picc=") + "picc=".length();
        expect("NDEF: PICCData ofseti", le3(actual), HEX.formatHex(le3(piccOffset)));
        System.out.println("Bütün yoxlamalar keçdi.");
    }

    private static void expect(String name, byte[] actual, String expected) {
        String value = HEX.formatHex(actual);
        if (!value.equals(expected)) {
            System.out.println("UĞURSUZ  " + name + ": " + value + " ≠ " + expected);
            System.exit(1);
        }
        System.out.println("OK  " + name);
    }
}
