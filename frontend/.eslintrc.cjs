// ESLint 8 legacy config (the flat eslint.config.js format is ESLint 9+).
// Baseline is the create-vite react-ts template for these dependency versions.
module.exports = {
  root: true,
  env: { browser: true, es2020: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:react-hooks/recommended',
  ],
  ignorePatterns: ['dist', '.eslintrc.cjs'],
  parser: '@typescript-eslint/parser',
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  plugins: ['react-refresh'],
  rules: {
    // The codebase uses `catch (err: any)` throughout (108 of the 126 hits on
    // first run). Not a correctness signal; re-enable once errors are narrowed
    // from `unknown` instead.
    '@typescript-eslint/no-explicit-any': 'off',

    // Unused parameters are deliberately prefixed with `_` (permissions.ts,
    // CreateAssessment.tsx, ...). Honor that convention.
    '@typescript-eslint/no-unused-vars': [
      'error',
      { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
    ],

    // `while (true) { const { done, value } = await reader.read(); ... }` is the
    // idiomatic streaming-response loop and is the only thing this rule flagged.
    'no-constant-condition': ['error', { checkLoops: false }],

    // Context files export a Provider plus its useX hook from the same module,
    // which this rule rejects. It only affects HMR granularity in dev, not the
    // built app, so it is not worth splitting 11 files over.
    'react-refresh/only-export-components': 'off',
  },
}
