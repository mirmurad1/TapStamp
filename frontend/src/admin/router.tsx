import { createBrowserRouter } from 'react-router';

import { AdminLayout } from './layout/AdminLayout';
import { DashboardPage } from './pages/DashboardPage';
import { PlaceholderPage } from './pages/PlaceholderPage';

export const adminRouter = createBrowserRouter(
  [
    {
      element: <AdminLayout />,
      children: [
        { index: true, element: <DashboardPage /> },
        { path: 'customers/*', element: <PlaceholderPage section="customers" /> },
        { path: 'staff-console/*', element: <PlaceholderPage section="staffConsole" /> },
        { path: 'settings/*', element: <PlaceholderPage section="settings" /> },
        { path: 'audit/*', element: <PlaceholderPage section="audit" /> },
        { path: '*', element: <PlaceholderPage section="dashboard" /> },
      ],
    },
  ],
  { basename: '/admin' },
);
