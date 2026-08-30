// ESLint flat config.
//
// Purpose: this config is an accessibility regression net, not a general style
// gate. The rules that matter here are the ones in
// @angular-eslint/eslint-plugin-template's accessibility preset - in flat config
// that is `angular.configs.templateAccessibility` (the eslintrc name was
// "plugin:@angular-eslint/template/accessibility"). It contributes alt-text,
// elements-content, label-has-associated-control, valid-aria,
// interactive-supports-focus, click-events-have-key-events, table-scope and the
// rest of the a11y set.
//
//   npm run lint        # everything, including the advisory warnings below
//   npm run lint:a11y   # templates only, errors only - this is the CI gate
//
// Why the split: every template in src/ passes the a11y rules today, so they can
// gate CI immediately. The general TypeScript rules below, by contrast, flag
// ~117 pre-existing findings (no-explicit-any, prefer-inject, OnPush) that are
// style debt unrelated to accessibility. They are kept visible as warnings
// rather than switched off - turning them into errors would make CI red for
// reasons that have nothing to do with the net this config exists to provide.
//
// `processInlineTemplates` is what extracts inline `template:` strings so the
// template rules see them too. There are no inline templates in src/ right now;
// the processor is here so the first one added is covered without a config change.

const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      '.angular/**',
      'coverage/**',
      'eslint.config.js',
      'karma.conf.js',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'app', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'app', style: 'kebab-case' },
      ],

      // Advisory only - see the note at the top of the file.
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': 'warn',
      '@angular-eslint/prefer-inject': 'warn',
      '@angular-eslint/prefer-on-push-component-change-detection': 'warn',
      'no-irregular-whitespace': 'warn',
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
  },
);
