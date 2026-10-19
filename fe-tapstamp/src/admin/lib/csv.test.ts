import { describe, expect, it } from 'vitest';

import { csvCell, toCsv } from './csv';

describe('CSV', () => {
  it('formula kimi başlayan xanalar apostrofla başlayır, ayırıcılar dırnağa alınır', () => {
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`);
    expect(csvCell('+994501234567')).toBe("'+994501234567");
    expect(csvCell('Kafe, Bakı')).toBe('"Kafe, Bakı"');
    expect(csvCell('Mock Coffee')).toBe('Mock Coffee');
    expect(csvCell(42)).toBe('42');
  });

  it('fayl UTF-8 BOM ilə başlayır', async () => {
    const bytes = new Uint8Array(await toCsv([['Şirkət', 'SMS'], ['Kafe', 3]]).arrayBuffer());
    // TextDecoder BOM-u atır, ona görə baytlara baxırıq: EF BB BF
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf]);
    expect(new TextDecoder().decode(bytes)).toBe('Şirkət,SMS\r\nKafe,3\r\n');
  });
});
