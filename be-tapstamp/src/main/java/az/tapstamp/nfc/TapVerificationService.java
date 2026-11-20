package az.tapstamp.nfc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import az.tapstamp.audit.AuditLogger;
import az.tapstamp.nfc.TapEventLog.Event;
import az.tapstamp.nfc.TapEventLog.Source;
import az.tapstamp.nfc.TapEventLog.Stage;
import az.tapstamp.nfc.repository.NfcTagMapper;
import az.tapstamp.nfc.repository.TapTokenMapper;
import az.tapstamp.security.crypto.Aad;
import az.tapstamp.security.crypto.EnvelopeCipher;
import az.tapstamp.security.ratelimit.RateLimitPolicy;
import az.tapstamp.security.ratelimit.RateLimiter;
import az.tapstamp.security.token.SecureTokens;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.web.ClientInfo;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Toxunuşun yoxlanılması (plan §6.3–6.6): format → IP limiti → çip → kriptoqrafiya (strategiya: {@link TagVerifier})
 * → çip limiti → bir tranzaksiyada replay qorunması, sayğacın yenilənməsi, jurnal və birdəfəlik {@code tap_token}.
 *
 * <p>Xarici cavabda dəqiq səbəb açıqlanmır: PICC, UID və CMAC xətaları eyni {@code TAP_SIGNATURE_INVALID} kodudur.
 * Dəqiq səbəb {@code tap_events}-ə yazılır.
 */
@Service
public class TapVerificationService {

    /** Tokenin ömrü: toxunuşdan sonra möhürü almaq (və ya girişə başlamaq) üçün. */
    public static final Duration TOKEN_TTL = Duration.ofSeconds(60);
    /** Sayğac bu qədər və daha çox addımla artıbsa — server görmədiyi oxunuşlar var (əvvəlcədən yığılmış URL ola bilər). */
    static final int COUNTER_GAP_THRESHOLD = 10;
    static final int COUNTER_GAP_MEDIUM = 100;

    private static final Logger log = LoggerFactory.getLogger(TapVerificationService.class);

    /** Uğurlu yoxlamanın nəticəsi: brauzerə veriləcək token. */
    public record Accepted(String token, UUID tagId, int counter) {
        @Override
        public @NonNull String toString() {
            return "Accepted[tagId=" + tagId + ", counter=" + counter + "]";
        }
    }

    private record Outcome(Accepted accepted, ErrorCode error) {
    }

    private record Verification(VerifiedTap tap, TagKeys.KeyStatus keyStatus, UUID keyId) {
    }

    private final NfcTagMapper tags;
    private final TapEventLog events;
    private final TapTokenMapper tokens;
    private final FraudFlags fraudFlags;
    private final List<TagVerifier> verifiers;
    private final EnvelopeCipher cipher;
    private final RateLimiter rateLimiter;
    private final AuditLogger audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TapVerificationService(NfcTagMapper tags, TapEventLog events, TapTokenMapper tokens, FraudFlags fraudFlags,
                                  List<TagVerifier> verifiers, @Qualifier("tenantCipher") EnvelopeCipher cipher,
                                  RateLimiter rateLimiter, AuditLogger audit,
                                  PlatformTransactionManager transactionManager, Clock clock) {
        this.tags = tags;
        this.events = events;
        this.tokens = tokens;
        this.fraudFlags = fraudFlags;
        this.verifiers = List.copyOf(verifiers);
        this.cipher = cipher;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public Accepted verify(String uidHex, String picc, String cmac, ClientInfo client, String browserId) {
        TapParams params = TapParams.parse(uidHex, picc, cmac).orElseThrow(() -> new ApiException(ErrorCode.TAP_MALFORMED));
        TenantInfo tenant = TenantContext.require();
        String scope = "t:" + tenant.id();
        rateLimiter.consume(RateLimitPolicy.TAP_PER_IP, scope, client.ip());

        Source source = new Source(client.ip(), BrowserIdCookie.bindingHash(browserId),
                BrowserIdCookie.userAgentHash(client.userAgent()));
        Optional<NfcTag> found = tags.findByUid(params.uid());
        if (found.isEmpty()) {
            events.record(Event.of(Stage.VERIFY, null, null, "TAG_UNKNOWN", source), clock.instant());
            throw new ApiException(ErrorCode.TAG_UNKNOWN);
        }
        NfcTag tag = found.get();
        if (!tag.active()) {
            events.record(Event.of(Stage.VERIFY, tag.branchId(), tag.id(), "TAG_DISABLED", source), clock.instant());
            throw new ApiException(ErrorCode.TAG_DISABLED);
        }

        Verification verification;
        try {
            verification = verifyCrypto(tenant, tag, params);
        } catch (TapVerificationException e) {
            events.record(Event.of(Stage.VERIFY, tag.branchId(), tag.id(), e.reason(), source), clock.instant());
            throw new ApiException(ErrorCode.TAP_SIGNATURE_INVALID);
        }

        try {
            rateLimiter.consume(RateLimitPolicy.TAP_PER_TAG, scope, tag.id().toString(),
                    tags.tapLimitPerMinute(tag.branchId()));
        } catch (ApiException e) {
            events.record(Event.of(Stage.VERIFY, tag.branchId(), tag.id(), "RATE_LIMITED", source)
                    .withCounter(verification.tap().counter(), null), clock.instant());
            throw e;
        }

        try {
            Outcome outcome = Objects.requireNonNull(tx.execute(status -> accept(tag.id(), verification, source)));
            if (outcome.error() != null) {
                throw new ApiException(outcome.error());
            }
            return outcome.accepted();
        } catch (DuplicateKeyException e) {
            // İkinci qat: UNIQUE (tag_id, counter) — məntiqdə səhv olsa belə eyni sayğac iki dəfə qəbul olunmur
            log.warn("Duplicate accepted counter for tag {}", tag.id());
            throw new ApiException(ErrorCode.TAP_REPLAYED);
        }
    }

    /** Rədd halında jurnal və fırıldaq işarəsi tranzaksiya ilə birlikdə commit olunur, xəta isə sonra atılır. */
    private Outcome accept(UUID tagId, Verification verification, Source source) {
        Instant now = clock.instant();
        NfcTag locked = tags.lock(tagId);
        int counter = verification.tap().counter();
        if (!locked.active()) {
            events.record(Event.of(Stage.VERIFY, locked.branchId(), tagId, "TAG_DISABLED", source)
                    .withCounter(counter, null), now);
            return new Outcome(null, ErrorCode.TAG_DISABLED);
        }
        if (counter <= locked.lastCounter()) {
            events.record(Event.of(Stage.VERIFY, locked.branchId(), tagId, "COUNTER_REPLAY", source)
                    .withCounter(counter, null), now);
            fraudFlags.raise(FraudFlags.Type.REPLAY_ATTEMPT, FraudFlags.Severity.HIGH, tagId, null,
                    Map.of("counter", counter, "lastCounter", locked.lastCounter()), now);
            return new Outcome(null, ErrorCode.TAP_REPLAYED);
        }
        Integer gap = locked.lastCounter() < 0 ? null : counter - locked.lastCounter() - 1;
        tags.acceptCounter(tagId, counter, now);
        // Filial bağlanıb: sayğac qəbul olunur (bu link sonra, filial açılanda təkrar işlədilə bilməsin), möhür verilmir
        if (!tags.branchActive(locked.branchId())) {
            events.record(Event.of(Stage.VERIFY, locked.branchId(), tagId, "BRANCH_CLOSED", source)
                    .withCounter(counter, gap), now);
            return new Outcome(null, ErrorCode.BRANCH_CLOSED);
        }
        UUID eventId = events.record(Event.of(Stage.VERIFY, locked.branchId(), tagId, null, source)
                .withCounter(counter, gap), now);
        if (gap != null && gap >= COUNTER_GAP_THRESHOLD) {
            fraudFlags.raise(FraudFlags.Type.COUNTER_GAP,
                    gap >= COUNTER_GAP_MEDIUM ? FraudFlags.Severity.MEDIUM : FraudFlags.Severity.LOW, tagId, null,
                    Map.of("gap", gap, "counter", counter), now);
        }
        if (verification.keyStatus() == TagKeys.KeyStatus.PENDING) {
            // Yeni açarla proqramlaşdırılmış çipin ilk toxunuşu: rotasiya tamamlanır
            tags.activateKeys(tagId, verification.keyId(), now);
            audit.tenant("NFC_TAG_KEYS_ACTIVATED", AuditLogger.ActorType.SYSTEM, null, "nfc_tag", tagId,
                    Map.of("keyId", verification.keyId().toString()));
        }
        String token = SecureTokens.generate();
        tokens.insert(Ids.newId(), SecureTokens.hash(token), locked.branchId(), tagId, eventId, source.browserIdHash(),
                now.plus(TOKEN_TTL), now);
        return new Outcome(new Accepted(token, tagId, counter), null);
    }

    private Verification verifyCrypto(TenantInfo tenant, NfcTag tag, TapParams params) {
        TagVerifier verifier = verifiers.stream().filter(candidate -> candidate.supports(tag.type())).findFirst()
                .orElseThrow(() -> new TapVerificationException("UNSUPPORTED_TAG_TYPE"));
        if (tag.type() == TagType.SIMULATED) {
            return new Verification(verifier.verify(tag, null, params), null, null);
        }
        List<NfcTagMapper.StoredKeys> keySets = tags.verificationKeys(tag.id());
        if (keySets.isEmpty()) {
            throw new TapVerificationException("KEYS_MISSING");
        }
        List<TapVerificationException> failures = new ArrayList<>();
        for (NfcTagMapper.StoredKeys stored : keySets) {
            TagKeys keys = decrypt(tenant, stored);
            try {
                return new Verification(verifier.verify(tag, keys, params), stored.status(), stored.id());
            } catch (TapVerificationException e) {
                failures.add(e);
            } finally {
                keys.wipe();
            }
        }
        // ACTIVE dəstin səbəbi jurnala yazılır (PENDING yalnız rotasiya zamanı yoxlanılır)
        throw failures.getFirst();
    }

    private TagKeys decrypt(TenantInfo tenant, NfcTagMapper.StoredKeys stored) {
        return new TagKeys(stored.id(), stored.version(), stored.status(),
                cipher.decrypt(stored.metaReadKeyEnc(), keyAad(tenant.id(), "meta_read_key_enc", stored.id())),
                cipher.decrypt(stored.fileReadKeyEnc(), keyAad(tenant.id(), "file_read_key_enc", stored.id())));
    }

    /** {@code nfc_tag_keys} sütunlarının AAD-ı (seed və gələcək açar idarəsi eyni formatı işlədir). */
    public static String keyAad(UUID tenantId, String column, UUID keyId) {
        return Aad.tenantField(tenantId, "nfc_tag_keys", column, keyId);
    }
}
