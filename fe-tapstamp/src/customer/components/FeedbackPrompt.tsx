import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, MessageSquareHeart } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';
import { formatDate } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';

import { feedbackApi, type FeedbackRating } from '../api/customerApi';
import { OfferRow, Sheet } from './Sheet';

const FEEDBACK_KEY = ['feedback-request'] as const;
const RATINGS: { value: FeedbackRating; emoji: string }[] = [
  { value: 'GOOD', emoji: '😊' },
  { value: 'OK', emoji: '😐' },
  { value: 'BAD', emoji: '🙁' },
];
const REASONS = ['SERVICE', 'WAIT', 'QUALITY', 'CLEANLINESS', 'PRICE', 'OTHER'] as const;

/**
 * Ziyarətdən sonra rəy: cavab gözləyən sorğu varsa təkliflər siyahısında sətir; push-dan gələndə (?feedback=1)
 * vərəq özü açılır. Əla — bir toxunuşla göndərilir; normal və pis — səbəb və şərh (istəyə bağlı).
 */
export function FeedbackRow() {
  const { t } = useTranslation('profile');
  const request = useQuery({ queryKey: FEEDBACK_KEY, queryFn: feedbackApi.open });
  const [open, setOpen] = useState(() => new URLSearchParams(window.location.search).get('feedback') === '1');
  const data = request.data;
  if (!data) {
    return null;
  }
  return (
    <>
      <OfferRow icon={<MessageSquareHeart />} title={t('feedback.rowTitle')} hint={t('feedback.rowHint')} onClick={() => setOpen(true)} />
      <FeedbackSheet open={open} onOpenChange={setOpen} requestId={data.id ?? ''}
        place={`${resolveText(data.branchName, currentLanguage())} · ${data.visitAt ? formatDate(data.visitAt, currentLanguage()) : ''}`} />
    </>
  );
}

function FeedbackSheet({ open, onOpenChange, requestId, place }: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  requestId: string;
  place: string;
}) {
  const { t } = useTranslation('profile');
  const queryClient = useQueryClient();
  const [rating, setRating] = useState<FeedbackRating>();
  const [reasons, setReasons] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const send = useMutation({
    mutationFn: (value: FeedbackRating) =>
      feedbackApi.answer(requestId, { rating: value, reasons: value === 'GOOD' ? [] : reasons, comment: comment || undefined }),
  });
  const close = (next: boolean) => {
    onOpenChange(next);
    if (!next && send.isSuccess) {
      void queryClient.invalidateQueries({ queryKey: FEEDBACK_KEY });
    }
  };
  const pick = (value: FeedbackRating) => {
    setRating(value);
    if (value === 'GOOD') {
      send.mutate(value);
    }
  };
  const toggle = (reason: string) =>
    setReasons((current) => (current.includes(reason) ? current.filter((r) => r !== reason) : [...current, reason]));

  return (
    <Sheet open={open} onOpenChange={close} title={t('feedback.title')} description={place}>
      {send.isSuccess ? (
        <div className="flex flex-col items-center gap-3 py-4">
          <span className="grid size-14 place-items-center rounded-full bg-primary/10 text-primary">
            <Check className="size-7" aria-hidden="true" />
          </span>
          <p className="text-lg font-bold">{t('feedback.thanks')}</p>
          <p className="text-sm text-muted-foreground">{t(rating === 'GOOD' ? 'feedback.thanksGood' : 'feedback.thanksOther')}</p>
          <Button size="lg" className="mt-2 w-full" onClick={() => close(false)}>
            {t('feedback.close')}
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-3 gap-2" role="group" aria-label={t('feedback.title')}>
            {RATINGS.map((item) => (
              <button
                key={item.value}
                type="button"
                onClick={() => pick(item.value)}
                disabled={send.isPending}
                aria-pressed={rating === item.value}
                className={cn(
                  'flex flex-col items-center gap-1 rounded-2xl border-2 py-3 text-sm font-semibold transition-colors',
                  rating === item.value ? 'border-primary bg-primary/10' : 'border-border hover:bg-muted',
                )}
              >
                <span className="text-4xl leading-none" aria-hidden="true">
                  {item.emoji}
                </span>
                {t(`feedback.rating.${item.value}`)}
              </button>
            ))}
          </div>
          {rating && rating !== 'GOOD' ? (
            <>
              <p className="text-left text-sm font-semibold">{t('feedback.whatToImprove')}</p>
              <div className="flex flex-wrap gap-2">
                {REASONS.map((reason) => (
                  <button
                    key={reason}
                    type="button"
                    onClick={() => toggle(reason)}
                    aria-pressed={reasons.includes(reason)}
                    className={cn(
                      'rounded-full border px-3 py-1.5 text-sm',
                      reasons.includes(reason) ? 'border-primary bg-primary text-primary-foreground' : 'border-border',
                    )}
                  >
                    {t(`feedback.reasons.${reason}`)}
                  </button>
                ))}
              </div>
              <textarea
                value={comment}
                onChange={(event) => setComment(event.target.value)}
                maxLength={500}
                rows={3}
                placeholder={t('feedback.commentPlaceholder')}
                aria-label={t('feedback.commentPlaceholder')}
                className="w-full rounded-xl border border-border bg-card p-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <Button size="lg" onClick={() => send.mutate(rating)} disabled={send.isPending}>
                {t('feedback.send')}
              </Button>
            </>
          ) : null}
          {send.isError ? <p className="text-sm text-[var(--destructive-ink)]">{t('feedback.error')}</p> : null}
          <p className="text-xs text-muted-foreground">{t('feedback.privacy')}</p>
        </div>
      )}
    </Sheet>
  );
}
