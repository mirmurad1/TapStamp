import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, LogOut, UserCog } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { cn } from '@/shared/lib/cn';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu';

import { authApi } from '../api/adminApi';
import { useAdminAuth, useMe } from '../auth/AdminAuthProvider';
import { clearUserData } from '../auth/session';
import { RoleLabel } from '../components/RoleLabel';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toLocaleUpperCase() ?? '')
    .join('');
}

/** İstifadəçi menyusu: ad, rol, hesab parametrləri və çıxış. */
/** {@code className} — düymənin ölçüsü (məs. menyunun altında tam en); {@code side} — menyunun açılma istiqaməti. */
export function UserMenu({ className, side = 'bottom' }: { className?: string; side?: 'top' | 'bottom' } = {}) {
  const { t } = useTranslation('admin');
  const { realm } = useAdminAuth();
  const me = useMe();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logout = useMutation({
    mutationFn: () => authApi.logout(realm),
    onSettled: () => {
      clearUserData(queryClient);
      void navigate('/login', { replace: true, state: { signedOut: true } });
    },
  });
  const name = me.displayName || me.email || '';
  const primaryRole = me.roles?.[0]?.role;

  return (
    // modal={false}: menyu açıq olanda səhifənin qalan hissəsi kilidlənmir (Radix-in sürüşmə kilidi tərtibatı pozmur)
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger
        className={cn(
          'flex min-h-11 items-center gap-2 rounded-full border border-border bg-card py-1 pl-1 pr-3 text-sm shadow-sm hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          className,
        )}
        aria-label={t('userMenu.label')}
      >
        <span className="grid size-8 place-items-center rounded-full bg-primary text-xs font-semibold text-primary-foreground">
          {initials(name)}
        </span>
        <span className="hidden min-w-0 flex-1 truncate text-left font-medium sm:inline">{name}</span>
        <ChevronDown className="size-4 text-muted-foreground" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align={side === 'top' ? 'start' : 'end'} side={side}>
        <DropdownMenuLabel>
          <p className="truncate font-medium">{name}</p>
          <p className="truncate text-xs text-muted-foreground">{me.email}</p>
          {primaryRole ? (
            <p className="mt-1 text-xs text-muted-foreground">
              <RoleLabel role={primaryRole} />
            </p>
          ) : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void navigate('/account')}>
          <UserCog aria-hidden="true" />
          {t('userMenu.account')}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => logout.mutate()} disabled={logout.isPending}>
          <LogOut aria-hidden="true" />
          {t('userMenu.signOut')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
