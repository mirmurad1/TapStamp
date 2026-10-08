import { useTranslation } from 'react-i18next';

import { isApiError } from '@/shared/api/client';
import { useErrorMessage } from '@/shared/api/useErrorMessage';
import { Alert } from '@/shared/ui/alert';

/** API xətası müştərinin dilində; yanlış OTP kodunda qalan cəhdlərin sayı da göstərilir. */
export function ErrorMessage({ error }: { error: unknown }) {
  const { t } = useTranslation('auth');
  const message = useErrorMessage();
  if (!error) {
    return null;
  }
  const remaining = isApiError(error, 'OTP_INVALID') ? error.problem.attemptsRemaining : undefined;
  return (
    <Alert tone="error">
      {message(error)}
      {typeof remaining === 'number' ? ` ${t('login.attemptsLeft', { count: remaining })}` : null}
    </Alert>
  );
}
