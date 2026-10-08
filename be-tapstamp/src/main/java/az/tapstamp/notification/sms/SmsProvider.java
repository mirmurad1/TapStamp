package az.tapstamp.notification.sms;

/** SMS provayderi. Dev-də konsol, prod-da real HTTP provayder. */
public interface SmsProvider {

    void send(SmsMessage message);
}
