import { Monitor, Moon, Sun } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { applyThemePreference, readThemePreference, type ThemePreference } from '@/shared/lib/theme';
import { Button } from '@/shared/ui/button';

const NEXT: Record<ThemePreference, ThemePreference> = { system: 'light', light: 'dark', dark: 'system' };
const ICONS = { light: Sun, dark: Moon, system: Monitor } as const;

/** Mövzunu növbə ilə dəyişir: sistem → açıq → qaranlıq. */
export function ThemeToggle() {
  const { t } = useTranslation('common');
  const [preference, setPreference] = useState<ThemePreference>(readThemePreference);
  const Icon = ICONS[preference];

  const toggle = () => {
    const next = NEXT[preference];
    applyThemePreference(next);
    setPreference(next);
  };

  return (
    <Button
      variant="ghost"
      size="icon"
      onClick={toggle}
      aria-label={`${t('theme.toggle')}: ${t(`theme.${preference}`)}`}
      title={t(`theme.${preference}`)}
    >
      <Icon aria-hidden="true" />
    </Button>
  );
}
