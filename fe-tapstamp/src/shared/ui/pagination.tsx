import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useEffect, useId } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { formatNumber } from '@/shared/lib/format';
import { cn } from '@/shared/lib/cn';

import { Button } from './button';
import { Select } from './input';
import { PAGE_SIZES, type PageState } from './usePagination';

/** Göstəriləcək səhifə nömrələri: ilk, son və cari səhifənin qonşuları; aralıqlar "…" ilə. */
function pageNumbers(current: number, count: number): (number | 'gap')[] {
  const pages = new Set([0, count - 1, current - 1, current, current + 1].filter((p) => p >= 0 && p < count));
  const sorted = [...pages].sort((a, b) => a - b);
  const result: (number | 'gap')[] = [];
  let previous: number | undefined;
  for (const p of sorted) {
    if (previous !== undefined && p - previous > 1) {
      result.push('gap');
    }
    result.push(p);
    previous = p;
  }
  return result;
}

interface PaginationProps {
  state: PageState;
  total: number;
  className?: string;
}

/** Siyahının altında: "11–20 / 57", səhifədə neçə sətir (5/10/20/50) və səhifələr arası keçid. */
export function Pagination({ state, total, className }: PaginationProps) {
  const { t } = useTranslation('common');
  const lang = currentLanguage();
  const id = useId();
  const { page, size, setPage, setSize } = state;
  const count = Math.max(Math.ceil(total / size), 1);

  // Sətirlər silinəndə (və ya filtr dəyişəndə) mövcud olmayan səhifədə qalmamaq üçün
  useEffect(() => {
    if (total > 0 && page >= count) {
      setPage(count - 1);
    }
  }, [total, page, count, setPage]);

  if (total === 0) {
    return null;
  }
  const from = page * size + 1;
  const to = Math.min((page + 1) * size, total);

  return (
    <nav
      aria-label={t('pagination.label')}
      className={cn('flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground', className)}
    >
      <div className="flex flex-wrap items-center gap-3">
        <span className="tabular-nums">
          {t('pagination.range', { from: formatNumber(from, lang), to: formatNumber(to, lang), total: formatNumber(total, lang) })}
        </span>
        <label htmlFor={id} className="flex items-center gap-2">
          {t('pagination.perPage')}
          <Select id={id} className="min-h-9 w-20 py-1" value={size} onChange={(event) => setSize(Number(event.target.value))}>
            {PAGE_SIZES.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </Select>
        </label>
      </div>
      {count > 1 ? (
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage(page - 1)}
            aria-label={t('pagination.previous')}
          >
            <ChevronLeft aria-hidden="true" />
            <span className="hidden sm:inline">{t('pagination.previous')}</span>
          </Button>
          <span className="px-2 tabular-nums sm:hidden">
            {page + 1} / {count}
          </span>
          <div className="hidden items-center gap-1 sm:flex">
            {pageNumbers(page, count).map((value, i) =>
              value === 'gap' ? (
                <span key={`gap-${i}`} className="px-1" aria-hidden="true">
                  …
                </span>
              ) : (
                <Button
                  key={value}
                  variant={value === page ? 'default' : 'ghost'}
                  size="sm"
                  className="min-w-9 tabular-nums"
                  aria-current={value === page ? 'page' : undefined}
                  aria-label={t('pagination.page', { page: value + 1 })}
                  onClick={() => setPage(value)}
                >
                  {value + 1}
                </Button>
              ),
            )}
          </div>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= count - 1}
            onClick={() => setPage(page + 1)}
            aria-label={t('pagination.next')}
          >
            <span className="hidden sm:inline">{t('pagination.next')}</span>
            <ChevronRight aria-hidden="true" />
          </Button>
        </div>
      ) : null}
    </nav>
  );
}
