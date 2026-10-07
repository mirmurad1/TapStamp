/**
 * Service worker-in qeydiyyatı (yalnız prod build-də; dev serverdə Vite-in HMR-i ilə qarışmasın). Admin panelində
 * qeydiyyat edilmir — admin heç vaxt keşlənmir.
 */
export function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) {
    return;
  }
  window.addEventListener('load', () => {
    void navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined);
  });
}
