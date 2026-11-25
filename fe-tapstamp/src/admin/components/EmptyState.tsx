import type { ReactNode } from 'react';

import { Button } from '@/shared/ui/button';

/** Boş siyahı: ikon, qısa izah və (varsa) növbəti addımın düyməsi — boz bir cümlə əvəzinə. */
export function EmptyState({
  icon,
  message,
  action,
}: {
  icon: ReactNode;
  message: string;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border px-6 py-12 text-center">
      <span className="grid size-12 place-items-center rounded-full bg-muted text-muted-foreground [&>svg]:size-6" aria-hidden="true">
        {icon}
      </span>
      <p className="max-w-sm text-sm text-muted-foreground">{message}</p>
      {action ? (
        <Button variant="outline" size="sm" onClick={action.onClick}>
          {action.label}
        </Button>
      ) : null}
    </div>
  );
}
