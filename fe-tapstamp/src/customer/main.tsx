import '@/shared/styles/globals.css';
import './i18n';

import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { setSessionRefresher } from '@/shared/api/client';
import { applyCachedBrandColor } from '@/shared/lib/brand';

import { App } from './App';
import { createCustomerQueryClient } from './auth/queryClient';
import { refreshSession } from './auth/refresh';
import { registerServiceWorker } from './pwa/registerServiceWorker';

// 401 cavabında (access tokenin vaxtı keçib) sessiya bir dəfə yenilənir və sorğu təkrarlanır
setSessionRefresher(refreshSession);

const queryClient = createCustomerQueryClient();
registerServiceWorker();

// Son brend rəngi dərhal — şirkətin rəngi serverdən gələnə qədər standart rəng görünməsin
applyCachedBrandColor();

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
