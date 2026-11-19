import { useState } from 'react';

import { initialOf } from '@/shared/lib/initial';
import { cn } from '@/shared/lib/cn';

/**
 * Şirkətin ikonu platforma panelində: şirkətin öz loqosu (platforma domenindən, CSP "img-src 'self'"), loqo yoxdursa və ya
 * yüklənmirsə — adının ilk hərfi.
 */
export function TenantLogo({ tenantId, name, lang, className }: { tenantId: string; name: string; lang: string; className?: string }) {
  const [failed, setFailed] = useState(false);
  return (
    <span
      className={cn('grid shrink-0 place-items-center overflow-hidden bg-primary/10 font-semibold text-primary', className)}
      aria-hidden="true"
    >
      {failed || !tenantId ? (
        initialOf(name, lang)
      ) : (
        <img
          src={`/api/v1/platform/tenants/${tenantId}/logo`}
          alt=""
          loading="lazy"
          className="size-full bg-card object-cover"
          onError={() => setFailed(true)}
        />
      )}
    </span>
  );
}
