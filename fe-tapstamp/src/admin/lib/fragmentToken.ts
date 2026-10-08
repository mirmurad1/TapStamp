let taken: { path: string; token: string } | undefined;

/**
 * Emaildəki linkin tokeni URL fraqmentindədir ({@code #token=...}): fraqment serverə göndərilmir, loglara və
 * Referer başlığına düşmür. Oxunandan sonra ünvan sətrindən silinir ki, brauzer tarixçəsində qalmasın.
 * Təkrar çağırış (məs. React StrictMode-da) eyni səhifə üçün artıq götürülmüş tokeni qaytarır.
 */
export function takeFragmentToken(location: Location = window.location): string | undefined {
  const token = new URLSearchParams(location.hash.replace(/^#/, '')).get('token');
  if (token) {
    taken = { path: location.pathname, token };
    window.history.replaceState(window.history.state, '', location.pathname + location.search);
    return token;
  }
  return taken?.path === location.pathname ? taken.token : undefined;
}
