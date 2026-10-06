// Mövzunu (açıq/qaranlıq) səhifə render olunmamışdan əvvəl tətbiq edir ki, ekranda ağ "flash" görünməsin.
// CSP inline skriptlərə icazə vermədiyi üçün bu, ayrıca statik fayldır.
(function () {
  var preference = 'system';
  try {
    preference = window.localStorage.getItem('cl-theme') || 'system';
  } catch (e) {
    // localStorage əlçatmazdırsa (məxfi rejim), sistem ayarı istifadə olunur
  }
  var dark =
    preference === 'dark' ||
    (preference === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.classList.toggle('dark', dark);
})();
