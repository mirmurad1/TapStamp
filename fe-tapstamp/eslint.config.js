import js from '@eslint/js';
import { defineConfig, globalIgnores } from 'eslint/config';
import i18next from 'eslint-plugin-i18next';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default defineConfig([
  globalIgnores(['dist', 'coverage', 'node_modules']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2023,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
      // Kontekst provayderi ilə birlikdə ixrac olunan hook-lar (standart React nümunəsi)
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true, allowExportNames: ['useAdminAuth', 'useMe', 'useStepUp', 'useCustomerSession', 'useConfirm'] },
      ],
      // React avtomatik escape edir; xam HTML yalnız sanitizasiya ilə və heç vaxt istifadəçi məzmunu üçün
      'no-restricted-syntax': [
        'error',
        {
          selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
          message: 'dangerouslySetInnerHTML qadağandır (XSS riski).',
        },
      ],
    },
  },
  {
    // UI-da sərt yazılmış mətn qadağandır: hər şey tərcümə fayllarından gəlməlidir
    files: ['src/**/*.tsx'],
    ignores: ['src/**/*.test.tsx', 'src/test/**'],
    plugins: { i18next },
    rules: {
      'i18next/no-literal-string': [
        'error',
        {
          mode: 'jsx-only',
          'jsx-attributes': { include: ['alt', 'aria-label', 'title', 'placeholder', 'label'] },
          // Standart siyahı + react-hook-form metodları (sahə adları UI mətni deyil)
          callees: {
            exclude: [
              'i18n(ext)?',
              't',
              'require',
              'addEventListener',
              'removeEventListener',
              'postMessage',
              'getElementById',
              'dispatch',
              'commit',
              'includes',
              'indexOf',
              'endsWith',
              'startsWith',
              'register',
              'watch',
              'getValues',
              'resetField',
              'setValue',
              'setError',
              'clearErrors',
              'trigger',
            ],
          },
        },
      ],
    },
  },
]);
