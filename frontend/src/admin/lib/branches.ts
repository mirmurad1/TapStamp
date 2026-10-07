import { useQuery } from '@tanstack/react-query';

import { currentLanguage } from '@/shared/i18n/setup';
import { resolveText } from '@/shared/i18n/localized';

import { contextApi } from '../api/adminApi';

/** Şirkətin filialları (cari dildə adları ilə) — filtrlər və cədvəllər üçün. */
export function useBranchNames() {
  const company = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company });
  const lang = currentLanguage();
  const branches = (company.data?.branches ?? []).map((branch) => ({ id: branch.id ?? '', name: resolveText(branch.name, lang) }));
  const nameOf = (id: string | undefined) => branches.find((branch) => branch.id === id)?.name;
  return { branches, nameOf };
}
