import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query';

import { ApiError } from '@/shared/api/client';

export const customerKeys = {
  session: ['customer-session'] as const,
  me: ['customer-me'] as const,
  devices: ['customer-devices'] as const,
};

/** Sessiya itəndə (yeniləmə də alınmadı): müştəri qonaq olur, şəxsi məlumat keşdən silinir. */
export function clearCustomerData(client: QueryClient): void {
  client.setQueryData(customerKeys.session, 'NONE');
  // Profil yenidən sorğulanmadan sıfırlanır: resetQueries aktiv sorğunu dərhal təkrar göndərər, 401 isə yenə bura
  // gətirər (sonsuz dövr)
  client.getQueryCache().findAll({ queryKey: customerKeys.me }).forEach((query) => query.reset());
  client.removeQueries({ queryKey: customerKeys.devices });
}

function onError(client: QueryClient, error: unknown) {
  if (error instanceof ApiError && error.status === 401) {
    clearCustomerData(client);
  }
}

export function createCustomerQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({ onError: (error) => onError(client, error) }),
    mutationCache: new MutationCache({ onError: (error) => onError(client, error) }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (count, error) => !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  });
  return client;
}
