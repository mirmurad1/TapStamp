import { RouterProvider } from 'react-router';

import { CustomerSessionProvider } from './auth/CustomerSessionProvider';
import { customerRouter } from './router';

/** Müştəri tətbiqi: sessiya (lazım gələrsə, tokenin yenilənməsi) və marşrutlar. */
export function App() {
  return (
    <CustomerSessionProvider>
      <RouterProvider router={customerRouter} />
    </CustomerSessionProvider>
  );
}
