import {
  BellRing,
  BookOpen,
  Building2,
  ClipboardList,
  LayoutDashboard,
  Medal,
  Megaphone,
  MessageSquareHeart,
  MessageSquareText,
  Nfc,
  Settings,
  ShieldCheck,
  UserPlus,
  UserCog,
  Users,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';

import type { Addon, Feature } from '@/shared/plan/plan';

import type { ContextScope, Me } from '../api/adminApi';

export type AdminSection =
  | 'dashboard'
  | 'customers'
  | 'staffConsole'
  | 'team'
  | 'tags'
  | 'stampCampaigns'
  | 'referrals'
  | 'reminders'
  | 'bonusRules'
  | 'tiers'
  | 'feedback'
  | 'catalog'
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
  /** Bölmə yalnız bu imkan şirkətin paketində olanda açıqdır (menyuda kilid nişanı ilə görünür). */
  feature?: Feature;
  /** Bölmə ayrıca satılan əlavədir: şirkətdə açıq deyilsə menyuda kilid nişanı ilə görünür. */
  addon?: Addon;
  /** Menyuda qrup: əsas iş, marketinq, idarəetmə. */
  group: NavGroup;
}

export const NAV_GROUPS = ["main", "growth", "manage"] as const;
export type NavGroup = (typeof NAV_GROUPS)[number];

const TENANT_ITEMS: readonly NavItem[] = [
  { section: 'dashboard', to: '/', icon: LayoutDashboard, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'], group: 'main' },
  { section: 'customers', to: '/customers', icon: Users, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'], group: 'main' },
  { section: 'staffConsole', to: '/staff-console', icon: ClipboardList, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'], group: 'main' },
  { section: 'feedback', to: '/feedback', icon: MessageSquareHeart, roles: ['COMPANY_OWNER'], feature: 'FEEDBACK', group: 'main' },
  { section: 'catalog', to: '/catalog', icon: BookOpen, roles: ['COMPANY_OWNER'], addon: 'CATALOG', group: 'main' },
  { section: 'campaigns', to: '/campaigns', icon: Megaphone, roles: ['COMPANY_OWNER'], group: 'growth' },
  { section: 'tiers', to: '/tiers', icon: Medal, roles: ['COMPANY_OWNER'], feature: 'TIERS', group: 'growth' },
  { section: 'referrals', to: '/referrals', icon: UserPlus, roles: ['COMPANY_OWNER'], feature: 'REFERRALS', group: 'growth' },
  { section: 'reminders', to: '/reminders', icon: BellRing, roles: ['COMPANY_OWNER'], feature: 'REMINDERS', group: 'growth' },
  { section: 'team', to: '/team', icon: UsersRound, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'], group: 'manage' },
  { section: 'tags', to: '/tags', icon: Nfc, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER'], group: 'manage' },
  { section: 'settings', to: '/settings', icon: Settings, roles: ['COMPANY_OWNER'], group: 'manage' },
  { section: 'audit', to: '/audit', icon: ShieldCheck, roles: ['COMPANY_OWNER'], feature: 'AUDIT_LOG', group: 'manage' },
  { section: 'account', to: '/account', icon: UserCog, roles: ['COMPANY_OWNER', 'BRANCH_MANAGER', 'STAFF'], group: 'manage' },
];

const PLATFORM_ITEMS: readonly NavItem[] = [
  { section: 'tenants', to: '/', icon: Building2, roles: ['SUPER_ADMIN'], group: 'main' },
  { section: 'smsUsage', to: '/sms-usage', icon: MessageSquareText, roles: ['SUPER_ADMIN'], group: 'main' },
  { section: 'campaigns', to: '/platform-campaigns', icon: Megaphone, roles: ['SUPER_ADMIN'], group: 'main' },
  { section: 'audit', to: '/platform-audit', icon: ShieldCheck, roles: ['SUPER_ADMIN'], group: 'main' },
  { section: 'account', to: '/account', icon: UserCog, roles: ['SUPER_ADMIN'], group: 'main' },
];

export function hasAnyRole(me: Me, roles: readonly string[]): boolean {
  return (me.roles ?? []).some((grant) => grant.role !== undefined && roles.includes(grant.role));
}

export function navigationFor(scope: ContextScope, me: Me): NavItem[] {
  const items = scope === 'PLATFORM' ? PLATFORM_ITEMS : TENANT_ITEMS;
  return items.filter((item) => hasAnyRole(me, item.roles));
}
