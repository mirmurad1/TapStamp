import { useTranslation } from 'react-i18next';

const ROLES = ['SUPER_ADMIN', 'COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'] as const;
type Role = (typeof ROLES)[number];

function isRole(value: string): value is Role {
  return (ROLES as readonly string[]).includes(value);
}

export function RoleLabel({ role }: { role: string }) {
  const { t } = useTranslation('admin');
  return <>{isRole(role) ? t(`roles.${role}`) : role}</>;
}
