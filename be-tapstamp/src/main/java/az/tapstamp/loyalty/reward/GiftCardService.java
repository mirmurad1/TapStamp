package az.tapstamp.loyalty.reward;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.loyalty.repository.GiftCardMapper.GiftCardRow;
import az.tapstamp.loyalty.repository.GiftCardMapper;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.tenancy.TenantContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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

    /** {@code source}: CARD — möhür kartı doldu, TIER — {@code tierName} səviyyəsinə çatdı. */
    public record GiftCard(UUID id, String code, LocalizedText name, Status status, Instant earnedAt,
                           Instant expiresAt, Instant redeemedAt, String source, @Nullable LocalizedText tierName) {
        @Override
        public @NonNull String toString() {
            return "GiftCard[id=" + id + ", status=" + status + "]";
        }
    }

    private final GiftCardMapper giftCards;
    private final GiftCardCodes codes;

    public GiftCardService(GiftCardMapper giftCards, GiftCardCodes codes) {
        this.giftCards = giftCards;
        this.codes = codes;
    }

    public Optional<ActiveRule> activeRule() {
        return giftCards.findActiveRule();
    }

    /**
     * Müştərinin kartının qaydası: hədd dəyişəndə yarımçıq qalan kart köhnə qaydaya bağlanır
     * ({@code loyalty_accounts.reward_rule_id}), əks halda aktiv qayda.
     */
    public Optional<ActiveRule> ruleFor(UUID customerId) {
        return giftCards.findRuleFor(customerId);
    }

    /** Yeni kart (çağıranın tranzaksiyasında). Kod toqquşması (40 bit) praktiki olaraq mümkün deyil, amma yoxlanılır. */
    public UUID issue(UUID customerId, ActiveRule rule, Instant earnedAt) {
        return insert(customerId, rule, earnedAt, "CARD", null);
    }

    /** Səviyyəyə çatdığı üçün hədiyyə: cari qaydanın hədiyyəsi, kartda səbəb kimi səviyyənin adı. */
    public UUID issueForTier(UUID customerId, ActiveRule rule, LocalizedText tierName, Instant earnedAt) {
        return insert(customerId, rule, earnedAt, "TIER", tierName);
    }

    private UUID insert(UUID customerId, ActiveRule rule, Instant earnedAt, String source,
                        @Nullable LocalizedText tierName) {
        UUID id = Ids.newId();
        CodeSlot code = freeCode(id);
        giftCards.insert(id, customerId, rule.id(), rule.stampsRequired(), rule.name(), earnedAt,
                rule.validityDays() == null ? null : earnedAt.plus(Duration.ofDays(rule.validityDays())),
                code.nonce(), code.lookup(), source, tierName);
        return id;
    }

    /** Müştərinin bütün kartları: əvvəlcə istifadə oluna bilənlər, sonra tarixçə (ən yenisi birinci). */
    public List<GiftCard> list(UUID customerId, Instant now) {
        assignMissingCodes(customerId);
        UUID tenantId = TenantContext.require().id();
        return giftCards.listForCustomer(customerId).stream()
                .map(row -> toGiftCard(tenantId, row, now))
                .toList();
    }

    private GiftCard toGiftCard(UUID tenantId, GiftCardRow row, Instant now) {
        Status status = Status.valueOf(row.status());
        // Müddəti keçmiş kart fon işi onu yeniləməmişdən əvvəl də EXPIRED göstərilir
        if (status == Status.AVAILABLE && row.expiresAt() != null && !row.expiresAt().isAfter(now)) {
            status = Status.EXPIRED;
        }
        return new GiftCard(row.id(), codes.code(tenantId, row.id(), row.codeNonce()), row.name(), status,
                row.earnedAt(), row.expiresAt(), row.redeemedAt(), row.source(), row.tierName());
    }

    /** Kodsuz köhnə kartlar (məs. dev seed) ilk göstərişdə kod alır. */
    private void assignMissingCodes(UUID customerId) {
        for (UUID id : giftCards.findWithoutCode(customerId)) {
            CodeSlot code = freeCode(id);
            giftCards.assignCode(id, code.nonce(), code.lookup());
        }
    }

    private record CodeSlot(int nonce, byte[] lookup) {
    }

    /** Kartın kodu: toqquşma olarsa növbəti {@code nonce} ilə yeni kod. */
    private CodeSlot freeCode(UUID rewardId) {
        UUID tenantId = TenantContext.require().id();
        int nonce = 0;
        byte[] lookup = codes.lookup(tenantId, codes.code(tenantId, rewardId, nonce));
        while (giftCards.codeTaken(lookup)) {
            nonce++;
            lookup = codes.lookup(tenantId, codes.code(tenantId, rewardId, nonce));
        }
        return new CodeSlot(nonce, lookup);
    }
}
