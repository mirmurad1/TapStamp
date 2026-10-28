import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatDate } from '@/shared/lib/format';
import { cn } from '@/shared/lib/cn';

import { publicApi } from '../api/customerApi';

type PublicRule = Awaited<ReturnType<typeof publicApi.bonusRules>>[number];

/** "14:00:00" → "14:00". */
function hhmm(value: string | undefined | null): string {
  return value ? value.slice(0, 5) : '';
}

/**
 * Kartın üstündə bu günün bonus qaydaları: indi qüvvədə olan qayda vurğulanır ("indi hər möhürə +1"), qalanları
 * cədvəli ilə göstərilir. Qayda yoxdursa heç nə göstərilmir.
 */
export function BonusRulesBanner() {
  const rules = useQuery({
    queryKey: ['public-bonus-rules'],
    queryFn: publicApi.bonusRules,
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  });
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const items = [...(rules.data ?? [])].sort((a, b) => Number(Boolean(b.runningNow)) - Number(Boolean(a.runningNow)));
  if (items.length === 0) {
    return null;
  }
  const branchName = (id: string | undefined) => {
    const branch = (company.data?.branches ?? []).find((candidate) => candidate.id === id);
    return branch ? resolveText(branch.name, currentLanguage()) : undefined;
  };
  return (
    <section className="flex flex-col gap-2">
      {items.map((rule) => (
        <RuleRow key={rule.id} rule={rule} branch={rule.branchId ? branchName(rule.branchId) : undefined} />
      ))}
    </section>
  );
}

function RuleRow({ rule, branch }: { rule: PublicRule; branch: string | undefined }) {
  const { t } = useTranslation('profile');
  const lang = currentLanguage();
  const days = rule.days ?? [];
  let when: string;
  if (days.length === 7) {
    when = t('bonus.everyDay');
  } else if (days.join() === '1,2,3,4,5') {
    when = t('bonus.weekdays');
  } else if (days.join() === '6,7') {
    when = t('bonus.weekend');
  } else {
    when = days.map((day) => t(`bonus.days.${day as 1 | 2 | 3 | 4 | 5 | 6 | 7}`)).join(', ');
  }
  const parts = [when];
  if (rule.startTime) {
    parts.push(`${hhmm(rule.startTime)}–${hhmm(rule.endTime)}`);
  }
  if (rule.endsOn) {
    parts.push(t('bonus.until', { date: formatDate(rule.endsOn, lang, 'UTC') }));
  }
  if (branch) {
    parts.push(branch);
  }
  const running = Boolean(rule.runningNow);
  return (
    <div
      className={cn(
        'flex items-center gap-3 rounded-2xl p-3.5',
        running ? 'shine relative overflow-hidden bg-primary text-primary-foreground shadow-md' : 'border border-primary/20 bg-primary/5',
      )}
    >
      <span
        className={cn(
          'grid size-11 shrink-0 place-items-center rounded-xl text-lg font-extrabold',
          running ? 'bg-white/20' : 'bg-primary/10 text-primary',
        )}
      >
        +{rule.bonusStamps}
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <Sparkles className="size-4 shrink-0" aria-hidden="true" />
          <span className="truncate">{resolveText(rule.name, lang)}</span>
        </span>
        <span className={cn('block text-sm', running ? 'opacity-90' : 'text-muted-foreground')}>
          {running ? t('bonus.runningNow', { count: rule.bonusStamps ?? 1 }) : t('bonus.later', { count: rule.bonusStamps ?? 1 })}
        </span>
        <span className={cn('block text-xs', running ? 'opacity-80' : 'text-muted-foreground')}>{parts.join(' · ')}</span>
      </span>
    </div>
  );
}
