import { useMutation } from '@tanstack/react-query';
import { FileText } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';

import { customerAuthApi, legalSlug, type Me } from '../api/customerApi';
import { useCustomerSession } from '../auth/CustomerSessionProvider';
import { ErrorMessage } from '../components/ErrorMessage';

/** Hüquqi sənədlərin yeni versiyası dərc olunub: davam etmək üçün yenidən razılıq lazımdır. */
export function ConsentPage({ pending }: { pending: NonNullable<Me['pendingConsents']> }) {
  const { t } = useTranslation(['auth', 'profile']);
  const { refresh } = useCustomerSession();
  const [accepted, setAccepted] = useState(false);
  const accept = useMutation({
    mutationFn: () =>
      customerAuthApi.acceptConsents(
        pending.map((document) => ({ type: document.type ?? 'TERMS', version: document.version ?? 1 })),
      ),
    onSuccess: () => refresh(),
  });

  return (
    <Card className="mt-2">
      <CardHeader>
        <CardTitle>{t('auth:consent.title')}</CardTitle>
        <CardDescription>{t('auth:consent.subtitle')}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <ErrorMessage error={accept.error} />
        <ul className="flex flex-col gap-2">
          {pending.map((document) => {
            const slug = legalSlug(document.type);
            return (
              <li key={document.type}>
                <a
                  href={`/legal/${slug}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex min-h-11 items-center gap-3 rounded-xl bg-muted px-4 font-medium underline-offset-4 hover:underline"
                >
                  <FileText className="size-5 shrink-0 text-accent" aria-hidden="true" />
                  {t(`profile:legal.${slug}`)}
                </a>
              </li>
            );
          })}
        </ul>
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            checked={accepted}
            onChange={(event) => setAccepted(event.target.checked)}
            className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
          />
          <span>{t('auth:consent.checkbox')}</span>
        </label>
        <Button size="lg" disabled={!accepted || accept.isPending} onClick={() => accept.mutate()}>
          {t('auth:consent.submit')}
        </Button>
      </CardContent>
    </Card>
  );
}
