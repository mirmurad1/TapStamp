import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useTranslation } from 'react-i18next';
import { beforeEach, describe, expect, it } from 'vitest';

import { customerI18n } from '@/customer/i18n';
import { readLangCookie } from '@/shared/i18n/languages';

import { LanguageSwitcher } from './LanguageSwitcher';

function PhoneForm() {
  const { t } = useTranslation('auth');
  return (
    <form>
      <label>
        {t('phone.label')}
        <input name="phone" />
      </label>
      <LanguageSwitcher />
    </form>
  );
}

describe('LanguageSwitcher', () => {
  beforeEach(async () => {
    await customerI18n.changeLanguage('az');
  });

  it('dili dərhal dəyişir, html lang-ı və cookie-ni yeniləyir, daxil edilmiş məlumatı saxlayır', async () => {
    const user = userEvent.setup();
    render(<PhoneForm />);

    const input = screen.getByLabelText('Telefon nömrəsi');
    await user.type(input, '501234567');

    await user.click(screen.getByRole('radio', { name: 'RU' }));

    expect(screen.getByLabelText('Номер телефона')).toHaveValue('501234567');
    expect(document.documentElement.lang).toBe('ru');
    expect(readLangCookie()).toBe('ru');
    expect(screen.getByRole('radio', { name: 'RU' })).toHaveAttribute('aria-checked', 'true');
  });
});
