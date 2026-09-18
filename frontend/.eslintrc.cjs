module.exports = {
  root: true,
  env: { browser: true, es2021: true, node: true },
  plugins: ['@typescript-eslint', 'react-hooks'],
  extends: ['eslint:recommended', 'plugin:react-hooks/recommended'],
  settings: { react: { version: 'detect' } },
  ignorePatterns: ['dist/'],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  overrides: [{
    files: ['**/*.ts', '**/*.tsx'],
    parser: '@typescript-eslint/parser',
    rules: {
      'no-undef': 'off',
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    },
  }],
};
