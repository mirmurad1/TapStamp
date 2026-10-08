/**
 * jsdom HTTP səhifədə {@code __Host-} prefiksli (Secure) cookie-ləri qəbul etmir — bu, brauzerin düzgün davranışıdır.
 * Testlərdə document.cookie sadə yaddaş anbarı ilə əvəz olunur.
 */
export function installCookieJar(initial: Record<string, string> = {}): () => void {
  const jar = new Map(Object.entries(initial));
  const original = Object.getOwnPropertyDescriptor(Document.prototype, 'cookie');
  Object.defineProperty(document, 'cookie', {
    configurable: true,
    get: () => [...jar].map(([name, value]) => `${name}=${value}`).join('; '),
    set: (value: string) => {
      const [pair = '', ...attributes] = value.split(';');
      const index = pair.indexOf('=');
      const name = pair.slice(0, index).trim();
      const cookieValue = pair.slice(index + 1).trim();
      const expired = attributes.some((attribute) => /^\s*max-age=0\s*$/i.test(attribute));
      if (expired || cookieValue === '') {
        jar.delete(name);
      } else {
        jar.set(name, cookieValue);
      }
    },
  });
  return () => {
    delete (document as unknown as Record<string, unknown>).cookie;
    if (original) {
      Object.defineProperty(Document.prototype, 'cookie', original);
    }
  };
}
