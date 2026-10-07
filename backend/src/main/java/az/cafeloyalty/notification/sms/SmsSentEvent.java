package az.cafeloyalty.notification.sms;

/**
 * Konsol provayderi SMS "göndərəndə" yayımlanan hadisə. Dev outbox və testlər bunu dinləyir; prod-da konsol provayderi
 * qadağandır, ona görə bu hadisə orada heç vaxt yaranmır.
 */
public record SmsSentEvent(SmsMessage message) {
}
