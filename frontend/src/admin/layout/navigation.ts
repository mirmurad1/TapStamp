import { ClipboardList, LayoutDashboard, Settings, ShieldCheck, Users, type LucideIcon } from 'lucide-react';

export type AdminSection = 'dashboard' | 'customers' | 'staffConsole' | 'settings' | 'audit';

export interface NavItem {
  section: AdminSection;
  to: string;
  icon: LucideIcon;
}

export const NAV_ITEMS: readonly NavItem[] = [
  { section: 'dashboard', to: '/', icon: LayoutDashboard },
  { section: 'customers', to: '/customers', icon: Users },
  { section: 'staffConsole', to: '/staff-console', icon: ClipboardList },
  { section: 'settings', to: '/settings', icon: Settings },
  { section: 'audit', to: '/audit', icon: ShieldCheck },
];
