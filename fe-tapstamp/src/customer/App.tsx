import { RouterProvider } from 'react-router';

import { PlanGate } from '@/shared/plan/PlanGate';

import { CustomerSessionProvider } from './auth/CustomerSessionProvider';
import { customerRouter } from './router';

/** Müştəri tətbiqi: şirkətin paketi (sınaq bitibsə — dayandırılma ekranı), sessiya və marşrutlar. */
export function App() {
  return (
    <PlanGate audience="customer" loading={null}>
      <CustomerSessionProvider>
        <RouterProvider router={customerRouter} />
      </CustomerSessionProvider>
    </PlanGate>
  );
}
