// Reglas de linter. Ademas de las recomendadas de Angular, aqui se hace
// ejecutable la regla de arquitectura: las dependencias van siempre hacia
// adentro y una funcionalidad no conoce a otra. Una regla que solo esta
// escrita en un documento no se cumple; esta falla el build.

import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';

export default tseslint.config(
  {
    ignores: ['dist/**', 'coverage/**', '.angular/**', 'out-tsc/**', 'playwright-report/**'],
  },
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...tseslint.configs.stylistic,
      ...angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'sastra', style: 'camelCase' },
      ],
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'sastra', style: 'kebab-case' },
      ],
      // Las convenciones del proyecto, como error y no como sugerencia.
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@angular-eslint/prefer-standalone': 'error',
      '@angular-eslint/prefer-signals': 'error',
      '@angular-eslint/prefer-inject': 'error',
      '@angular-eslint/no-developer-preview': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/consistent-type-definitions': ['error', 'interface'],
    },
  },

  // El dominio de una funcionalidad es TypeScript puro: se prueba sin TestBed y
  // sobrevive a un cambio de marco. Si necesita Angular o RxJS, la regla esta
  // en el sitio equivocado.
  {
    files: ['src/app/features/*/domain/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@angular/*', 'rxjs', 'rxjs/*', '@tanstack/*', '@jsverse/*'],
              message:
                'El dominio no conoce ningun marco ni libreria de infraestructura. Si esto hace falta aqui, la logica pertenece a application o a infrastructure.',
            },
          ],
        },
      ],
    },
  },

  // Una funcionalidad no importa de otra. Lo compartido sube a shared o a core.
  {
    files: ['src/app/features/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@features/*', '**/features/*'],
              message:
                'Una funcionalidad no importa de otra. Lo que comparten sube a shared o a core; dentro de la misma funcionalidad se usan rutas relativas.',
            },
          ],
        },
      ],
    },
  },

  // core y shared no pueden depender de una funcionalidad concreta: son la base
  // sobre la que estas se apoyan, y la flecha no se invierte.
  {
    files: ['src/app/core/**/*.ts', 'src/app/shared/**/*.ts'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@features/*', '**/features/*'],
              message:
                'core y shared no conocen ninguna funcionalidad. La dependencia va siempre hacia adentro.',
            },
          ],
        },
      ],
    },
  },

  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {
      // Accesibilidad: es criterio de aceptacion, no una sugerencia.
      '@angular-eslint/template/interactive-supports-focus': 'error',
      '@angular-eslint/template/click-events-have-key-events': 'error',
      '@angular-eslint/template/alt-text': 'error',
      '@angular-eslint/template/label-has-associated-control': 'error',
      '@angular-eslint/template/valid-aria': 'error',
      '@angular-eslint/template/prefer-control-flow': 'error',
    },
  },
);
