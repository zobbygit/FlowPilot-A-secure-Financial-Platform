import { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ButtonLink } from '../components/Button';
import { Logo } from '../components/Logo';
import { ThemeToggle } from '../components/ThemeToggle';
import { Curtain } from './Curtain';
import { useSectionProgress } from './hooks';
import { CalendarPreview, DashboardPreview, HouseholdPreview, PipelineIllustration, PlanPreview, RecordsPreview, RulePreview } from './Previews';
import { SceneHost } from './SceneHost';

function Story({ id, kicker, title, children, preview, flip = false, cta }: { id: string; kicker: string; title: string; children: ReactNode; preview?: ReactNode; flip?: boolean; cta?: { to: string; label: string } }) {
  return (
    <section id={id} aria-labelledby={`${id}-h`} className="relative mx-auto max-w-6xl px-4 py-20 sm:py-28">
      <div className={`grid items-center gap-10 ${preview ? 'lg:grid-cols-2' : ''}`}>
        <div className={flip ? 'lg:order-2' : ''}><p className="mb-3 text-sm font-medium uppercase tracking-[0.2em] text-accent">{kicker}</p><h2 id={`${id}-h`} className="text-3xl font-bold leading-tight tracking-tight sm:text-5xl">{title}</h2>
          <div className="mt-5 max-w-xl space-y-4 text-lg text-muted">{children}</div>{cta && <ButtonLink to={cta.to} variant="secondary" className="mt-7">{cta.label}</ButtonLink>}</div>
        {preview && <div className={flip ? 'lg:order-1' : ''}>{preview}</div>}
      </div>
    </section>
  );
}

const NAV: [string, string][] = [['problem', 'Problem'], ['pipeline', 'Pipeline'], ['records', 'Records'], ['plan', 'Plans'], ['household', 'Households'], ['control', 'Control']];
const FAQ: [string, string][] = [
  ['Does FlowPilot move real money?', 'No. Payments inside FlowPilot are a simulator between FlowPilot users, clearly labelled everywhere. Automation rules only plan allocations; they never transfer funds.'],
  ['Are the documents official?', 'No. Templates, vouchers and receipts are personal records. Every generated document carries a notice that it is not a tax invoice or a legally certified document.'],
  ['Who can see my finances?', 'You. Administrators work with aggregates; reaching a user’s financial summary needs a written reason, expires after 24 hours and is recorded in an append-only audit log.'],
  ['Can I take my data with me?', 'Yes. You can download a full JSON export and delete your account from Settings. Deletion removes your identity; the immutable ledger is kept in de-identified form for financial integrity.'],
  ['Do I need to allow cookies?', 'Only the essential cookies that keep you signed in are used. Optional cookies stay off unless you choose Accept.'],
];

export function LandingPage() {
  const [uniRef, progress] = useSectionProgress<HTMLElement>();
  return (
    <div className="relative bg-bg">
      <Curtain />
      <a href="#story" className="sr-only focus:not-sr-only focus:absolute focus:left-3 focus:top-3 focus:z-[70] focus:rounded-lg focus:bg-surface focus:px-3 focus:py-2">Skip to content</a>

      {/* 1 · The Opening */}
      <header className="relative min-h-screen overflow-hidden">
        <div className="fp-orb pointer-events-none absolute -right-40 top-0 h-[44rem] w-[44rem] rounded-full opacity-60" aria-hidden="true" />
        <nav aria-label="Primary" className="relative z-10 mx-auto flex max-w-6xl items-center justify-between px-4 py-5"><Logo />
          <ul className="hidden items-center gap-6 text-sm text-muted md:flex">{NAV.map(([id, l]) => <li key={id}><a href={`#${id}`} className="hover:text-ink">{l}</a></li>)}</ul>
          <div className="flex items-center gap-2"><ThemeToggle /><ButtonLink to="/user/login" variant="ghost">Log in</ButtonLink><ButtonLink to="/user/register">Get started</ButtonLink></div></nav>
        <div className="relative mx-auto grid min-h-[calc(100vh-5rem)] max-w-6xl items-center gap-6 px-4 pb-16 lg:grid-cols-[1.05fr_1fr]">
          <div><p className="mb-5 text-sm font-medium uppercase tracking-[0.25em] text-accent">Your personal finance command center</p>
            <h1 className="text-5xl font-bold leading-[1.02] tracking-tight sm:text-7xl">One system for <span className="bg-gradient-to-r from-accent to-live bg-clip-text text-transparent">everything money</span> touches.</h1>
            <p className="mt-6 max-w-lg text-lg text-muted">Accounts, receipts, bills, budgets and plans — brought into a single, coherent place you control.</p>
            <div className="mt-9 flex flex-wrap gap-3"><ButtonLink to="/user/register">Create your account</ButtonLink><a href="#story" className="inline-flex items-center rounded-xl border border-line bg-surface px-5 py-2.5 text-sm font-semibold hover:bg-surface-2">See the story ↓</a></div></div>
          <SceneHost kind="hero" className="mx-auto aspect-square w-full max-w-xl" />
        </div>
      </header>

      <main id="story">
        {/* 2 · The Problem */}
        <Story id="problem" kicker="The problem" title="Your money lives in too many places.">
          <p>Balances in one app. Receipts in a camera roll. Bills in an inbox. Plans in a note you can no longer find.</p><p>Every piece makes sense alone. Together, they never quite add up — and you carry the difference in your head.</p></Story>

        {/* 3 · The Turning Point */}
        <Story id="turning" kicker="The turning point" title="Meet FlowPilot: one coherent financial system." flip cta={{ to: '/user/register', label: 'Start with an account' }} preview={<DashboardPreview />}>
          <p>Every account, transaction, receipt, memo, bill and goal lives in one place — recorded in an immutable ledger so the numbers always reconcile.</p></Story>

        {/* 4 · The Money Universe (pinned, scroll-linked, never hijacks scrolling) */}
        <section id="universe" ref={uniRef} aria-labelledby="universe-h" className="relative h-[220vh]">
          <div className="sticky top-0 flex h-screen items-center overflow-hidden">
            <SceneHost kind="universe" progress={progress} className="absolute inset-0" />
            <div className="pointer-events-none relative mx-auto w-full max-w-6xl px-4"><p className="mb-3 text-sm font-medium uppercase tracking-[0.2em] text-accent">The money universe</p>
              <h2 id="universe-h" className="max-w-xl text-3xl font-bold leading-tight tracking-tight sm:text-5xl">Every unit of money, in orbit around you.</h2>
              <p className="mt-5 max-w-md rounded-2xl bg-bg/60 p-3 text-lg text-muted backdrop-blur">Income, spending, bills, savings and shared costs — each ring a part of your financial life, moving together. Keep scrolling to travel inward.</p></div></div></section>

        {/* 5 · The Financial Activity Pipeline */}
        <Story id="pipeline" kicker="The activity pipeline" title="Watch money move through your plan." cta={{ to: '/app/dashboard', label: 'Open the pipeline in your dashboard' }}>
          <p>Income flows past your commitments, budgets and goals before it becomes spending — and what is left is what you can actually use.</p></Story>
        <div className="mx-auto max-w-6xl px-4 pb-20"><PipelineIllustration /><p className="mt-3 text-center text-xs text-muted">Illustration. In the app, every stage shows your own figures and the transactions behind them.</p></div>

        {/* 6 · Command Center */}
        <Story id="command" kicker="Your command center" title="A dashboard that answers “where do I stand?”" flip cta={{ to: '/app/dashboard', label: 'Go to your dashboard' }} preview={<DashboardPreview />}>
          <p>Balance, income, expenses, net saved, cash flow, spending by category, upcoming bills and goals — computed straight from your ledger, in dark or light.</p></Story>

        {/* 7 · Every Record Has a Place */}
        <Story id="records" kicker="Every record has a place" title="Transactions, receipts, memos and templates." cta={{ to: '/app/transactions', label: 'Open transactions' }} preview={<RecordsPreview />}>
          <p>Record a purchase in seconds with a template, attach the receipt, add a memo for later. Mistakes are corrected by reversal, never by silently rewriting history.</p></Story>

        {/* 8 · Money With a Plan */}
        <Story id="plan" kicker="Money with a plan" title="Budgets, goals and a calendar of what’s coming." flip cta={{ to: '/app/budgets', label: 'Plan your budgets' }} preview={<PlanPreview />}>
          <p>Budgets track real spending against limits. Goals count only money you have actually moved. A 30–90 day projection shows where you are heading — with its assumptions spelled out.</p></Story>

        {/* 9 · Rules */}
        <Story id="rules" kicker="Rules that work for you" title="Plan allocations once. Let FlowPilot apply them." cta={{ to: '/app/automation', label: 'Build a rule' }} preview={<RulePreview />}>
          <p>“When income arrives, plan 10% to savings.” Rules reserve money on paper, explain anything they skip, and never overspend — you stay in charge of every real transfer.</p></Story>

        {/* 10 · Households */}
        <Story id="household" kicker="Household finances, together" title="Share the costs. Keep the peace." flip cta={{ to: '/app/households', label: 'Start a household' }} preview={<HouseholdPreview />}>
          <p>Split shared expenses equally, by amount or by percentage, keep a household budget, and see exactly who owes whom with a suggested way to settle up.</p></Story>

        {/* 11 · Calendar */}
        <Story id="calendar" kicker="A calendar for your money" title="Bills, renewals and commitments — before they surprise you." cta={{ to: '/app/calendar', label: 'See your calendar' }} preview={<CalendarPreview />}>
          <p>Subscriptions, due dates and planned contributions sit next to what you have already spent, with reminders ahead of time.</p></Story>

        {/* 12 · Designed for Control */}
        <Story id="control" kicker="Designed for control" title="Private by design. Auditable by default." flip cta={{ to: '/legal/privacy', label: 'Read how we handle data' }}>
          <p>Server-side sessions you can revoke, a permanent audit trail of your financial changes, a full data export and account deletion — all in your hands.</p><ul className="list-disc space-y-1 pl-5 text-base"><li>Append-only audit history you can read</li><li>Device and session control</li><li>Export or delete your data any time</li></ul></Story>

        {/* FAQ */}
        <section id="faq" aria-labelledby="faq-h" className="mx-auto max-w-3xl px-4 py-20"><h2 id="faq-h" className="mb-6 text-3xl font-bold tracking-tight">Questions, answered plainly</h2>
          <div className="divide-y divide-line rounded-2xl border border-line bg-surface">{FAQ.map(([q, a]) => <details key={q} className="group p-4"><summary className="cursor-pointer list-none font-medium">{q}</summary><p className="mt-2 text-muted">{a}</p></details>)}</div></section>

        {/* Final CTA */}
        <section aria-labelledby="cta-h" className="mx-auto max-w-4xl px-4 py-24 text-center"><h2 id="cta-h" className="text-4xl font-bold tracking-tight sm:text-6xl">Bring it all together.</h2><p className="mx-auto mt-4 max-w-md text-lg text-muted">Create your account, add your first account, and see your money as one picture.</p>
          <div className="mt-8 flex flex-wrap justify-center gap-3"><ButtonLink to="/user/register">Get started free</ButtonLink><ButtonLink to="/user/login" variant="secondary">Log in</ButtonLink></div></section>
      </main>



<footer className="border-t border-line bg-surface/60">
  <div className="mx-auto grid max-w-6xl grid-cols-2 gap-8 px-4 py-12 lg:grid-cols-4">
    
    {/* Logo & Description: Full width on mobile, 1 col on desktop */}
    <div className="col-span-2 lg:col-span-1">
      <Logo />
      <p className="mt-3 max-w-xs text-sm text-muted">
        Your personal finance command center. Simulated payments only; documents are personal records.
      </p>
    </div>

    {/* Product: 1 col on mobile */}
    <nav aria-label="Product" className="col-span-1">
      <h3 className="mb-3 text-sm font-semibold">Product</h3>
      <ul className="space-y-2 text-sm text-muted">
        {NAV.map(([id, l]) => (
          <li key={id}><a href={`#${id}`} className="hover:text-ink">{l}</a></li>
        ))}
        <li><a href="#faq" className="hover:text-ink">FAQ</a></li>
      </ul>
    </nav>

    {/* Account: 1 col on mobile */}
    <nav aria-label="Account" className="col-span-1">
      <h3 className="mb-3 text-sm font-semibold">Account</h3>
      <ul className="space-y-2 text-sm text-muted">
        <li><Link to="/user/register" className="hover:text-ink">Create account</Link></li>
        <li><Link to="/user/login" className="hover:text-ink">Log in</Link></li>
        <li><Link to="/user/forgot-password" className="hover:text-ink">Reset password</Link></li>
        <li><Link to="/app/support" className="hover:text-ink">Support (sign in)</Link></li>
        <li><Link to="/admin/login" className="hover:text-ink">Admin Login</Link></li>
      </ul>
    </nav>

    {/* Legal: Full width & centered on mobile, 1 col & left-aligned on desktop */}
    <nav aria-label="Legal" className="col-span-2 flex flex-col items-center text-center lg:col-span-1 lg:items-start lg:text-left">
      <h3 className="mb-3 text-sm font-semibold">Legal</h3>
      <ul className="space-y-2 text-sm text-muted">
        <li><Link to="/legal/privacy" className="hover:text-ink">Privacy policy</Link></li>
        <li><Link to="/legal/cookies" className="hover:text-ink">Cookie policy</Link></li>
        <li><Link to="/legal/terms" className="hover:text-ink">Terms of service</Link></li>
      </ul>
    </nav>
    
  </div>

  <p className="border-t border-line py-5 text-center text-xs text-muted">
    © {new Date().getFullYear()} FlowPilot. All rights reserved.
  </p>
</footer>
    </div>
  );
}
