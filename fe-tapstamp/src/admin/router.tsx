import { createBrowserRouter } from 'react-router';

import { RequireActive, RequireState } from './auth/guards';
import { AdminLayout } from './layout/AdminLayout';
import { AuthLayout } from './layout/AuthLayout';
import { AccountPage } from './pages/account/AccountPage';
import { AuditPage } from './pages/audit/AuditPage';
import { CampaignsPage, StampCampaignsPage } from './pages/campaigns/CampaignsPage';
import { CustomerDetailPage } from './pages/customers/CustomerDetailPage';
import { CustomersPage } from './pages/customers/CustomersPage';
import { ForgotPasswordPage } from './pages/auth/ForgotPasswordPage';
import { InvitationPage } from './pages/auth/InvitationPage';
import { LoginPage } from './pages/auth/LoginPage';
import { MfaPage } from './pages/auth/MfaPage';
import { MfaSetupPage } from './pages/auth/MfaSetupPage';
import { ResetPasswordPage } from './pages/auth/ResetPasswordPage';
import { SimulatorPage } from './pages/dev/SimulatorPage';
import { SettingsPage } from './pages/settings/SettingsPage';
import { StaffConsolePage } from './pages/staff/StaffConsolePage';
import { PlatformAuditPage } from './pages/platform/PlatformAuditPage';
import { PlatformCampaignsPage } from './pages/platform/PlatformCampaignsPage';
import { SmsUsagePage } from './pages/platform/SmsUsagePage';
import { ProvisionTenantPage } from './pages/platform/ProvisionTenantPage';
import { TenantDetailPage } from './pages/platform/TenantDetailPage';
import { TagsPage } from './pages/tags/TagsPage';
import { TeamPage } from './pages/team/TeamPage';
import { HomePage, NotFound, RequireRole, RequireScope, RouteError } from './routes/RouteElements';

export const adminRouter = createBrowserRouter(
  [
    {
      errorElement: <RouteError />,
      children: [
        {
          element: <AuthLayout />,
          children: [
            {
              element: <RequireState allowed={['NONE']} />,
              children: [
                { path: 'login', element: <LoginPage /> },
                { path: 'forgot-password', element: <ForgotPasswordPage /> },
              ],
            },
            // Emaildəki linklər istənilən vəziyyətdə açılır (məs. başqa hesabla daxil olunubsa da)
            { path: 'reset-password', element: <ResetPasswordPage /> },
            { path: 'invitation', element: <InvitationPage /> },
            { element: <RequireState allowed={['PENDING_MFA']} />, children: [{ path: 'mfa', element: <MfaPage /> }] },
            {
              element: <RequireState allowed={['MFA_ENROLLMENT', 'ACTIVE']} />,
              children: [{ path: 'mfa/setup', element: <MfaSetupPage /> }],
            },
          ],
        },
        {
          element: <RequireActive />,
          children: [
            {
              element: <AdminLayout />,
              children: [
                { index: true, element: <HomePage /> },
                { path: 'account', element: <AccountPage /> },
                {
                  element: <RequireScope scope="TENANT" />,
                  children: [
                    {
                      element: <RequireRole roles={['COMPANY_OWNER', 'BRANCH_MANAGER']} />,
                      children: [
                        { path: 'team', element: <TeamPage /> },
                        { path: 'customers', element: <CustomersPage /> },
                        { path: 'customers/:customerId', element: <CustomerDetailPage /> },
                        { path: 'tags', element: <TagsPage /> },
                      ],
                    },
                    {
                      element: <RequireRole roles={['COMPANY_OWNER']} />,
                      children: [
                        { path: 'settings/*', element: <SettingsPage /> },
                        { path: 'audit', element: <AuditPage /> },
                        { path: 'campaigns', element: <CampaignsPage /> },
                        { path: 'stamp-campaigns', element: <StampCampaignsPage /> },
                      ],
                    },
                    { path: 'staff-console/*', element: <StaffConsolePage /> },
                    { path: 'dev/simulator', element: <SimulatorPage /> },
                  ],
                },
                {
                  element: <RequireScope scope="PLATFORM" />,
                  children: [
                    { path: 'tenants/new', element: <ProvisionTenantPage /> },
                    { path: 'tenants/:tenantId', element: <TenantDetailPage /> },
                    { path: 'platform-audit', element: <PlatformAuditPage /> },
                    { path: 'sms-usage', element: <SmsUsagePage /> },
                    { path: 'platform-campaigns', element: <PlatformCampaignsPage /> },
                  ],
                },
                { path: '*', element: <NotFound /> },
              ],
            },
          ],
        },
      ],
    },
  ],
  { basename: '/admin' },
);
