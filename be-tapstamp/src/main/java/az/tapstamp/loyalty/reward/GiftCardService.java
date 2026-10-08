package az.tapstamp.loyalty.reward;

import static az.tapstamp.shared.persistence.SqlTime.instant;
import static az.tapstamp.shared.persistence.SqlTime.ts;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import az.tapstamp.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Hədiyyə kartları ({@code rewards} cədvəli): möhür kartı dolanda yaranır, daimi unikal kodla müştərinin
 * "Hədiyyə kartları" bölməsinə düşür. Qayda dəyişsə də kart öz snapshot-ını (ad, möhür sayı) saxlayır.
 */
@Service
public class GiftCardService {

    /** Aktiv mükafat qaydası. {@code validityDays == null} — kart müddətsizdir. */
    public record ActiveRule(UUID id, int stampsRequired, LocalizedText name, LocalizedText description,
                             Integer validityDays) {
    }

    public enum Status {
        AVAILABLE,
        REDEEMED,
        REVOKED,
        EXPIRED
    }

    public record GiftCard(UUID id, String code, LocalizedText name, Status status, Instant earnedAt,
                           Instant expiresAt, Instant redeemedAt) {
        @Override
        public String toString() {
            return "GiftCard[id=" + id + ", status=" + status + "]";
        }
    }

    private static final LocalizedTextConverter TEXT = new LocalizedTextConverter();

    private final JdbcClient jdbc;
    private final GiftCardCodes codes;

    public GiftCardService(@Qualifier("tenantJdbc") JdbcClient jdbc, GiftCardCodes codes) {
        this.jdbc = jdbc;
        this.codes = codes;
    }

    public Optional<ActiveRule> activeRule() {
        return jdbc.sql("""
                        SELECT id, stamps_required, reward_name::text AS name, reward_description::text AS description,
                               validity_days
                        FROM reward_rules WHERE active""")
                .query((rs, n) -> new ActiveRule(rs.getObject("id", UUID.class), rs.getInt("stamps_required"),
                        TEXT.convertToEntityAttribute(rs.getString("name")),
                        TEXT.convertToEntityAttribute(rs.getString("description")),
                        rs.getObject("validity_days", Integer.class)))
                .optional();
    }

    /** Yeni kart (çağıranın tranzaksiyasında). Kod toqquşması (40 bit) praktiki olaraq mümkün deyil, amma yoxlanılır. */
    public UUID issue(UUID customerId, ActiveRule rule, Instant earnedAt) {
        UUID id = Ids.newId();
        UUID tenantId = TenantContext.require().id();
        int nonce = 0;
        byte[] lookup = lookupFor(tenantId, id, nonce);
        while (codeTaken(lookup)) {
            nonce++;
            lookup = lookupFor(tenantId, id, nonce);
        }
        jdbc.sql("""
                        INSERT INTO rewards (id, customer_id, rule_id, stamps_required_snapshot, reward_name_snapshot,
                                             status, earned_at, expires_at, code_nonce, code_lookup, created_at)
                        VALUES (:id, :customerId, :ruleId, :stamps, CAST(:name AS jsonb), 'AVAILABLE', :earnedAt,
                                :expiresAt, :nonce, :lookup, :earnedAt)""")
                .param("id", id)
                .param("customerId", customerId)
                .param("ruleId", rule.id())
                .param("stamps", rule.stampsRequired())
                .param("name", TEXT.convertToDatabaseColumn(rule.name()))
                .param("earnedAt", ts(earnedAt))
                .param("expiresAt", ts(rule.validityDays() == null ? null
                        : earnedAt.plus(Duration.ofDays(rule.validityDays()))))
                .param("nonce", nonce)
                .param("lookup", lookup)
                .update();
        return id;
    }

    /** Müştərinin bütün kartları: əvvəlcə istifadə oluna bilənlər, sonra tarixçə (ən yenisi birinci). */
    public List<GiftCard> list(UUID customerId, Instant now) {
        assignMissingCodes(customerId);
        UUID tenantId = TenantContext.require().id();
        return jdbc.sql("""
                        SELECT id, reward_name_snapshot::text AS name, status, earned_at, expires_at, redeemed_at,
                               code_nonce
                        FROM rewards WHERE customer_id = :customerId
                        ORDER BY CASE WHEN status = 'AVAILABLE' THEN 0 ELSE 1 END, earned_at DESC
                        LIMIT 100""")
                .param("customerId", customerId)
                .query((rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Status status = Status.valueOf(rs.getString("status"));
                    Instant expiresAt = instant(rs, "expires_at");
                    if (status == Status.AVAILABLE && expiresAt != null && !expiresAt.isAfter(now)) {
                        status = Status.EXPIRED;
                    }
                    return new GiftCard(id, codes.code(tenantId, id, rs.getInt("code_nonce")),
                            TEXT.convertToEntityAttribute(rs.getString("name")), status, instant(rs, "earned_at"),
                            expiresAt, instant(rs, "redeemed_at"));
                })
                .list();
    }

    /** Kodsuz köhnə kartlar (məs. dev seed) ilk göstərişdə kod alır. */
    private void assignMissingCodes(UUID customerId) {
        UUID tenantId = TenantContext.require().id();
        List<UUID> missing = jdbc.sql("SELECT id FROM rewards WHERE customer_id = :customerId AND code_lookup IS NULL")
                .param("customerId", customerId)
                .query(UUID.class)
                .list();
        for (UUID id : missing) {
            int nonce = 0;
            byte[] lookup = lookupFor(tenantId, id, nonce);
            while (codeTaken(lookup)) {
                nonce++;
                lookup = lookupFor(tenantId, id, nonce);
            }
            jdbc.sql("UPDATE rewards SET code_nonce = :nonce, code_lookup = :lookup WHERE id = :id AND code_lookup IS NULL")
                    .param("nonce", nonce)
                    .param("lookup", lookup)
                    .param("id", id)
                    .update();
        }
    }

    private byte[] lookupFor(UUID tenantId, UUID rewardId, int nonce) {
        return codes.lookup(tenantId, codes.code(tenantId, rewardId, nonce));
    }

    private boolean codeTaken(byte[] lookup) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM rewards WHERE code_lookup = :lookup)")
                .param("lookup", lookup)
                .query(Boolean.class)
                .single());
    }
}
