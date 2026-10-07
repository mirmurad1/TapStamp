import { createBrowserRouter } from 'react-router';

import { CustomerLayout } from './components/CustomerLayout';
import { HomePage } from './pages/HomePage';
import { LegalPage } from './pages/LegalPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { SettingsPage } from './pages/SettingsPage';

export const customerRouter = createBrowserRouter([
  {
    element: <CustomerLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'legal/:slug', element: <LegalPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
