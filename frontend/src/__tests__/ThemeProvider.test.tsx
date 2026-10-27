import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider, useTheme } from '../theme/ThemeProvider';

function Probe() {
  const { resolved, preference, toggle } = useTheme();
  return <button onClick={toggle}>{`${preference}:${resolved}`}</button>;
}

describe('ThemeProvider', () => {
  it('follows the system preference by default', () => {
    window.__prefersDark = true;
    render(<ThemeProvider><Probe /></ThemeProvider>);
    expect(screen.getByRole('button')).toHaveTextContent('system:dark');
    expect(document.documentElement).toHaveClass('dark');
  });

  it('respects a stored light preference over a dark system', () => {
    window.__prefersDark = true;
    window.localStorage.setItem('fp-theme', 'light');
    render(<ThemeProvider><Probe /></ThemeProvider>);
    expect(screen.getByRole('button')).toHaveTextContent('light:light');
    expect(document.documentElement).not.toHaveClass('dark');
  });

  it('toggles, updates the document class and persists the choice', async () => {
    window.localStorage.setItem('fp-theme', 'light');
    render(<ThemeProvider><Probe /></ThemeProvider>);
    await userEvent.click(screen.getByRole('button'));
    expect(document.documentElement).toHaveClass('dark');
    expect(window.localStorage.getItem('fp-theme')).toBe('dark');
  });

  it('ignores corrupt stored values', () => {
    window.localStorage.setItem('fp-theme', 'neon');
    render(<ThemeProvider><Probe /></ThemeProvider>);
    expect(screen.getByRole('button')).toHaveTextContent('system:');
  });
});
