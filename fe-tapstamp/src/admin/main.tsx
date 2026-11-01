import '@/shared/styles/globals.css';
import './i18n';

import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { applyCachedBrandColor } from '@/shared/lib/brand';

import { App } from './App';
import { createAdminQueryClient } from './auth/session';

const queryClient = createAdminQueryClient();

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
