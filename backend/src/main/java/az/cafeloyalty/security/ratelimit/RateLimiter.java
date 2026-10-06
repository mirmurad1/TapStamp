package az.cafeloyalty.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import az.cafeloyalty.security.crypto.CryptoProperties;
import az.cafeloyalty.security.crypto.Hkdf;
import az.cafeloyalty.shared.error.ApiException;
import az.cafeloyalty.shared.error.ErrorCode;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Bucket4j əsaslı rate limiter (sorğu tezliyinin məhdudlaşdırılması). Vəziyyət platforma bazasında saxlanılır —
 * bir neçə backend instansiyası eyni limitləri paylaşır, əlavə servis (Redis) lazım olmur. Açarlarda şəxsi məlumat
 * (email, IP) açıq saxlanılmır: onlar HMAC ilə hash-lənir.
 *
 * <p>Limitlər {@code app.rate-limits.<siyasət>} xassələri ilə dəyişdirilə bilər, məs.
 * {@code app.rate-limits.admin-login-per-ip=50}.
 */
@Component
public class RateLimiter {

    private final ProxyManager<String> buckets;
    private final byte[] keyHmac;
    private final Map<String, Integer> capacityOverrides;

    public RateLimiter(@Qualifier("platformDataSource") DataSource platformDataSource, CryptoProperties crypto,
                       Environment environment) {
        this.buckets = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(platformDataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("rate_limit_buckets")
                .idColumn("id")
                .stateColumn("state")
                .expiresAtColumn("expires_at")
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
        this.keyHmac = Hkdf.derive(Base64.getDecoder().decode(crypto.phoneHmacKey()),
                "cafe-loyalty:rate-limit:v1".getBytes(StandardCharsets.UTF_8), new byte[0], 32);
        this.capacityOverrides = Binder.get(environment)
                .bind("app.rate-limits", Bindable.mapOf(String.class, Integer.class))
                .orElse(Map.of());
    }

    /**
     * Bir cəhd istehlak edir; limit aşılıbsa {@code RATE_LIMITED} istisnası atır.
     *
     * @param scope   məsələn tenant ID-si və ya "platform"
     * @param subject məsələn IP ünvanı və ya email (hash-lənir)
     */
    public void consume(RateLimitPolicy policy, String scope, String subject) {
        ConsumptionProbe probe = bucket(policy, scope, subject).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw rateLimited(probe.getNanosToWaitForRefill());
        }
    }

    /** Bir cəhd istehlak edir və limitin aşılıb-aşılmadığını qaytarır (istisna atmadan). */
    public boolean tryConsume(RateLimitPolicy policy, String scope, String subject) {
        return bucket(policy, scope, subject).tryConsume(1);
    }

    /** Limitin hazırda bitib-bitmədiyini yoxlayır (istehlak etmədən). */
    public boolean isExhausted(RateLimitPolicy policy, String scope, String subject) {
        return bucket(policy, scope, subject).getAvailableTokens() <= 0;
    }

    public static ApiException rateLimited(long nanosToWait) {
        long seconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(nanosToWait) + 1);
        return new ApiException(ErrorCode.RATE_LIMITED, new Object[0], Map.of("retryAfterSeconds", seconds));
    }

    private BucketProxy bucket(RateLimitPolicy policy, String scope, String subject) {
        int capacity = capacity(policy);
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, policy.window()))
                .build();
        String key = policy.name() + ":" + scope + ":" + hmac(subject == null ? "" : subject);
        return buckets.builder().build(key, () -> configuration);
    }

    private int capacity(RateLimitPolicy policy) {
        String property = policy.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return capacityOverrides.getOrDefault(property, policy.defaultCapacity());
    }

    private String hmac(String subject) {
        byte[] digest = Hkdf.hmac(keyHmac, subject.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest, 0, 16);
    }
}
