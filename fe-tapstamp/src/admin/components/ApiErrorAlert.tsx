import { useTranslation } from 'react-i18next';

import { isApiError } from '@/shared/api/client';
import { useErrorMessage } from '@/shared/api/useErrorMessage';
import { Alert } from '@/shared/ui/alert';

import type { PasswordViolation } from '../lib/forms';

const VIOLATIONS: readonly PasswordViolation[] = ['TOO_SHORT', 'TOO_LONG', 'COMMON_PASSWORD', 'CONTAINS_EMAIL', 'REPETITIVE'];

/** API xətası: kod üzrə tərcümə; şifrə siyasəti pozulubsa, hər səbəb ayrıca sadalanır. */
export function ApiErrorAlert({ error }: { error: unknown }) {
  const { t } = useTranslation('auth');
  const message = useErrorMessage();
  if (!error) {
    return null;
  }
  const violations = isApiError(error, 'PASSWORD_POLICY_VIOLATION')
    ? (error.problem.violations ?? []).filter((v): v is PasswordViolation => VIOLATIONS.includes(v as PasswordViolation))
    : [];
  return (
    <Alert tone="error">
      <p>{message(error)}</p>
      {violations.length > 0 ? (
        <ul className="mt-1 list-disc ps-4">
          {violations.map((violation) => (
            <li key={violation}>{t(`passwordPolicy.${violation}`)}</li>
          ))}
        </ul>
      ) : null}
    </Alert>
  );
}
