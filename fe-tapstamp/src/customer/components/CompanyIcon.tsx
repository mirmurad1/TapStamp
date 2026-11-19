import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { cn } from '@/shared/lib/cn';

import { publicApi } from '../api/customerApi';
import { useCompanyInitial } from '../lib/companyInitial';

/**
 * Şirkətin ikonu (nümunə bildiriş, ana ekran sxemi): yüklənmiş loqo, yoxdursa — brend rəngində adın ilk hərfi.
 * Ölçü və künclər {@code className} ilə verilir.
 */
export function CompanyIcon({ className }: { className?: string }) {
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  const initial = useCompanyInitial();
  const [failed, setFailed] = useState(false);
  const logoUrl = company.data?.logoUrl;
  const showLogo = Boolean(logoUrl) && !failed;
  return (
    <span
      aria-hidden="true"
      className={cn(
        'grid shrink-0 place-items-center overflow-hidden font-extrabold',
        showLogo ? 'bg-card ring-1 ring-border' : 'bg-primary text-primary-foreground',
        className,
      )}
    >
      {showLogo ? (
        <img src={logoUrl} alt="" className="size-full object-cover" onError={() => setFailed(true)} />
      ) : (
        initial
      )}
    </span>
  );
}
