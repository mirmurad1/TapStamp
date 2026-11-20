package az.tapstamp.notification.push;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import az.tapstamp.notification.push.repository.PushSubscriptionMapper;
import az.tapstamp.notification.push.repository.PushSubscriptionMapper.Subscription;
import az.tapstamp.plan.Feature;
import az.tapstamp.plan.PlanService;
import az.tapstamp.shared.error.ApiException;
import az.tapstamp.shared.error.ErrorCode;
import az.tapstamp.shared.i18n.Language;
import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.id.Ids;
import az.tapstamp.shared.web.DeviceLabels;
import az.tapstamp.tenancy.TenantContext;
import az.tapstamp.tenancy.TenantInfo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Müştərilərə web push bildirişləri (qərar #54): abunələrin idarəsi və göndəriş. Bildiriş tranzaksiya
 * <b>commit olandan sonra</b> arxa planda göndərilir (möhür ləğv olunarsa bildiriş getmir, cavab gecikmir). Mətn
 * müştərinin dilində ({@code push.*} açarları). Push xidməti abunənin etibarsız olduğunu bildirsə, abunə silinir.
 */
@Service
public class PushService {

    /** Bildiriş növləri; mətn {@code push.<növ>.title/body}, klikdə açılan səhifə — {@code url}. */
    public enum Kind {
        STAMP_CONFIRMED("/"),
        GIFT_READY("/gift-cards"),
        REFERRAL_BONUS("/"),
        /** Avtomatik xatırlatmalar (qərar #55); arqumentlər: kartdakı möhür, hədd. */
        REMINDER_NEAR_REWARD("/"),
        REMINDER_WINBACK("/"),
        REMINDER_WINBACK_EMPTY("/"),
        REMINDER_UNUSED_GIFT("/gift-cards"),
        /** Bonus qaydası başladı (qərar #59); arqumentlər: qaydanın adı ({@link LocalizedText}), bonus möhür. */
        BONUS_RULE_STARTED("/"),
        /** Səviyyəyə çatdı (çatma imtiyazı ilə); arqument: səviyyənin adı ({@link LocalizedText}). */
        TIER_REACHED("/gift-cards"),
        /** Ziyarətdən 15 dəqiqə sonra rəy sorğusu (qərar #61). */
        FEEDBACK_REQUEST("/?feedback=1"),
        /** Yeni endirimlər (bir bildirişdə); arqumentlər: say, siyahı ({@link LocalizedText}). Ünvan elanla verilir. */
        DEALS_NEW("/menu"),
        /** Endirimin son günü; arqumentlər: say, siyahı ({@link LocalizedText}). */
        DEALS_LAST_DAY("/menu");

        private final String url;

        Kind(String url) {
            this.url = url;
        }
    }

    public record Config(boolean enabled, @Nullable String publicKey) {
    }

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final int MAX_DEVICES = 10;
    /** Cihaz oflayndırsa push serveri mesajı 1 gün saxlayır. */
    private static final int TTL_SECONDS = 24 * 60 * 60;
    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final PushSubscriptionMapper subscriptions;
    private final WebPushSender sender;
    private final MessageSource messages;
    private final TaskExecutor executor;
    private final JsonMapper json;
    private final Clock clock;
    private final PlanService plans;

    public PushService(PushSubscriptionMapper subscriptions, WebPushSender sender,
                       @Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME) MessageSource messages,
                       @Qualifier("applicationTaskExecutor") TaskExecutor executor, JsonMapper json, Clock clock,
                       PlanService plans) {
        this.subscriptions = subscriptions;
        this.sender = sender;
        this.messages = messages;
        this.executor = executor;
        this.json = json;
        this.clock = clock;
        this.plans = plans;
    }

    public Config config() {
        return new Config(available(), sender.publicKey());
    }

    /** Serverdə push qurulub və şirkətin paketində var. */
    private boolean available() {
        return sender.enabled() && plans.has(Feature.PUSH);
    }

    /** Brauzerin abunəsi: ünvan icazəli push serverində olmalı, açarlar düzgün ölçüdə. */
    public void subscribe(UUID customerId, String endpoint, String p256dh, String auth, @Nullable String userAgent) {
        if (!available()) {
            throw new ApiException(ErrorCode.PUSH_UNAVAILABLE);
        }
        if (!WebPushSender.allowedEndpoint(endpoint) || decodedLength(p256dh) != 65 || decodedLength(auth) != 16) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        subscriptions.upsert(Ids.newId(), customerId, endpoint, p256dh, auth,
                userAgent == null ? null : DeviceLabels.describe(userAgent), clock.instant());
        subscriptions.trim(customerId, MAX_DEVICES);
    }

    public void unsubscribe(UUID customerId, String endpoint) {
        subscriptions.delete(customerId, endpoint);
    }

    /** Hesab silinəndə və ya bloklananda — bütün cihazlardakı abunələr. */
    public void removeAll(UUID customerId) {
        subscriptions.deleteAll(customerId);
    }

    public long customersWithPush() {
        return subscriptions.countCustomers();
    }

    /**
     * Bildirişi növbəyə qoyur: cari tranzaksiya varsa commit-dən sonra, yoxdursa dərhal (arxa planda). Push
     * söndürülübsə heç nə etmir.
     */
    public void notify(UUID customerId, Kind kind, Object... args) {
        enqueue(customerId, kind, args, null);
    }

    /**
     * {@link #notify} kimi, üstəlik nəticə bildirilir (arxa plan axınında, şirkətin kontekstində): {@code true} — ən azı
     * bir cihaza push xidməti qəbul etdi; {@code false} — cihaz yoxdur və ya hamısı alınmadı. Push söndürülübsə —
     * {@code false} dərhal.
     */
    public void notifyTracked(UUID customerId, Kind kind, Consumer<Boolean> onResult, Object... args) {
        notifyTracked(customerId, kind, kind.url, onResult, args);
    }

    /** {@link #notifyTracked} kimi, klikdə açılan ünvan verilir (məs. açılışı saymaq üçün elanın id-si ilə). */
    public void notifyTracked(UUID customerId, Kind kind, String url, Consumer<Boolean> onResult, Object... args) {
        if (!available()) {
            onResult.accept(false);
            return;
        }
        enqueue(customerId, kind, url, args, onResult);
    }

    private void enqueue(UUID customerId, Kind kind, Object[] args, @Nullable Consumer<Boolean> onResult) {
        enqueue(customerId, kind, kind.url, args, onResult);
    }

    private void enqueue(UUID customerId, Kind kind, String url, Object[] args, @Nullable Consumer<Boolean> onResult) {
        if (!available()) {
            return;
        }
        TenantInfo tenant = TenantContext.require();
        Runnable task = () -> executor.execute(() -> TenantContext.runAs(tenant, () -> {
            boolean delivered = deliver(customerId, kind, url, args);
            if (onResult != null) {
                onResult.accept(delivered);
            }
        }));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** Ən azı bir cihaza çatdırılıbsa (push xidməti qəbul edib) — {@code true}. */
    private boolean deliver(UUID customerId, Kind kind, String url, Object[] args) {
        try {
            List<Subscription> devices = subscriptions.forCustomer(customerId);
            if (devices.isEmpty()) {
                return false;
            }
            Language language = subscriptions.activeCustomerLanguage(customerId).flatMap(Language::fromTag).orElse(null);
            if (language == null) {
                return false;
            }
            boolean delivered = false;
            Object[] localized = localize(args, language);
            String key = "push." + kind.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", messages.getMessage(key + ".title", localized, language.locale()));
            payload.put("body", messages.getMessage(key + ".body", localized, language.locale()));
            payload.put("url", url);
            payload.put("tag", kind.name());
            byte[] body = json.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            for (Subscription device : devices) {
                WebPushSender.Result result = sender.send(device.endpoint(), B64.decode(device.p256dh()),
                        B64.decode(device.auth()), body, TTL_SECONDS);
                switch (result) {
                    case SENT -> {
                        subscriptions.markSent(device.id(), clock.instant());
                        delivered = true;
                    }
                    case GONE -> subscriptions.deleteById(device.id());
                    case FAILED -> { }
                }
            }
            return delivered;
        } catch (RuntimeException e) {
            log.warn("Push notification {} failed: {}", kind, e.getClass().getSimpleName());
            return false;
        }
    }

    /** Admin tərəfindən daxil edilən 3 dilli mətnlər müştərinin dilində. */
    private static Object[] localize(Object[] args, Language language) {
        Object[] result = args.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] instanceof LocalizedText text) {
                result[i] = text.resolve(language);
            }
        }
        return result;
    }

    private static int decodedLength(String value) {
        try {
            return value == null ? -1 : B64.decode(value).length;
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }
}
