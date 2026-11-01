import { createBrowserRouter } from 'react-router';

import { CustomerLayout } from './components/CustomerLayout';
import { HomePage } from './pages/HomePage';
import { GiftCardsPage } from './pages/GiftCardsPage';
import { LegalPage } from './pages/LegalPage';
import { LoginPage } from './pages/LoginPage';
import { MenuPage } from './pages/MenuPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ReferralPage } from './pages/ReferralPage';
import { SettingsPage } from './pages/SettingsPage';
import { TapErrorPage, TapPage } from './pages/TapPage';

export const customerRouter = createBrowserRouter([
  {
    element: <CustomerLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'gift-cards', element: <GiftCardsPage /> },
      { path: 'menu', element: <MenuPage /> },
      { path: 'tap/error', element: <TapErrorPage /> },
      { path: 'tap/:token', element: <TapPage /> },
      { path: 'legal/:slug', element: <LegalPage /> },
      { path: 'r/:code', element: <ReferralPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
