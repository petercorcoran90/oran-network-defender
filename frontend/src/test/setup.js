// Registers @testing-library/jest-dom matchers (toBeInTheDocument, etc.) with Vitest's expect.
import '@testing-library/jest-dom/vitest';

// jsdom in this setup doesn't ship a Storage implementation, so components that persist to
// localStorage (e.g. the tweaks panel) would otherwise hit `undefined` in tests. Provide a
// minimal in-memory localStorage when one isn't present.
if (typeof globalThis.localStorage === 'undefined') {
  const store = new Map();
  const memoryStorage = {
    getItem: (key) => (store.has(key) ? store.get(key) : null),
    setItem: (key, value) => { store.set(key, String(value)); },
    removeItem: (key) => { store.delete(key); },
    clear: () => { store.clear(); },
    key: (index) => Array.from(store.keys())[index] ?? null,
    get length() { return store.size; },
  };
  globalThis.localStorage = memoryStorage;
  if (globalThis.window) {
    globalThis.window.localStorage = memoryStorage;
  }
}
