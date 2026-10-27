// import { Activity, ArrowLeftRight, FileText, History, Home, Bell, Megaphone, ScrollText, Users, Eye, SlidersHorizontal, BarChart3, CreditCard, LifeBuoy, FileSearch, Repeat, FlaskConical, Workflow, Send, LayoutTemplate, Receipt, CalendarClock, CalendarDays, FileBarChart, Gauge, LayoutDashboard, PiggyBank, LogOut, Settings, ShieldCheck, Wallet } from 'lucide-react';
// import { Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom';
// import { useAuth } from '../auth/AuthContext';
// import { Button } from '../components/Button';
// import { Logo } from '../components/Logo';
// import { ThemeToggle } from '../components/ThemeToggle';
// import { NotificationBell } from '../components/NotificationBell';
// import { useLiveUpdates } from '../lib/live';
// import type { Role } from '../lib/types';

// /** Separate layouts for the two roles. Only routes that exist are linked (no dead navigation). */
// export function AppShell({ role }: { role: Role }) {
//   const { me, signOut } = useAuth();
//   const navigate = useNavigate();
//   useLiveUpdates(Boolean(me));
//   const nav = role === 'ADMIN'
//     ? [
//         { to: '/admin', label: 'Dashboard', icon: ShieldCheck, end: true },
//         { to: '/admin/users', label: 'Users', icon: Users, end: false },
//         { to: '/admin/payments', label: 'Payment monitoring', icon: CreditCard, end: false },
//         { to: '/admin/support', label: 'Support queue', icon: LifeBuoy, end: false },
//         { to: '/admin/audit', label: 'Audit logs', icon: ScrollText, end: false },
//         { to: '/admin/timeline', label: 'Activity timeline', icon: Activity, end: false },
//         { to: '/admin/oversight', label: 'Oversight', icon: Eye, end: false },
//         { to: '/admin/announcements', label: 'Announcements', icon: Megaphone, end: false },
//         { to: '/admin/reports', label: 'Reports', icon: BarChart3, end: false },
//         { to: '/admin/config', label: 'Configuration', icon: SlidersHorizontal, end: false },
//         { to: '/admin/settings', label: 'Profile & settings', icon: Settings, end: false },
//       ]
//     : [
//         { to: '/app/dashboard', label: 'Dashboard', icon: LayoutDashboard, end: false },
//         { to: '/app/accounts', label: 'Accounts & Wallets', icon: Wallet, end: false },
//         { to: '/app/transactions', label: 'Transactions', icon: ArrowLeftRight, end: false },
//         { to: '/app/templates', label: 'Finance Templates', icon: LayoutTemplate, end: false },
//         { to: '/app/receipts', label: 'Receipts & Memos', icon: Receipt, end: false },
//         { to: '/app/reconcile', label: 'Reconcile Statements', icon: FileSearch, end: false },
//         { to: '/app/recurring', label: 'Recurring Charges', icon: Repeat, end: false },
//         { to: '/app/sandbox', label: 'Planning Sandbox', icon: FlaskConical, end: false },
//         { to: '/app/automation', label: 'Money Automation', icon: Workflow, end: false },
//         { to: '/app/payments', label: 'Payment Simulator', icon: Send, end: false },
//         { to: '/app/budgets', label: 'Budgets', icon: Gauge, end: false },
//         { to: '/app/bills', label: 'Bills & Subscriptions', icon: CalendarClock, end: false },
//         { to: '/app/savings', label: 'Savings & Planning', icon: PiggyBank, end: false },
//         { to: '/app/calendar', label: 'Financial Calendar', icon: CalendarDays, end: false },
//         { to: '/app/reports', label: 'Reports & Exports', icon: FileBarChart, end: false },
//         { to: '/app/households', label: 'Shared Households', icon: Home, end: false },
//         { to: '/app/documents', label: 'Custom Documents', icon: FileText, end: false },
//         { to: '/app/audit', label: 'Audit Trail', icon: History, end: false },
//         { to: '/app/support', label: 'Support', icon: LifeBuoy, end: false },
//         { to: '/app/notifications', label: 'Notifications', icon: Bell, end: false },
//         { to: '/app/settings', label: 'Settings & Profile', icon: Settings, end: false },
//       ];

//   if (role === 'USER' && me && !me.onboardingCompleted) return <Navigate to="/onboarding" replace />;

//   const logout = async () => { await signOut(); navigate(role === 'ADMIN' ? '/admin/login' : '/user/login', { replace: true }); };

//   return (
//     <div className="min-h-screen bg-bg lg:grid lg:grid-cols-[16rem_1fr]">
//       <aside className="border-b border-line bg-surface p-4 lg:sticky lg:top-0 lg:h-screen lg:overflow-y-auto lg:border-b-0 lg:border-r">
//         <div className="mb-4 flex items-center justify-between lg:mb-8"><Logo /><div className="lg:hidden"><ThemeToggle /></div></div>
//         <nav aria-label={role === 'ADMIN' ? 'Admin' : 'Main'} className="flex gap-2 overflow-x-auto lg:flex-col">
//           {nav.map(({ to, label, icon: Icon, end }) => (
//             <NavLink key={to} to={to} end={end}
//               className={({ isActive }) => `flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium ${isActive ? 'bg-accent/15 text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'}`}>
//               <Icon className="h-4 w-4" aria-hidden="true" />{label}
//             </NavLink>
//           ))}
//         </nav>
//       </aside>
//       <div>
//         <header className="flex items-center justify-between gap-3 border-b border-line px-4 py-3 sm:px-8">
//           <p className="truncate text-sm text-muted">{me?.fullName} · {me?.email}</p>
//           <div className="flex items-center gap-2">
//             <NotificationBell to={role === 'ADMIN' ? '/admin/notifications' : '/app/notifications'} />
//             <div className="hidden lg:block"><ThemeToggle /></div>
//             <Button variant="secondary" onClick={logout}><LogOut className="h-4 w-4" aria-hidden="true" />Log out</Button>
//           </div>
//         </header>
//         <main className="p-4 sm:p-8"><Outlet /></main>
//       </div>
//     </div>
//   );
// }










import { Activity, ArrowLeftRight, FileText, History, Home, Bell, Megaphone, ScrollText, Users, Eye, SlidersHorizontal, BarChart3, CreditCard, LifeBuoy, FileSearch, Repeat, FlaskConical, Workflow, Send, LayoutTemplate, Receipt, CalendarClock, CalendarDays, FileBarChart, Gauge, LayoutDashboard, PiggyBank, LogOut, Settings, ShieldCheck, Wallet, Menu, X, UserRoundIcon } from 'lucide-react';
import { useState } from 'react';
import { Navigate, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button } from '../components/Button';
import { Logo } from '../components/Logo';
import { ThemeToggle } from '../components/ThemeToggle';
import { NotificationBell } from '../components/NotificationBell';
import { useLiveUpdates } from '../lib/live';
import type { Role } from '../lib/types';

/** Separate layouts for the two roles. Only routes that exist are linked (no dead navigation). */
export function AppShell({ role }: { role: Role }) {
  const { me, signOut } = useAuth();
  const navigate = useNavigate();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  
  useLiveUpdates(Boolean(me));

  const nav = role === 'ADMIN'
    ? [
        { to: '/admin', label: 'Dashboard', icon: ShieldCheck, end: true },
        { to: '/admin/users', label: 'Users', icon: Users, end: false },
        { to: '/admin/payments', label: 'Payment monitoring', icon: CreditCard, end: false },
        { to: '/admin/support', label: 'Support queue', icon: LifeBuoy, end: false },
        { to: '/admin/audit', label: 'Audit logs', icon: ScrollText, end: false },
        { to: '/admin/timeline', label: 'Activity timeline', icon: Activity, end: false },
        { to: '/admin/oversight', label: 'Oversight', icon: Eye, end: false },
        { to: '/admin/announcements', label: 'Announcements', icon: Megaphone, end: false },
        { to: '/admin/reports', label: 'Reports', icon: BarChart3, end: false },
        { to: '/admin/config', label: 'Configuration', icon: SlidersHorizontal, end: false },
        { to: '/admin/settings', label: 'Profile & settings', icon: Settings, end: false },
      ]
    : [
        { to: '/app/dashboard', label: 'Dashboard', icon: LayoutDashboard, end: false },
        { to: '/app/accounts', label: 'Accounts & Wallets', icon: Wallet, end: false },
        { to: '/app/transactions', label: 'Transactions', icon: ArrowLeftRight, end: false },
        { to: '/app/templates', label: 'Finance Templates', icon: LayoutTemplate, end: false },
        { to: '/app/receipts', label: 'Receipts & Memos', icon: Receipt, end: false },
        { to: '/app/reconcile', label: 'Reconcile Statements', icon: FileSearch, end: false },
        { to: '/app/recurring', label: 'Recurring Charges', icon: Repeat, end: false },
        { to: '/app/sandbox', label: 'Planning Sandbox', icon: FlaskConical, end: false },
        { to: '/app/automation', label: 'Money Automation', icon: Workflow, end: false },
        { to: '/app/payments', label: 'Payment Simulator', icon: Send, end: false },
        { to: '/app/budgets', label: 'Budgets', icon: Gauge, end: false },
        { to: '/app/bills', label: 'Bills & Subscriptions', icon: CalendarClock, end: false },
        { to: '/app/savings', label: 'Savings & Planning', icon: PiggyBank, end: false },
        { to: '/app/calendar', label: 'Financial Calendar', icon: CalendarDays, end: false },
        { to: '/app/reports', label: 'Reports & Exports', icon: FileBarChart, end: false },
        { to: '/app/households', label: 'Shared Households', icon: Home, end: false },
        { to: '/app/documents', label: 'Custom Documents', icon: FileText, end: false },
        { to: '/app/audit', label: 'Audit Trail', icon: History, end: false },
        { to: '/app/support', label: 'Support', icon: LifeBuoy, end: false },
        { to: '/app/notifications', label: 'Notifications', icon: Bell, end: false },
        { to: '/app/settings', label: 'Settings & Profile', icon: Settings, end: false },
      ];

  if (role === 'USER' && me && !me.onboardingCompleted) return <Navigate to="/onboarding" replace />;

  const logout = async () => { await signOut(); navigate(role === 'ADMIN' ? '/admin/login' : '/user/login', { replace: true }); };

  return (
    <div className="min-h-screen bg-bg lg:grid lg:grid-cols-[16rem_1fr]">
      
      {/* SIDEBAR */}
      <aside className={`
        fixed inset-y-0 left-0 z-50 w-64 transform overflow-y-auto bg-surface p-4 pb-20 transition-transform duration-200 ease-in-out 
        ${isMobileMenuOpen ? 'translate-x-0 border-r border-line shadow-2xl' : '-translate-x-full'} 
        lg:static lg:translate-x-0 lg:w-auto lg:border-r lg:border-b-0 lg:border-transparent lg:shadow-none 
        lg:sticky lg:top-0 lg:h-screen
      `}>
        <div className="mb-4 flex items-center justify-between lg:mb-8">
          <Logo />
          <button className="rounded-lg p-2 text-muted hover:bg-surface-2 hover:text-ink lg:hidden" onClick={() => setIsMobileMenuOpen(false)}>
            <X className="h-6 w-6" aria-hidden="true" />
          </button>
        </div>
        
        <nav aria-label={role === 'ADMIN' ? 'Admin' : 'Main'} className="flex flex-col gap-2">
          {nav.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setIsMobileMenuOpen(false)}
              className={({ isActive }) => `flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium ${isActive ? 'bg-accent/15 text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'}`}>
              <Icon className="h-4 w-4" aria-hidden="true" />{label}
            </NavLink>
          ))}
        </nav>
      </aside>

      {/* BACKDROP */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm lg:hidden" onClick={() => setIsMobileMenuOpen(false)} aria-hidden="true" />
      )}

      {/* MAIN CONTENT AREA */}
      <div className="flex min-w-0 flex-col">
        
        {/* ======================================================== */}
        {/* MOBILE HEADER: 2 Rows */}
        {/* ======================================================== */}
        <header className="flex flex-col gap-4 border-b border-line p-4 lg:hidden">
          
          {/* Row 1: Hamburger + Logo + 3 Icons */}
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <button onClick={() => setIsMobileMenuOpen(true)} className="-ml-2 rounded-lg p-2 text-muted hover:bg-surface-2 hover:text-ink">
                <Menu className="h-6 w-6" aria-hidden="true" />
              </button>
              <Logo />
            </div>
            
            <div className="flex shrink-0 items-center gap-1.5 sm:gap-2">
              <NotificationBell to={role === 'ADMIN' ? '/admin/notifications' : '/app/notifications'} />
              <ThemeToggle />
  <button
  type="button"
  onClick={() => navigate(role === 'ADMIN' ? '/admin/settings' : '/app/settings')}
  aria-label="Account settings"
  className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-xl border border-line text-muted transition-colors hover:bg-surface-2 hover:text-ink"
>
  <UserRoundIcon className="h-5 w-5" />
</button>
            </div>
          </div>

          {/* Row 2: Name ONLY + Logout */}
         <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-baseline gap-1.5">
              <span className="text-sm text-muted">Welcome</span>
              <span className="truncate text-sm font-medium text-ink" title={me?.fullName || ""}>
                {me?.fullName}
              </span>
            </div>
            
            <Button variant="secondary" onClick={logout} className="whitespace-nowrap">
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Log out
            </Button>
          </div>
        </header>

        {/* ========================================== */}
        {/* DESKTOP HEADER: Original single-row layout */}
        {/* ========================================== */}
        <header className="hidden items-center justify-between gap-3 border-b border-line px-8 py-3 lg:flex">
          <p
            className="flex min-w-0 items-center gap-1.5 truncate text-sm text-muted"
            title={[me?.fullName, me?.email].filter(Boolean).join(" · ")}
          >
            {me?.fullName && (
              <span className="truncate font-medium text-ink">
                {me.fullName}
              </span>
            )}
            {me?.fullName && me?.email && (
              <span aria-hidden className="opacity-50">·</span>
            )}
            {me?.email && <span className="truncate">{me.email}</span>}
          </p>
          
          <div className="flex shrink-0 items-center gap-2">
            <NotificationBell to={role === 'ADMIN' ? '/admin/notifications' : '/app/notifications'} />
            <ThemeToggle />

<button
  type="button"
  onClick={() => navigate(role === 'ADMIN' ? '/admin/settings' : '/app/settings')}
  aria-label="Account settings"
  className="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-xl border border-line text-muted transition-colors hover:bg-surface-2 hover:text-ink"
>
  {me?.avatarUrl ? (
    <img
      src={me.avatarUrl}
      alt=""
      className="h-full w-full object-cover"
    />
  ) : (
    <UserRoundIcon className="h-5 w-5" />
  )}
</button>
            <Button variant="secondary" onClick={logout} className="whitespace-nowrap">
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Log out
            </Button>
          </div>
        </header>
        
        <main className="flex-1 p-4 sm:p-8"><Outlet /></main>
      </div>
    </div>
  );
}