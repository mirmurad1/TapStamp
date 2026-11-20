/**
 * Başlanğıc siyahısının "Sonra davam et" seçimi: kart bir sətrə kiçilir və bu brauzerdə, səhifələr arasında keçəndə də
 * kiçik qalır. Sistemdən çıxanda və ya sessiya bitəndə (yenidən girişdə) silinir — kart yenə tam açılır.
 */
const KEY = 'tapstamp.setup-collapsed';

export function setupCollapsed(): boolean {
  try {
    return localStorage.getItem(KEY) === '1';
  } catch {
    return false;
  }
}

export function setSetupCollapsed(collapsed: boolean): void {
  try {
    if (collapsed) {
      localStorage.setItem(KEY, '1');
    } else {
      localStorage.removeItem(KEY);
    }
  } catch {
    // Yaddaş bağlıdır — kart növbəti dəfə açıq görünür
  }
}
