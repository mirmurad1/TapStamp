import { useQuery } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';

import { publicApi } from '../api/customerApi';
import { ErrorMessage } from '../components/ErrorMessage';

/**
 * Hüquqi sənəd. Mətn sadə formatdadır (# başlıq, > qeyd, abzaslar) və təhlükəsiz şəkildə — HTML kimi yox, React
 * elementləri kimi göstərilir.
 */
export function LegalPage() {
  const { t } = useTranslation('profile');
  const { slug = '' } = useParams();
  const lang = currentLanguage();
  const document = useQuery({ queryKey: ['legal', slug], queryFn: () => publicApi.legalDocument(slug) });
  const blocks = resolveText(document.data?.content, lang).split(/\n\s*\n/).filter((block) => block.trim());

  return (
    <div className="flex flex-col gap-4 pt-2">
      <Button asChild variant="ghost" className="self-start">
        <Link to="/">
          <ArrowLeft aria-hidden="true" />
          {t('legal.back')}
        </Link>
      </Button>
      <ErrorMessage error={document.error} />
      {document.data ? (
        <Card>
          <CardContent className="flex flex-col gap-3 pt-5 sm:pt-6">
            {blocks.map((block, index) => {
              const text = block.trim();
              if (text.startsWith('# ')) {
                return (
                  <h1 key={index} className="font-display text-2xl font-semibold">
                    {text.slice(2)}
                  </h1>
                );
              }
              if (text.startsWith('> ')) {
                return (
                  <blockquote key={index} className="border-l-4 border-accent pl-3 text-sm text-muted-foreground">
                    {text.slice(2)}
                  </blockquote>
                );
              }
              return (
                <p key={index} className="leading-relaxed">
                  {text}
                </p>
              );
            })}
            {document.data.publishedAt ? (
              <p className="pt-2 text-xs text-muted-foreground">
                {t('legal.version', {
                  version: document.data.version,
                  date: formatDate(document.data.publishedAt, lang),
                })}
              </p>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
