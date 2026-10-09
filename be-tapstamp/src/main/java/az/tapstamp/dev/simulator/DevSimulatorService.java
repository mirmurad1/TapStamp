package az.tapstamp.dev.simulator;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import az.tapstamp.nfc.NfcTag;
import az.tapstamp.nfc.TagKeys;
import az.tapstamp.nfc.TagType;
import az.tapstamp.nfc.TapVerificationService;
import az.tapstamp.nfc.repository.NfcTagMapper;
import az.tapstamp.nfc.sun.Ntag424Sun;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * NFC simulyatoru (yalnız dev): çipin "oxunuşunu" təqlid edib imzalı toxunuş linki yaradır. {@code NTAG424_SUN}
 * çipləri real NTAG 424 kriptoqrafiyası ilə emulyasiya olunur (açarlar dev bazasından açılır), beləliklə
 * {@code Ntag424SunVerifier} də avadanlıqsız uçdan-uca yoxlanılır. Real çip kimi hər "oxunuş" sayğacı artırır —
 * link açılmasa belə.
 */
@Service
@Profile("dev")
public class DevSimulatorService {

    public record SimulatedTag(UUID id, String uid, String label, TagType type, boolean active, int lastCounter,
                               UUID branchId, LocalizedText branchName) {
    }

    public record SimulatedTap(String path, int counter) {
    }

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final NfcTagMapper tags;
    private final EnvelopeCipher cipher;
    private final DevSimulatorKey key;
    /** Simulyatorun verdiyi son sayğac (tenant:çip) — açılmamış linklər də sayğacı "istehlak edir". */
    private final Map<String, Integer> issued = new ConcurrentHashMap<>();

    public DevSimulatorService(@Qualifier("tenantJdbc") JdbcClient jdbc, NfcTagMapper tags,
                               @Qualifier("tenantCipher") EnvelopeCipher cipher, DevSimulatorKey key) {
        this.jdbc = jdbc;
        this.tags = tags;
        this.cipher = cipher;
        this.key = key;
    }

    public List<SimulatedTag> tags() {
        return jdbc.sql("""
                        SELECT t.id, t.uid, t.label, t.type, t.status, t.last_counter, t.branch_id, b.name::text AS branch
                        FROM nfc_tags t JOIN branches b ON b.id = t.branch_id ORDER BY b.created_at, t.label""")
                .query((rs, n) -> new SimulatedTag(rs.getObject("id", UUID.class), HEX.formatHex(rs.getBytes("uid")),
                        rs.getString("label"), TagType.valueOf(rs.getString("type")),
                        "ACTIVE".equals(rs.getString("status")), rs.getInt("last_counter"),
                        rs.getObject("branch_id", UUID.class), TEXT.fromJson(rs.getString("branch"))))
                .list();
    }

    public SimulatedTap tap(UUID tagId) {
        UUID tenantId = TenantContext.require().id();
        NfcTag tag = tags.findById(tagId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        int counter = issued.merge(tenantId + ":" + tagId, tag.lastCounter() + 1,
                (previous, fromDb) -> Math.max(previous + 1, fromDb));
        byte[] picc;
        byte[] cmac;
        if (tag.type() == TagType.SIMULATED) {
            picc = new byte[16];
            RANDOM.nextBytes(picc);
            picc[0] = (byte) counter;
            picc[1] = (byte) (counter >>> 8);
            picc[2] = (byte) (counter >>> 16);
            cmac = key.mac(tag.uid(), picc);
        } else {
            TagKeys keys = programmedKeys(tenantId, tagId);
            try {
                byte[] padding = new byte[5];
                RANDOM.nextBytes(padding);
                picc = Ntag424Sun.encryptPicc(keys.metaReadKey(), tag.uid(), counter, padding);
                cmac = Ntag424Sun.sdmMac(keys.fileReadKey(), tag.uid(), counter);
            } finally {
                keys.wipe();
            }
        }
        return new SimulatedTap("/t/" + HEX.formatHex(tag.uid()) + "?picc=" + HEX.formatHex(picc) + "&cmac="
                + HEX.formatHex(cmac), counter);
    }

    /** Çipə "yazılmış" açarlar: rotasiya gedirsə yeni (PENDING) dəst, əks halda ACTIVE. */
    private TagKeys programmedKeys(UUID tenantId, UUID tagId) {
        List<NfcTagMapper.StoredKeys> sets = tags.verificationKeys(tagId);
        NfcTagMapper.StoredKeys stored = sets.stream().filter(s -> s.status() == TagKeys.KeyStatus.PENDING).findFirst()
                .or(() -> sets.stream().findFirst())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new TagKeys(stored.id(), stored.version(), stored.status(),
                cipher.decrypt(stored.metaReadKeyEnc(),
                        TapVerificationService.keyAad(tenantId, "meta_read_key_enc", stored.id())),
                cipher.decrypt(stored.fileReadKeyEnc(),
                        TapVerificationService.keyAad(tenantId, "file_read_key_enc", stored.id())));
    }
}
