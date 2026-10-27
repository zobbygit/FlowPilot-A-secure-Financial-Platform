import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { CookieBanner } from './components/CookieBanner';
import { AdminAnnouncementsPage } from './pages/admin/AdminAnnouncementsPage';
import { AdminAuditPage } from './pages/admin/AdminAuditPage';
import { AdminConfigPage } from './pages/admin/AdminConfigPage';
import { AdminHome } from './pages/admin/AdminHome';
import { AdminOversightPage } from './pages/admin/AdminOversightPage';
import { AdminPaymentsPage } from './pages/admin/AdminPaymentsPage';
import { AdminReportsPage } from './pages/admin/AdminReportsPage';
import { AdminSettingsPage } from './pages/admin/AdminSettingsPage';
import { AdminSupportPage } from './pages/admin/AdminSupportPage';
import { AdminUsersPage } from './pages/admin/AdminUsersPage';
import { AdminLoginPage } from './pages/admin/AdminLoginPage';
import { AppShell } from './pages/AppShell';
import { LandingPage } from './landing/LandingPage';
import { LegalPage } from './pages/legal/LegalPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { CheckEmailPage } from './pages/user/CheckEmailPage';
import { ForgotPasswordPage } from './pages/user/ForgotPasswordPage';
import { LoginPage } from './pages/user/LoginPage';
import { RegisterPage } from './pages/user/RegisterPage';
import { ResetPasswordPage } from './pages/user/ResetPasswordPage';
import { BillsPage } from './pages/user/BillsPage';
import { BudgetsPage } from './pages/user/BudgetsPage';
import { GoalsPage } from './pages/user/GoalsPage';
import { CalendarPage } from './pages/user/CalendarPage';
import { NotificationsPage } from './pages/user/NotificationsPage';
import { OnboardingPage } from './pages/user/OnboardingPage';
import { ReportsPage } from './pages/user/ReportsPage';
import { ReceiptsPage } from './pages/user/ReceiptsPage';
import { TemplatesPage } from './pages/user/TemplatesPage';
import { PaymentsPage } from './pages/user/PaymentsPage';
import { AutomationPage } from './pages/user/AutomationPage';
import { SandboxPage } from './pages/user/SandboxPage';
import { ReconcilePage } from './pages/user/ReconcilePage';
import { RecurringPage } from './pages/user/RecurringPage';
import { SupportPage } from './pages/user/SupportPage';
import { AuditTrailPage } from './pages/user/AuditTrailPage';
import { DocumentsPage } from './pages/user/DocumentsPage';
import { HouseholdsPage } from './pages/user/HouseholdsPage';
import { AccountsPage } from './pages/user/AccountsPage';
import { DashboardPage } from './pages/user/DashboardPage';
import { TransactionsPage } from './pages/user/TransactionsPage';
import { SettingsPage } from './pages/user/SettingsPage';
import { VerifyEmailPage } from './pages/user/VerifyEmailPage';

export default function App() {
  return (
    <>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/user/register" element={<RegisterPage />} />
        <Route path="/user/check-email" element={<CheckEmailPage />} />
        <Route path="/user/verify-email" element={<VerifyEmailPage />} />
        <Route path="/user/login" element={<LoginPage />} />
        <Route path="/user/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/user/reset-password" element={<ResetPasswordPage />} />
        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route path="/legal/:doc" element={<LegalPage />} />

        <Route element={<ProtectedRoute role="USER" />}>
          <Route path="/onboarding" element={<OnboardingPage />} />
          <Route path="/app" element={<AppShell role="USER" />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="accounts" element={<AccountsPage />} />
            <Route path="transactions" element={<TransactionsPage />} />
            <Route path="budgets" element={<BudgetsPage />} />
            <Route path="bills" element={<BillsPage />} />
            <Route path="savings" element={<GoalsPage />} />
            <Route path="households" element={<HouseholdsPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="audit" element={<AuditTrailPage />} />
            <Route path="support" element={<SupportPage />} />
            <Route path="reconcile" element={<ReconcilePage />} />
            <Route path="recurring" element={<RecurringPage />} />
            <Route path="sandbox" element={<SandboxPage />} />
            <Route path="automation" element={<AutomationPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="templates" element={<TemplatesPage />} />
            <Route path="receipts" element={<ReceiptsPage />} />
            <Route path="calendar" element={<CalendarPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="notifications" element={<NotificationsPage />} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>
        </Route>

        <Route element={<ProtectedRoute role="ADMIN" />}>
          <Route path="/admin" element={<AppShell role="ADMIN" />}>
            <Route index element={<AdminHome />} />
            <Route path="users" element={<AdminUsersPage />} />
            <Route path="payments" element={<AdminPaymentsPage />} />
            <Route path="support" element={<AdminSupportPage />} />
            <Route path="audit" element={<AdminAuditPage />} />
            <Route path="timeline" element={<AdminAuditPage timeline />} />
            <Route path="oversight" element={<AdminOversightPage />} />
            <Route path="announcements" element={<AdminAnnouncementsPage />} />
            <Route path="reports" element={<AdminReportsPage />} />
            <Route path="config" element={<AdminConfigPage />} />
            <Route path="settings" element={<AdminSettingsPage />} />
            <Route path="notifications" element={<NotificationsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      <CookieBanner />
    </>
  );
}
