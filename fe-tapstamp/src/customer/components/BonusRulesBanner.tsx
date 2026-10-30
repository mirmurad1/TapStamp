import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { DEFAULT_TIME_ZONE, formatDate } from '@/shared/lib/format';
import { cn } from '@/shared/lib/cn';

import { publicApi } from '../api/customerApi';

type PublicRule = Awaited<ReturnType<typeof publicApi.bonusRules>>[number];

/** "14:00:00" → "14:00". */
function hhmm(value: string | undefined | null): string {
  return value ? value.slice(0, 5) : '';
}

/** Şirkətin yerli vaxtı: həftənin günü (1 = B.e.) və gün başlanğıcından dəqiqə. */
function localNow(timeZone: string): { day: number; minutes: number } {
  const parts = new Intl.DateTimeFormat('en-US', { weekday: 'short', hour: 'numeric', minute: 'numeric', hourCycle: 'h23', timeZone })
    .formatToParts(new Date());
  const get = (type: string) => parts.find((part) => part.type === type)?.value ?? '0';
  const day = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].indexOf(get('weekday')) + 1;
  return { day, minutes: Number(get('hour')) * 60 + Number(get('minute')) };
}

/** "14:00:00" → 840. */
function toMinutes(value: string): number {
  return Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
}

/** Hər dəqiqə yenilənən saat — geri sayım və "başlayır" yazısı üçün. */
function useMinuteTick(): number {
  const [tick, setTick] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setTick(Date.now()), 30_000);
    return () => window.clearInterval(id);
  }, []);
  return tick;
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
  const timeZone = company.data?.timezone ?? DEFAULT_TIME_ZONE;
  useMinuteTick();
  const now = localNow(timeZone);
  // Başlama və ya bitmə anı keçibsə, serverdən təzə vəziyyəti alırıq (rəng dərhal dəyişsin, 5 dəqiqə gözləməsin)
  const stale = (rules.data ?? []).some((rule) => {
    if (!rule.startTime || !rule.endTime || !(rule.days ?? []).includes(now.day)) {
      return false;
    }
    const inside = now.minutes >= toMinutes(rule.startTime) && now.minutes < toMinutes(rule.endTime);
    return inside !== Boolean(rule.runningNow);
  });
  const { refetch, isFetching } = rules;
  useEffect(() => {
    if (stale && !isFetching) {
      void refetch();
    }
  }, [stale, isFetching, refetch]);
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
        <RuleRow key={rule.id} rule={rule} now={now} branch={rule.branchId ? branchName(rule.branchId) : undefined} />
      ))}
    </section>
  );
}

/** Gedən qayda üçün "bitməsinə …", gözləyən üçün növbəti başlama anı; bilinmirsə — boş. */
function useTiming(rule: PublicRule, now: { day: number; minutes: number }, running: boolean): string | undefined {
  const { t } = useTranslation('profile');
  const days = rule.days ?? [];
  if (running) {
    if (!rule.endTime) {
      return undefined;
    }
    const left = toMinutes(rule.endTime) - now.minutes;
    if (left <= 0) {
      return undefined;
    }
    return left < 60
      ? t('bonus.endsInMinutes', { minutes: left })
      : t('bonus.endsIn', { hours: Math.floor(left / 60), minutes: left % 60 });
  }
  const start = rule.startTime ? toMinutes(rule.startTime) : 0;
  const time = hhmm(rule.startTime);
  for (let offset = 0; offset < 8; offset++) {
    const day = ((now.day - 1 + offset) % 7) + 1;
    if (!days.includes(day) || (offset === 0 && now.minutes >= start)) {
      continue;
    }
    if (offset === 0) {
      return t('bonus.startsToday', { time });
    }
    if (offset === 1) {
      return time ? t('bonus.startsTomorrowAt', { time }) : t('bonus.startsTomorrow');
    }
    const name = t(`bonus.days.${day as 1 | 2 | 3 | 4 | 5 | 6 | 7}`);
    return time ? t('bonus.startsOnDayAt', { day: name, time }) : t('bonus.startsOnDay', { day: name });
  }
  return undefined;
}

function RuleRow({ rule, branch, now }: { rule: PublicRule; branch: string | undefined; now: { day: number; minutes: number } }) {
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
  const timing = useTiming(rule, now, running);
  return (
    <div
      className={cn(
        // Rəng keçidi hamar olsun: vəziyyət dəyişəndə fon birdən deyil, yavaş-yavaş dəyişir
        'flex items-center gap-3 rounded-2xl border p-3.5 transition-[background-color,color,border-color,box-shadow] duration-700 motion-reduce:transition-none',
        running
          ? 'shine relative overflow-hidden border-transparent bg-primary text-primary-foreground shadow-md'
          : 'border-primary/20 bg-primary/5',
      )}
    >
      <span
        className={cn(
          'grid size-11 shrink-0 place-items-center rounded-xl text-lg font-extrabold transition-colors duration-700 motion-reduce:transition-none',
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
        {timing && (
          <span className={cn('mt-0.5 block text-xs font-semibold', running ? '' : 'text-primary')}>{timing}</span>
        )}
        <span className={cn('block text-xs', running ? 'opacity-80' : 'text-muted-foreground')}>{parts.join(' · ')}</span>
      </span>
    </div>
  );
}
