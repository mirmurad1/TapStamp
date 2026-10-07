import '@/shared/styles/globals.css';
import './i18n';

import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { setSessionRefresher } from '@/shared/api/client';

import { App } from './App';
import { createCustomerQueryClient } from './auth/queryClient';
import { refreshSession } from './auth/refresh';

// 401 cavabında (access tokenin vaxtı keçib) sessiya bir dəfə yenilənir və sorğu təkrarlanır
setSessionRefresher(refreshSession);

const queryClient = createCustomerQueryClient();

const root = document.getElementById('root');
if (!root) {
  throw new Error('Root element #root not found');
}

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
