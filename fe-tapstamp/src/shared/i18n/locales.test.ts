import { describe, expect, it } from 'vitest';

import { SUPPORTED_LANGUAGES, type Language } from './languages';

type Json = { [key: string]: Json } | string;

const files = import.meta.glob<{ default: Record<string, Json> }>('/src/locales/*/*.json', { eager: true });

const PLURAL_SUFFIX = /_(zero|one|two|few|many|other)$/;

function flatten(node: Json, prefix = ''): string[] {
  if (typeof node === 'string') {
    return [prefix];
  }
  return Object.entries(node).flatMap(([key, value]) => flatten(value, prefix ? `${prefix}.${key}` : key));
}

function load(lang: Language): Map<string, string[]> {
  const byNamespace = new Map<string, string[]>();
  for (const [path, module] of Object.entries(files)) {
    const match = /\/src\/locales\/(\w+)\/(\w+)\.json$/.exec(path);
    if (match?.[1] === lang && match[2]) {
      byNamespace.set(match[2], flatten(module.default));
    }
  }
  return byNamespace;
}

/** Cəm suffiksləri çıxarılmış açarlar: "units.stamp_few" → "units.stamp". */
function baseKeys(keys: string[]): Set<string> {
  return new Set(keys.map((key) => key.replace(PLURAL_SUFFIX, '')));
}

const reference = load('az');

describe('tərcümə faylları', () => {
  it('hər dildə eyni namespace-lər var', () => {
    for (const lang of SUPPORTED_LANGUAGES) {
      expect([...load(lang).keys()].sort(), lang).toEqual([...reference.keys()].sort());
    }
  });

  it('hər dildə eyni açarlar var (cəm formaları nəzərə alınmaqla)', () => {
    for (const lang of SUPPORTED_LANGUAGES) {
      for (const [namespace, keys] of load(lang)) {
        const expected = [...baseKeys(reference.get(namespace) ?? [])].sort();
        expect([...baseKeys(keys)].sort(), `${lang}/${namespace}`).toEqual(expected);
      }
    }
  });

  it('cəm açarlarında dilin bütün CLDR kateqoriyaları var', () => {
    for (const lang of SUPPORTED_LANGUAGES) {
      const categories = new Intl.PluralRules(lang).resolvedOptions().pluralCategories;
      for (const [namespace, keys] of load(lang)) {
        const pluralBases = new Set(keys.filter((k) => PLURAL_SUFFIX.test(k)).map((k) => k.replace(PLURAL_SUFFIX, '')));
        for (const base of pluralBases) {
          for (const category of categories) {
            expect(keys, `${lang}/${namespace}: ${base}_${category}`).toContain(`${base}_${category}`);
          }
        }
      }
    }
  });

  it('boş tərcümə yoxdur', () => {
    for (const [path, module] of Object.entries(files)) {
      const check = (node: Json, key: string) => {
        if (typeof node === 'string') {
          expect(node.trim(), `${path}: ${key}`).not.toBe('');
        } else {
          Object.entries(node).forEach(([k, v]) => check(v, `${key}.${k}`));
        }
      };
      check(module.default, '');
    }
  });
});
