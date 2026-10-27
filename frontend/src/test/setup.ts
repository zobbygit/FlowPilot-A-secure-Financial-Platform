import '@testing-library/jest-dom';

// jsdom has no matchMedia. Tests can flip `window.__prefersDark` before rendering.
declare global {
  interface Window { __prefersDark?: boolean }
}
beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.className = '';
  window.__prefersDark = false;
  window.matchMedia = ((query: string) => ({
    matches: query.includes('dark') ? Boolean(window.__prefersDark) : false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
});
