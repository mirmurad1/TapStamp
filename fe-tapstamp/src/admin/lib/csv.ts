/**
 * Brauzerdə CSV yaratmaq (Excel üçün): UTF-8 BOM (Azərbaycan və rus hərfləri düzgün açılsın), vergüllə ayrılmış
 * sütunlar. Backend-dəki CSV ixracı ilə eyni qoruma: "= + - @", tab və ya CR ilə başlayan mətn xanası apostrofla
 * başlayır ki, Excel onu formula kimi icra etməsin (CSV injection). Rəqəmlər olduğu kimi yazılır.
 */
export function csvCell(value: string | number): string {
  if (typeof value === 'number') {
    return String(value);
  }
  let safe = value;
  if (safe.length > 0 && '=+-@\t\r'.includes(safe.charAt(0))) {
    safe = `'${safe}`;
  }
  if (/[",;\r\n]/.test(safe)) {
    safe = `"${safe.replaceAll('"', '""')}"`;
  }
  return safe;
}

export function toCsv(rows: readonly (readonly (string | number)[])[]): Blob {
  const text = rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
  return new Blob([`\uFEFF${text}\r\n`], { type: 'text/csv;charset=utf-8' });
}
