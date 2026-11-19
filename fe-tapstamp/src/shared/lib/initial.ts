/**
 * İkon üçün adın baş hərfi: adın ilk HƏRFİ götürülür ("42 Coffee Roasters" → "C"), rəqəm və işarələr ötürülür.
 * Adda hərf yoxdursa — ilk simvol; ad boşdursa — boş sətir.
 */
export function initialOf(name: string | undefined, locale: string): string {
  const trimmed = (name ?? '').trim();
  const letter = /\p{L}/u.exec(trimmed)?.[0] ?? trimmed.charAt(0);
  return letter.toLocaleUpperCase(locale);
}
