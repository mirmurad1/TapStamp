import { describe, expect, it } from 'vitest';

import { initialOf } from './initial';

describe('initialOf', () => {
  it('skips leading digits and symbols', () => {
    expect(initialOf('42 Coffee Roasters', 'az')).toBe('C');
    expect(initialOf('  #1 bərbər', 'az')).toBe('B');
  });

  it('uses locale-aware upper case', () => {
    expect(initialOf('istanbul kafe', 'az')).toBe('İ');
    expect(initialOf('əla', 'az')).toBe('Ə');
  });

  it('falls back to the first character, or empty', () => {
    expect(initialOf('2024', 'az')).toBe('2');
    expect(initialOf(undefined, 'az')).toBe('');
  });
});
