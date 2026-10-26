import { useQuery } from '@tanstack/react-query';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';

import { publicApi } from '../api/customerApi';

/** Şirkətin adı (cari dildə) — ikon sxemləri və möhür üzərindəki yazı üçün. */
export function useCompanyName(): string {
  const company = useQuery({ queryKey: ['public-company'], queryFn: publicApi.company, staleTime: 5 * 60_000 });
  return resolveText(company.data?.name, currentLanguage());
}

/** Şirkət adının ilk hərfi (ad yoxdursa boş). */
export function useCompanyInitial(): string {
  return useCompanyName().trim().charAt(0).toLocaleUpperCase(currentLanguage());
}
