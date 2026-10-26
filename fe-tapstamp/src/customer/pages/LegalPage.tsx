import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, LoaderCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate, useParams } from 'react-router';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';

import { publicApi, type Company } from '../api/customerApi';
import { ErrorMessage } from '../components/ErrorMessage';

/** Hüquqi sənəd səhifəsi ({@code /legal/:slug}): Parametrlərdən açılır, "Geri" gəldiyi səhifəyə qaytarır. */
export function LegalPage() {
  const { t } = useTranslation('profile');
  const { slug = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  // Geri — gəldiyi səhifəyə; səhifə birbaşa açılıbsa Parametrlərə
  const back = () => (location.key === 'default' ? navigate('/settings') : navigate(-1));

  return (
    <div className="flex flex-col gap-4 pt-2">
      <Button variant="ghost" className="self-start" onClick={back}>
        <ArrowLeft aria-hidden="true" />
        {t('legal.back')}
      </Button>
      <Card>
        <CardContent className="pt-5 sm:pt-6">
          <LegalDocument slug={slug} />
        </CardContent>
      </Card>
    </div>
  );
}

/**
 * Hüquqi sənədin mətni (səhifədə və qeydiyyat ekranının vərəqində). Mətn sadə formatdadır (# başlıq, > qeyd,
 * abzaslar) və təhlükəsiz şəkildə — HTML kimi yox, React elementləri kimi göstərilir. {{stampRules}} və {{rewardRule}}
 * şirkətin cari parametrləri ilə əvəz olunur (sahib Parametrlərdə dəyişəndə mətn də dəyişir).
 */
export function LegalDocument({ slug }: { slug: string }) {
  const lang = currentLanguage();
  const document = useQuery({ queryKey: ['legal', slug], queryFn: () => publicApi.legalDocument(slug) });
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const rules = useRuleSentences(company.data);
  const blocks = resolveText(document.data?.content, lang)
    .replace(/\{\{(stampRules|rewardRule)\}\}/g, (_, key: 'stampRules' | 'rewardRule') => rules[key])
    .split(/\n\s*\n/)
    .filter((block) => block.trim());

  if (document.isPending) {
    return (
      <div className="flex justify-center py-8 text-primary" role="status">
        <LoaderCircle className="size-6 animate-spin" aria-hidden="true" />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-3 text-left">
      <ErrorMessage error={document.error} />
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
            <blockquote key={index} className="border-l-4 border-primary/40 pl-3 text-sm text-muted-foreground">
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
    </div>
  );
}

/** Şirkətin möhür və hədiyyə qaydasının seçilmiş dildə cümlələri (istifadə şərtləri üçün). */
function useRuleSentences(company: Company | undefined): { stampRules: string; rewardRule: string } {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const interval = company?.stampRules?.minIntervalMinutes ?? 0;
  const perDay = company?.stampRules?.maxStampsPerDay;
  const stampRules = [
    interval > 0 ? t('legal.rules.interval', { count: interval }) : '',
    perDay ? t('legal.rules.perDay', { count: perDay }) : '',
  ]
    .filter(Boolean)
    .join(' ');
  const rule = company?.rewardRule;
  const rewardRule = rule?.stampsRequired
    ? t('legal.rules.reward', { count: rule.stampsRequired, reward: resolveText(rule.rewardName, lang) })
    : '';
  return { stampRules, rewardRule };
}
