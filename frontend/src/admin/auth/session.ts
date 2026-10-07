import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/shared/api/client';

/** Sessiyanın bitdiyini bildirən kodlar: istifadəçi giriş səhifəsinə yönləndirilir. */
const SESSION_LOST_CODES = new Set(['SESSION_EXPIRED', 'UNAUTHENTICATED']);

let expiredNotice = false;

/** Giriş səhifəsi "sessiyanın vaxtı bitdi" bildirişini bir dəfə göstərir. */
export function consumeExpiredNotice(): boolean {
  const value = expiredNotice;
  expiredNotice = false;
  return value;
}

/** İstifadəçi çıxanda: sessiya NONE olur, profil və istifadəçiyə aid bütün keşlənmiş məlumat silinir. */
export function clearUserData(client: QueryClient): void {
  client.setQueryData(sessionKeys.session, 'NONE');
  client.getQueryCache().findAll({ queryKey: sessionKeys.me }).forEach((query) => query.reset());
  const kept = new Set<unknown>([sessionKeys.context[0], sessionKeys.session[0], sessionKeys.me[0]]);
  client.removeQueries({ predicate: (query) => !kept.has(query.queryKey[0]) });
}

export const sessionKeys = {
  context: ['context'] as const,
  session: ['session'] as const,
  me: ['me'] as const,
};

function onError(client: QueryClient, error: unknown) {
  if (error instanceof ApiError && error.status === 401 && SESSION_LOST_CODES.has(error.code)) {
    expiredNotice = error.code === 'SESSION_EXPIRED';
    // Müşahidə olunan sorğular silinmir, sıfırlanır (yenidən sorğulanmadan — əks halda 401 sonsuz dövr yaradar)
    client.setQueryData(sessionKeys.session, 'NONE');
    client.getQueryCache().findAll({ queryKey: sessionKeys.me }).forEach((query) => query.reset());
  }
}

/** Admin tətbiqinin QueryClient-i: istənilən sorğuda sessiya bitərsə, vəziyyət dərhal "NONE" olur. */
export function createAdminQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (error) => onError(client, error) }),
    mutationCache: new MutationCache({ onError: (error) => onError(client, error) }),
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        refetchOnWindowFocus: true,
        retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  });
  return client;
}
