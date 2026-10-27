/**
 * Jest (not Vitest) is the required runner. Vite-specific bits are isolated so Jest never sees them:
 *  - import.meta.env lives only in src/lib/env.ts, which tests replace with src/test/envShim.ts
 *  - CSS imports are stubbed
 *  - ts-jest compiles with a CommonJS tsconfig (tsconfig.jest.json)
 */
module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/src/test/setup.ts'],
  testMatch: ['<rootDir>/src/**/*.test.{ts,tsx}'],
  transform: { '^.+\\.tsx?$': ['ts-jest', { tsconfig: '<rootDir>/tsconfig.jest.json' }] },
  moduleNameMapper: {
    '\\.(css)$': 'identity-obj-proxy',
    '.*/lib/env$': '<rootDir>/src/test/envShim.ts',
  },
  collectCoverageFrom: ['src/**/*.{ts,tsx}', '!src/main.tsx', '!src/test/**'],
};
