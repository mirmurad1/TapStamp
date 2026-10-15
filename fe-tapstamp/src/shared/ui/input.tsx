import * as React from 'react';

import { cn } from '@/shared/lib/cn';

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type = 'text', ...props }, ref) => (
    <input
      ref={ref}
      type={type}
      className={cn(
        'flex min-h-11 w-full rounded-xl border border-border bg-card px-3.5 py-2 text-base text-foreground shadow-sm transition-colors',
        'placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        'disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-destructive sm:text-sm',
        className,
      )}
      {...props}
    />
  ),
);
Input.displayName = 'Input';

/** Ox (chevron): brauzerin standart oxu sahənin lap kənarına yapışır, ona görə öz oxumuz kənardan məsafə ilə çəkilir. */
const CHEVRON =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%236b7280' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")";

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, style, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        'flex min-h-11 w-full appearance-none rounded-xl border border-border bg-card py-2 pl-3.5 pr-10 text-base text-foreground shadow-sm',
        'bg-[length:1rem_1rem] bg-[position:right_0.875rem_center] bg-no-repeat',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 sm:text-sm',
        'aria-[invalid=true]:border-destructive',
        className,
      )}
      style={{ backgroundImage: CHEVRON, ...style }}
      {...props}
    />
  ),
);
Select.displayName = 'Select';
