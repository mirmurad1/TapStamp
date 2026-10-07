// @vitest-environment node
import { readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';

import * as fontkit from 'fontkit';
import { describe, expect, it } from 'vitest';

const require = createRequire(import.meta.url);

const AZERBAIJANI = 'əƏğĞıİöÖüÜşŞçÇ';
const RUSSIAN = 'АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя';
const MANAT = '₼';

/** Paketin bütün alt-dəstlərindəki (latin, latin-ext, cyrillic...) qliflərin birləşməsi. */
function codepoints(packageName: string): Set<number> {
  const filesDir = join(dirname(require.resolve(`${packageName}/package.json`)), 'files');
  const result = new Set<number>();
  for (const file of readdirSync(filesDir).filter((name) => name.endsWith('-normal.woff2'))) {
    const font = fontkit.openSync(join(filesDir, file));
    if ('characterSet' in font) {
      font.characterSet.forEach((cp) => result.add(cp));
    }
  }
  return result;
}

function missing(available: Set<number>, text: string): string[] {
  return [...text].filter((ch) => !available.has(ch.codePointAt(0) ?? 0));
}

describe('şriftlər Azərbaycan və Kiril hərflərini dəstəkləyir', () => {
  it('Inter (mətn və başlıqlar)', () => {
    const inter = codepoints('@fontsource-variable/inter');
    expect(missing(inter, AZERBAIJANI + RUSSIAN + MANAT)).toEqual([]);
  });
});
