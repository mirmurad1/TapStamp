package az.cafeloyalty.notification.mail;

/**
 * Konsol provayderi email "göndərəndə" yayımlanan hadisə. Dev outbox və testlər bunu dinləyir; prod-da
 * konsol provayderi qadağandır, ona görə bu hadisə orada heç vaxt yaranmır.
 */
public record MailSentEvent(MailMessage message) {
}
