import { createBrowserRouter } from 'react-router';

import { CustomerLayout } from './components/CustomerLayout';
import { HomePage } from './pages/HomePage';
import { NotFoundPage } from './pages/NotFoundPage';

export const customerRouter = createBrowserRouter([
  {
    element: <CustomerLayout />,
    children: [
      { index: true, element: <HomePage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
