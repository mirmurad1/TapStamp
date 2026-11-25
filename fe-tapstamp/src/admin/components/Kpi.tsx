import type { ReactNode } from 'react';

/**
 * Göstərici kartı: başlıq və kiçik ikon, böyük rəqəm, bir sətirlik izah. Şirkətin göstəricilər panelində və platforma
 * panelində eyni görünüş üçün ortaqdır.
 */
export function Kpi({
  icon,
  label,
  value,
  loading = false,
  children,
  action,
}: {
  icon: ReactNode;
  label: string;
  value: string;
  loading?: boolean;
  children?: ReactNode;
  /** Kartın altında kiçik düymə (məs. siyahını açmaq) */
  action?: { label: string; onClick: () => void };
}) {
  return (
    <div className="flex flex-col gap-1 rounded-2xl border border-border bg-card p-4">
      <div className="flex items-center justify-between gap-2 text-sm text-muted-foreground">
        <span className="truncate">{label}</span>
        <span className="shrink-0 text-primary [&>svg]:size-4" aria-hidden="true">
          {icon}
        </span>
      </div>
      <div>
        <p className="font-display text-3xl font-semibold tabular-nums" aria-busy={loading}>
          {loading ? '…' : value}
        </p>
        {children !== undefined ? <p className="mt-1 text-xs text-muted-foreground">{loading ? ' ' : children}</p> : null}
      </div>
      {action ? (
        <button
          type="button"
          onClick={action.onClick}
          className="mt-1 self-start rounded-md text-sm font-medium text-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        >
          {action.label}
        </button>
      ) : null}
    </div>
  );
}
