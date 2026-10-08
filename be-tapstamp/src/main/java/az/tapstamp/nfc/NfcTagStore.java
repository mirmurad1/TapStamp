package az.tapstamp.nfc;

import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code nfc_tags} və {@code nfc_tag_keys} cədvəlləri (cari tenant-ın bazası). */
@Component
public class NfcTagStore {

    /** Şifrəli açar dəsti (K0 burada oxunmur — yoxlama üçün lazım deyil). */
    public record StoredKeys(UUID id, int version, TagKeys.KeyStatus status, byte[] metaReadKeyEnc,
                             byte[] fileReadKeyEnc) {

        @Override
        public String toString() {
            return "StoredKeys[id=" + id + ", status=" + status + "]";
        }
    }

    private static final String COLUMNS = "id, branch_id, uid, type, label, status, last_counter";

    private final JdbcClient jdbc;

    public NfcTagStore(@Qualifier("tenantJdbc") JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NfcTag> findByUid(byte[] uid) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM nfc_tags WHERE uid = :uid")
                .param("uid", uid)
                .query(NfcTagStore::map)
                .optional();
    }

    public Optional<NfcTag> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM nfc_tags WHERE id = :id")
                .param("id", id)
                .query(NfcTagStore::map)
                .optional();
    }

    /** Sətir kilidi: eyni çipin paralel toxunuşları ardıcıl emal olunur (replay qorunması). */
    public NfcTag lock(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM nfc_tags WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(NfcTagStore::map)
                .single();
    }

    public void acceptCounter(UUID id, int counter, Instant now) {
        jdbc.sql("""
                        UPDATE nfc_tags SET last_counter = :counter, last_tap_at = :now, updated_at = :now,
                                            version = version + 1
                        WHERE id = :id""")
                .param("counter", counter)
                .param("now", ts(now))
                .param("id", id)
                .update();
    }

    /** Yoxlama üçün açar dəstləri: əvvəlcə ACTIVE, sonra (rotasiya gedirsə) PENDING. */
    public List<StoredKeys> verificationKeys(UUID tagId) {
        return jdbc.sql("""
                        SELECT id, key_version, status, meta_read_key_enc, file_read_key_enc FROM nfc_tag_keys
                        WHERE tag_id = :tagId AND status IN ('ACTIVE', 'PENDING')
                        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END""")
                .param("tagId", tagId)
                .query((rs, n) -> new StoredKeys(rs.getObject("id", UUID.class), rs.getInt("key_version"),
                        TagKeys.KeyStatus.valueOf(rs.getString("status")), rs.getBytes("meta_read_key_enc"),
                        rs.getBytes("file_read_key_enc")))
                .list();
    }

    /** Rotasiyanın tamamlanması: köhnə ACTIVE dəst RETIRED, PENDING dəst ACTIVE olur (eyni tranzaksiyada). */
    public void activateKeys(UUID tagId, UUID keyId, Instant now) {
        jdbc.sql("UPDATE nfc_tag_keys SET status = 'RETIRED', retired_at = :now WHERE tag_id = :tagId AND status = 'ACTIVE'")
                .param("now", ts(now))
                .param("tagId", tagId)
                .update();
        jdbc.sql("UPDATE nfc_tag_keys SET status = 'ACTIVE', activated_at = :now WHERE id = :id AND status = 'PENDING'")
                .param("now", ts(now))
                .param("id", keyId)
                .update();
    }

    /** Çip üzrə dəqiqədə toxunuş limiti: filialın sui-istifadə qaydası, yoxdursa şirkət standartı. */
    public int tapLimitPerMinute(UUID branchId) {
        return jdbc.sql("""
                        SELECT max_taps_per_tag_per_minute FROM stamp_policies
                        WHERE branch_id = :branchId OR branch_id IS NULL
                        ORDER BY branch_id NULLS LAST LIMIT 1""")
                .param("branchId", branchId)
                .query(Integer.class)
                .optional()
                .orElse(10);
    }

    static NfcTag map(ResultSet rs, int rowNum) throws SQLException {
        return new NfcTag(
                rs.getObject("id", UUID.class),
                rs.getObject("branch_id", UUID.class),
                rs.getBytes("uid"),
                TagType.valueOf(rs.getString("type")),
                rs.getString("label"),
                "ACTIVE".equals(rs.getString("status")),
                rs.getInt("last_counter"));
    }
}
