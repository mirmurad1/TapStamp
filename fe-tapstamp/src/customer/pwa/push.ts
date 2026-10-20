import { pushApi } from '../api/customerApi';

/**
 * Web push (brauzer bildirişləri). Abunə service worker vasitəsilədir: brauzer push xidmətində ünvan yaradır, server
 * mesajı oraya göndərir, service worker bildirişi göstərir.
 *
 * - "unsupported" — brauzer push-u dəstəkləmir;
 * - "needs-install" — iPhone/iPad: push yalnız ana ekrana əlavə olunmuş tətbiqdə işləyir (iOS 16.4+);
 * - "supported" — icazə istənə bilər.
 */
export type PushSupport = 'unsupported' | 'needs-install' | 'supported';

function isIos(): boolean {
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

function isStandalone(): boolean {
  return (
    Boolean(window.matchMedia?.('(display-mode: standalone)').matches) ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}

export function pushSupport(): PushSupport {
  if (isIos() && !isStandalone()) {
    return 'needs-install';
  }
  const supported = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  return supported ? 'supported' : 'unsupported';
}

export function notificationPermission(): NotificationPermission {
  return 'Notification' in window ? Notification.permission : 'denied';
}

/** VAPID açıq açarı (base64url) → abunə üçün bayt massivi. */
function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = (base64url + '='.repeat((4 - (base64url.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

/** Service worker qeydiyyatı (prod-da tətbiq açılanda artıq olur; dev-də — burada). */
async function registration(): Promise<ServiceWorkerRegistration> {
  const existing = await navigator.serviceWorker.getRegistration('/');
  if (!existing) {
    await navigator.serviceWorker.register('/sw.js', { scope: '/' });
  }
  return navigator.serviceWorker.ready;
}

export async function currentSubscription(): Promise<PushSubscription | null> {
  if (pushSupport() !== 'supported') {
    return null;
  }
  const existing = await navigator.serviceWorker.getRegistration('/');
  return existing ? existing.pushManager.getSubscription() : null;
}

/** İcazə istəyir (yalnız istifadəçinin kliki ilə çağırılmalıdır) və cihazı serverdə qeyd edir. */
export async function enablePush(publicKey: string): Promise<boolean> {
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    return false;
  }
  const reg = await registration();
  const subscription =
    (await reg.pushManager.getSubscription()) ??
    (await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(publicKey) }));
  const json = subscription.toJSON();
  await pushApi.subscribe({
    endpoint: json.endpoint ?? subscription.endpoint,
    keys: { p256dh: json.keys?.p256dh ?? '', auth: json.keys?.auth ?? '' },
  });
  return true;
}

/** Bu cihazda bildirişləri söndürür: serverdən silinir, brauzerdəki abunə ləğv olunur. */
export async function disablePush(): Promise<void> {
  const subscription = await currentSubscription();
  if (!subscription) {
    return;
  }
  await pushApi.unsubscribe(subscription.endpoint).catch(() => undefined);
  await subscription.unsubscribe().catch(() => false);
}
