import { useEffect, useState } from 'react';
import { Logo } from '../components/Logo';
import { prefersReducedMotion } from './hooks';

const KEY = 'fp-curtain-seen';

/**
 * Horizontal curtain-opening reveal. It plays at most once per browser session, is skipped entirely for reduced-motion
 * users, can always be skipped with a visible button (or Escape), removes itself after ~2.4s, and never captures pointer
 * events once it starts opening - the app is always reachable.
 */
export function Curtain() {
  const seen = (() => { try { return sessionStorage.getItem(KEY) === '1'; } catch { return true; } })();
  const [phase, setPhase] = useState<'closed' | 'opening' | 'gone'>(seen || prefersReducedMotion() ? 'gone' : 'closed');

  useEffect(() => {
    if (phase === 'gone') return;
    const t1 = setTimeout(() => setPhase('opening'), 700), t2 = setTimeout(() => finish(), 2400);
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') finish(); };
    window.addEventListener('keydown', onKey);
    return () => { clearTimeout(t1); clearTimeout(t2); window.removeEventListener('keydown', onKey); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function finish() { try { sessionStorage.setItem(KEY, '1'); } catch { /* ignore */ } setPhase('gone'); }
  if (phase === 'gone') return null;
  return (
    <div className={`fixed inset-0 z-[60] ${phase === 'opening' ? 'pointer-events-none' : ''}`} role="presentation">
      <div className={`absolute inset-y-0 left-0 w-1/2 bg-bg transition-transform duration-[1400ms] ease-[cubic-bezier(.77,0,.18,1)] ${phase === 'opening' ? '-translate-x-full' : ''}`}><div className="absolute inset-y-0 right-0 w-px bg-accent/60" /></div>
      <div className={`absolute inset-y-0 right-0 w-1/2 bg-bg transition-transform duration-[1400ms] ease-[cubic-bezier(.77,0,.18,1)] ${phase === 'opening' ? 'translate-x-full' : ''}`}><div className="absolute inset-y-0 left-0 w-px bg-accent/60" /></div>
      <div className={`absolute inset-0 flex items-center justify-center transition-opacity duration-500 ${phase === 'opening' ? 'opacity-0' : 'opacity-100'}`}><Logo className="text-3xl" /></div>
      <button type="button" onClick={finish} className="pointer-events-auto absolute bottom-6 right-6 rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-muted hover:text-ink">Skip intro</button>
    </div>
  );
}
