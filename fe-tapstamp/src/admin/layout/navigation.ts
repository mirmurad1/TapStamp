import {
  Building2,
  ClipboardList,
  LayoutDashboard,
  Megaphone,
  MessageSquareText,
  Nfc,
  Settings,
  ShieldCheck,
  Stamp,
  UserCog,
  Users,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';

import type { ContextScope, Me } from '../api/adminApi';

export type AdminSection =
  | 'dashboard'
  | 'customers'
  | 'staffConsole'
  | 'team'
  | 'tags'
  | 'stampCampaigns'
  | 'settings'
  | 'audit'
  | 'tenants'
  | 'smsUsage'
  | 'campaigns'
  | 'account'
  | 'simulator';

type Role = 'SUPER_ADMIN' | 'COMPANY_OWNER' | 'BRANCH_MANAGER' | 'STAFF';

export interface NavItem {
  section: AdminSection;
  to: string;
  icon: LucideIcon;
  /** Bölməni görə bilən rollar (backend də eyni qaydanı yoxlayır — menyu yalnız rahatlıq üçündür). */
  roles: readonly Role[];
}

const TENANT_ITEMS: readonly NavItem[] = [
  { section: 'dashboard', to: '/', icon: LayoutDashboard, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'] },
  { section: 'customers', to: '/customers', icon: Users, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'] },
  { section: 'staffConsole', to: '/staff-console', icon: ClipboardList, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'] },
  { section: 'team', to: '/team', icon: UsersRound, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'] },
  { section: 'tags', to: '/tags', icon: Nfc, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'] },
  { section: 'campaigns', to: '/campaigns', icon: Megaphone, roles: ['COMPANY_OWNER'] },
  { section: 'stampCampaigns', to: '/stamp-campaigns', icon: Stamp, roles: ['COMPANY_OWNER'] },
  { section: 'settings', to: '/settings', icon: Settings, roles: ['COMPANY_OWNER'] },
  { section: 'audit', to: '/audit', icon: ShieldCheck, roles: ['COMPANY_OWNER'] },
  { section: 'account', to: '/account', icon: UserCog, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'] },
];

const PLATFORM_ITEMS: readonly NavItem[] = [
  { section: 'tenants', to: '/', icon: Building2, roles: ['SUPER_ADMIN'] },
  { section: 'smsUsage', to: '/sms-usage', icon: MessageSquareText, roles: ['SUPER_ADMIN'] },
  { section: 'campaigns', to: '/platform-campaigns', icon: Megaphone, roles: ['SUPER_ADMIN'] },
  { section: 'audit', to: '/platform-audit', icon: ShieldCheck, roles: ['SUPER_ADMIN'] },
  { section: 'account', to: '/account', icon: UserCog, roles: ['SUPER_ADMIN'] },
];

export function hasAnyRole(me: Me, roles: readonly string[]): boolean {
  return (me.roles ?? []).some((grant) => grant.role !== undefined && roles.includes(grant.role));
}

export function navigationFor(scope: ContextScope, me: Me): NavItem[] {
  const items = scope === 'PLATFORM' ? PLATFORM_ITEMS : TENANT_ITEMS;
  return items.filter((item) => hasAnyRole(me, item.roles));
}
