/** @type {import('ts-jest').JestConfigWithTsJest} */
module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  testMatch: ["<rootDir>/src/**/*.test.ts"],
  transform: {
    // ts-jest forces module: CommonJS for Jest's require() runtime, which is incompatible
    // with tsconfig.json's moduleResolution: "Bundler" (paired for the real ESM build) —
    // that mismatch breaks type inference through zustand's conditional package exports.
    // Override to "node10" resolution for tests only; the actual build (tsc) is unaffected.
    "^.+\\.ts$": ["ts-jest", { tsconfig: { moduleResolution: "node10" } }],
  },
};
