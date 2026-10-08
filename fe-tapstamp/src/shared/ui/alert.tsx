import { CircleAlert, CircleCheck, Info } from 'lucide-react';
import * as React from 'react';

import { cn } from '@/shared/lib/cn';

const ICONS = { error: CircleAlert, success: CircleCheck, info: Info } as const;

interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  tone?: keyof typeof ICONS;
}

/** Forma və səhifə səviyyəsində bildiriş. Xəta üçün role="alert" (ekran oxuyucusu dərhal oxuyur). */
export function Alert({ tone = 'info', className, children, ...props }: AlertProps) {
  const Icon = ICONS[tone];
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm',
        tone === 'error' && 'border-destructive/40 bg-destructive/10 text-destructive',
        tone === 'success' && 'border-success/40 bg-success/10 text-success',
        tone === 'info' && 'border-border bg-muted text-foreground',
        className,
      )}
      {...props}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1">{children}</div>
    </div>
  );
}
