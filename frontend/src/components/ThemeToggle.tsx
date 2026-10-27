import { Moon, Sun } from 'lucide-react';
import { useTheme } from '../theme/ThemeProvider';

export function ThemeToggle() {
  const { resolved, toggle } = useTheme();
  const toDark = resolved === 'light';
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={toDark ? 'Switch to dark theme' : 'Switch to light theme'}
      title={toDark ? 'Dark theme' : 'Light theme'}
      className="flex h-9 w-9 items-center justify-center rounded-xl border border-line bg-surface text-muted hover:text-ink"
    >
      {toDark ? <Moon className="h-4 w-4" aria-hidden="true" /> : <Sun className="h-4 w-4" aria-hidden="true" />}
    </button>
  );
}
