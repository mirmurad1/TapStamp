import { describe, expect, it } from 'vitest';

import { takeFragmentToken } from './fragmentToken';

describe('takeFragmentToken', () => {
  it('tokeni fraqmentdən götürür və ünvan sətrindən silir', () => {
    window.history.replaceState(null, '', '/admin/reset-password#token=abc_DEF-123');
    expect(takeFragmentToken()).toBe('abc_DEF-123');
    expect(window.location.hash).toBe('');
    // StrictMode-da ikinci çağırış eyni tokeni qaytarır
    expect(takeFragmentToken()).toBe('abc_DEF-123');
  });

  it('başqa səhifədə köhnə tokeni qaytarmır', () => {
    window.history.replaceState(null, '', '/admin/invitation');
    expect(takeFragmentToken()).toBeUndefined();
  });
});
