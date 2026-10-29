import { cn } from '@/shared/lib/cn';

interface TabsProps<T extends string> {
  value: T;
  options: readonly { value: T; label: string }[];
  onChange: (value: T) => void;
  label: string;
  className?: string;
}

/** Səhifə daxilində bölmələr arasında keçid: alt xətli tablar (klaviatura ilə də). */
export function Tabs<T extends string>({ value, options, onChange, label, className }: TabsProps<T>) {
  return (
    <div role="tablist" aria-label={label} className={cn('flex gap-1 overflow-x-auto overflow-y-hidden border-b border-border [scrollbar-width:none]', className)}>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(option.value)}
            className={cn(
              '-mb-px shrink-0 border-b-2 px-3 py-2 text-sm font-medium transition-colors',
              'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              active ? 'border-primary text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}
