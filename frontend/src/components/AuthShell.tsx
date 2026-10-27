import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Logo } from './Logo';
import { ThemeToggle } from './ThemeToggle';

/** Two-panel authentication layout: form on the left, brand scene on the right (hidden on small screens). */
export function AuthShell({ title, subtitle, children, aside }: { title: string; subtitle?: string; children: ReactNode; aside?: ReactNode }) {
  return (
    <main className="relative min-h-screen overflow-hidden bg-bg">
      <div className="fp-orb pointer-events-none absolute -left-40 -top-40 h-[32rem] w-[32rem] rounded-full opacity-60" aria-hidden="true" />
      <div className="relative mx-auto grid min-h-screen max-w-6xl items-center gap-8 px-4 py-8 lg:grid-cols-2">
        <section className="rounded-3xl border border-line bg-surface/90 p-6 shadow-glow backdrop-blur sm:p-10">
          <div className="mb-8 flex items-center justify-between">
            <Link to="/" aria-label="FlowPilot home"><Logo /></Link>
            <ThemeToggle />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
          {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </section>
        <aside className="hidden lg:block" aria-hidden={aside ? undefined : true}>
          {aside ?? (
            <div className="relative mx-auto aspect-square w-4/5 max-w-md">
              <div className="fp-orb fp-float absolute inset-0 rounded-full" />
              <div className="absolute inset-[22%] rounded-3xl border border-line bg-surface/70 backdrop-blur" />
            </div>
          )}
        </aside>
      </div>
    </main>
  );
}
