package az.tapstamp.customer.consent;

import az.tapstamp.shared.i18n.LocalizedText;

/**
 * Yeni şirkətin standart hüquqi sənədləri (məxfilik siyasəti və istifadə şərtləri), 3 dildə. Şirkət yaradılanda
 * bazaya ilk versiya kimi yazılır.
 *
 * <p>{@code {{stampRules}}} və {@code {{rewardRule}}} — müştəri tətbiqi bunları şirkətin cari parametrləri ilə
 * (möhürlər arası vaxt, gündəlik limit, hədiyyə) seçilmiş dildə əvəz edir; sahib parametrləri dəyişəndə mətn də
 * dəyişir.
 */
public final class DefaultLegalDocuments {

    private DefaultLegalDocuments() {
    }

    public static LocalizedText privacyPolicy() {
        return LocalizedText.of("""
                        # Məxfilik siyasəti

                        Biz yalnız loyallıq proqramı üçün lazım olan məlumatları toplayırıq: telefon nömrəniz, \
                        istəyə bağlı adınız və ziyarətlərinizin tarixçəsi.

                        Telefon nömrənizdən sizə kampaniyalar, endirimlər və yeniliklər barədə məlumat \
                        göndərmək üçün istifadə edilə bilər.

                        Hesabınızı istənilən vaxt silə bilərsiniz.""",
                """
                        # Privacy policy

                        We only collect what the loyalty programme needs: your phone number, your name (optional) \
                        and your visit history.

                        We may use your phone number to send you information about campaigns, discounts and news.

                        You can delete your account at any time.""",
                """
                        # Политика конфиденциальности

                        Мы собираем только то, что нужно для программы лояльности: номер телефона, имя (по желанию) \
                        и историю посещений.

                        Ваш номер телефона может использоваться для отправки вам информации об акциях, скидках \
                        и новостях.

                        Вы можете удалить аккаунт в любой момент.""");
    }

    public static LocalizedText terms() {
        return LocalizedText.of("""
                        # İstifadə şərtləri

                        Möhürlər yalnız şirkətimizdə alınmış məhsul və xidmətlər üçün verilir. {{stampRules}}

                        {{rewardRule}} Hədiyyə nağd pula dəyişdirilmir.""",
                """
                        # Terms of use

                        Stamps are given only for products and services bought from us. {{stampRules}}

                        {{rewardRule}} The reward cannot be exchanged for cash.""",
                """
                        # Условия использования

                        Штампы начисляются только за товары и услуги, приобретённые у нас. {{stampRules}}

                        {{rewardRule}} Подарок не обменивается на деньги.""");
    }
}
