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

export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        'flex min-h-11 w-full rounded-xl border border-border bg-card px-3 py-2 text-base text-foreground shadow-sm',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50 sm:text-sm',
        'aria-[invalid=true]:border-destructive',
        className,
      )}
      {...props}
    />
  ),
);
Select.displayName = 'Select';
