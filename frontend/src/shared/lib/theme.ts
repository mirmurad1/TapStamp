export type ThemePreference = 'light' | 'dark' | 'system';

/** Mövzu yalnız bu cihazın rahatlığı üçün saxlanılır; əlçatmazdırsa sistem ayarı işlədilir. */
const STORAGE_KEY = 'cl-theme';

export function readThemePreference(): ThemePreference {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY);
    return value === 'light' || value === 'dark' ? value : 'system';
  } catch {
    return 'system';
  }
}

export function applyThemePreference(preference: ThemePreference): void {
  try {
    if (preference === 'system') {
      window.localStorage.removeItem(STORAGE_KEY);
    } else {
      window.localStorage.setItem(STORAGE_KEY, preference);
    }
  } catch {
    // Saxlamaq mümkün deyilsə, seçim yalnız bu səhifə üçün qüvvədədir
  }
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = preference === 'dark' || (preference === 'system' && prefersDark);
  document.documentElement.classList.toggle('dark', dark);
}
