export type Role = 'USER' | 'ADMIN';

export interface Me {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  emailVerified: boolean;
  avatarUrl: string | null;
  themePreference: 'light' | 'dark' | 'system';
  onboardingCompleted: boolean;
}

export interface SessionView {
  id: string;
  current: boolean;
  userAgent: string | null;
  ipAddress: string | null;
  createdAt: string;
  lastActiveAt: string;
  expiresAt: string;
  status: 'ACTIVE' | 'REVOKED' | 'EXPIRED';
}

export type AccountType = 'CASH' | 'BANK' | 'SAVINGS' | 'WALLET';
export type TxType = 'INCOME' | 'EXPENSE' | 'TRANSFER' | 'REFUND' | 'ADJUSTMENT' | 'OPENING_BALANCE';

/** Money is a decimal STRING end to end; convert to Number only for display/drawing. */
export interface AccountView { id: string; name: string; type: AccountType; currency: string; currentBalance: string; allowNegative: boolean }
export interface CategoryView { id: string; name: string; kind: 'INCOME' | 'EXPENSE'; color: string | null }
export interface TransactionView {
  id: string; type: TxType; status: 'POSTED' | 'REVERSED'; occurredOn: string; description: string | null; merchant: string | null;
  categoryId: string | null; categoryName: string | null; amount: string; currency: string;
  accountName: string | null; toAccountName: string | null; reversal: boolean; reversible: boolean;
}
export interface PageView<T> { items: T[]; page: number; size: number; total: number }
export interface Totals { income: string; expenses: string; net: string; incomeChangePct: number | null; expenseChangePct: number | null; netChangePct: number | null }
export interface MonthFlow { month: string; income: string; expenses: string }
export interface CategorySlice { categoryId: string | null; name: string; color: string | null; amount: string; percent: number }
export interface DashboardView {
  currency: string; from: string; to: string; totalBalance: string; totals: Totals; cashFlow: MonthFlow[];
  spending: CategorySlice[]; recent: TransactionView[]; accounts: AccountView[];
}

export interface BudgetView {
  id: string; name: string; periodType: 'MONTHLY' | 'CUSTOM'; periodStart: string; periodEnd: string; categoryId: string | null; categoryName: string | null;
  limit: string; currency: string; rollover: boolean; thresholds: number[]; carriedOver: string; spent: string; remaining: string;
  percentUsed: number; state: 'OK' | 'WARNING' | 'EXCEEDED'; crossedThresholds: number[];
}
export type BillStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'NEEDS_REVIEW';
export type Frequency = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY';
export interface BillView {
  id: string; name: string; kind: 'BILL' | 'SUBSCRIPTION'; amount: string; currency: string; frequency: Frequency; nextDueDate: string; status: BillStatus;
  categoryId: string | null; accountId: string | null; reminderDaysBefore: number; notes: string | null; lastPaidOn: string | null;
  daysUntilDue: number; reminderDue: boolean; overdue: boolean; monthlyEstimate: string; previousAmount: string | null;
}
export interface BillsOverview { bills: BillView[]; estimates: { currency: string; monthly: string; annual: string }[] }
export interface GoalView {
  id: string; name: string; goalType: 'EMERGENCY' | 'TRAVEL' | 'GADGET' | 'OTHER'; targetAmount: string; targetDate: string | null; currency: string;
  contributionAmount: string | null; contributionFrequency: 'WEEKLY' | 'MONTHLY' | null; status: 'ACTIVE' | 'COMPLETED' | 'ARCHIVED';
  saved: string; planned: string; percentSaved: number; remaining: string; suggestedMonthly: string | null;
}

export interface NotificationView { id: string; type: string; title: string; body: string | null; link: string | null; read: boolean; createdAt: string }
export interface NotificationPage { items: NotificationView[]; unread: number; page: number; size: number; total: number }
export interface NotificationPrefs { bills: boolean; budgets: boolean; lowBalance: boolean; payments: boolean; automation: boolean; support: boolean; lowBalanceThreshold: string | null }
export interface ProfileView {
  email: string; fullName: string; preferredCurrency: string; timezone: string; locale: string; themePreference: 'light' | 'dark' | 'system';
  numberFormat: string; dateFormat: string; financialGoals: string[]; avatarUrl: string | null; onboardingCompleted: boolean;
}
export interface CategoryManageView { id: string; name: string; kind: 'INCOME' | 'EXPENSE'; color: string | null; sortOrder: number; archived: boolean; builtIn: boolean; usage: number }
export interface CalendarEvent { date: string; kind: string; title: string; amount: string; currency: string; state: 'PLANNED' | 'COMPLETED'; link: string }
export interface ReportTable { type: string; title: string; currency: string; from: string | null; to: string | null; columns: string[]; rows: string[][]; notes: string[] }

export interface ReceiptView {
  id: string; transactionId: string | null; fileId: string; receiptNumber: string | null; merchant: string | null; subtotal: string | null; tax: string | null; total: string | null;
  currency: string | null; reviewStatus: 'UNREVIEWED' | 'VERIFIED' | 'NEEDS_REVIEW'; mimeType: string; fileName: string; sizeBytes: number; createdAt: string;
  history: { fromStatus: string | null; toStatus: string; note: string | null; changedAt: string }[];
}
export interface MemoView { id: string; title: string; body: string | null; tags: string[]; pinned: boolean; followUpDate: string | null; transactionId: string | null; goalId: string | null; updatedAt: string }

export type FieldType = 'TEXT' | 'AMOUNT' | 'NUMBER' | 'DATE' | 'DROPDOWN' | 'CHECKBOX' | 'TAGS' | 'FILE' | 'CATEGORY' | 'ACCOUNT' | 'TRANSACTION';
export interface FieldDef { key: string; label: string; type: FieldType; binding: string; required: boolean; options: string | null; defaultValue: string | null; min: number | string | null; max: number | string | null }
export interface TemplateView { id: string; name: string; description: string | null; targetType: 'TRANSACTION' | 'MEMO' | 'BILL'; fixedType: 'INCOME' | 'EXPENSE' | null; builtIn: boolean; archived: boolean; fields: FieldDef[] }
export interface TemplateRequest { name: string; description: string | null; targetType: TemplateView['targetType']; fixedType: 'INCOME' | 'EXPENSE' | null; fields: FieldDef[] }

export interface PaymentProfile { handle: string; receiveAccountId: string | null; simulated: boolean }
export interface PaymentView {
  id: string; reference: string; direction: 'SENT' | 'RECEIVED'; counterpartyName: string; counterpartyHandle: string | null; amount: string; currency: string; note: string | null;
  status: 'PENDING' | 'SUCCESSFUL' | 'FAILED' | 'CANCELLED' | 'REFUNDED'; failureReason: string | null; requestReference: string | null;
  canConfirm: boolean; canCancel: boolean; canRefund: boolean; canDispute: boolean; disputeStatus: string | null; createdAt: string; updatedAt: string; simulated: boolean;
}
export interface PaymentDetail { payment: PaymentView; events: { fromStatus: string | null; toStatus: string; note: string | null; createdAt: string }[]; refund: { amount: string; reason: string | null; createdAt: string } | null }
export interface PaymentRequestView { id: string; reference: string; direction: 'CREATED' | 'INCOMING'; counterpartyName: string; amount: string; currency: string; note: string | null; status: 'OPEN' | 'PAID' | 'CANCELLED' | 'EXPIRED'; expiresAt: string; createdAt: string }

export interface PipelineStage { id: string; title: string; amount: string; secondary: string | null; explanation: string; liveActivity: boolean; related: { id: string; date: string; title: string; amount: string }[] }
export interface PipelineView { currency: string; from: string; to: string; stages: PipelineStage[] }

export interface ActionDef { targetType: 'GOAL' | 'BILL' | 'BUDGET'; targetId: string; amountType: 'FIXED' | 'PERCENT'; amountValue: string | number }
export interface RuleView { id: string; name: string; status: 'ACTIVE' | 'PAUSED' | 'ARCHIVED'; triggerType: 'INCOME_RECEIVED' | 'SCHEDULE' | 'MANUAL'; schedule: 'WEEKLY' | 'MONTHLY' | null; scheduleDay: number | null; minTriggerAmount: string | null; triggerCategoryId: string | null; sourceAccountId: string; priority: number; perRunLimit: string | null; actions: ActionDef[]; lastRunAt: string | null; lastRunStatus: string | null }
export interface RuleRequest { name: string; triggerType: RuleView['triggerType']; schedule: 'WEEKLY' | 'MONTHLY' | null; scheduleDay: number | null; minTriggerAmount: string | null; triggerCategoryId: string | null; sourceAccountId: string; priority: number; perRunLimit: string | null; actions: ActionDef[] }
export interface Outcome { position: number; targetType: string; targetId: string; targetName: string; requested: string; allocated: string; status: string; message: string | null }
export interface AutomationPreview { triggerAmount: string | null; available: string; perRunLimit: string | null; outcomes: Outcome[]; totalAllocated: string; note: string }
export interface RunView { id: string; ruleId: string; ruleName: string; status: 'SUCCESS' | 'PARTIAL' | 'FAILED'; triggerKey: string; triggerAmount: string | null; totalAllocated: string; message: string | null; attempt: number; retryOf: string | null; createdAt: string; outcomes: Outcome[]; retryable: boolean }

export interface SandboxEvent { kind: 'ONE_OFF' | 'MONTHLY_CHANGE' | 'CANCEL_BILL'; label: string; date: string | null; amount: string | null; billId: string | null }
export interface SandboxResult { points: { date: string; baseline: string; scenario: string }[]; lowestBaseline: string; lowestBaselineDate: string; lowestScenario: string; lowestScenarioDate: string; firstNegativeScenario: string | null; endBaseline: string; endScenario: string }
export interface Simulation { currency: string; horizonDays: number; assumptions: { startingBalance: string; averageDailyNet: string; scheduledBills: number; notes: string[] }; result: SandboxResult; disclaimer: string }
export interface Scenario { id: string; name: string; currency: string; horizonDays: number; events: SandboxEvent[] }

export interface CsvGuess { dateCol: number | null; descCol: number | null; amountCol: number | null; debitCol: number | null; creditCol: number | null; refCol: number | null; dateFormat: string; decimal: string }
export interface CsvPreview { headers: string[] | null; sample: string[][]; rowCount: number; guess: CsvGuess; looksLikeHeader: boolean }
export interface ImportSummary { id: string; accountId: string; accountName: string; fileName: string | null; rowCount: number; invalid: number; suggested: number; unmatched: number; possibleDuplicates: number; confirmed: number; rejected: number; createdAt: string; status: string }
export interface StatementRow { id: string; rowNo: number; date: string | null; amount: string | null; description: string | null; reference: string | null; status: string; validationError: string | null; score: number | null; note: string | null; suggestedTransactionId: string | null; suggestedLabel: string | null; matchedTransactionId: string | null }
export interface RowPage { summary: ImportSummary; rows: StatementRow[]; page: number; size: number; total: number; missingInStatement: { id: string; date: string; label: string; amount: string }[] }
export interface RecurringCandidate { merchant: string; frequency: string; typicalAmount: string; occurrences: number; lastDate: string; nextExpected: string; confidence: number; reasons: string[]; priceChangeNote: string | null }

export interface TicketView { id: string; ticketNo: string; title: string; category: string; priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'; description: string; status: 'OPEN' | 'IN_PROGRESS' | 'AWAITING_USER' | 'RESOLVED' | 'CLOSED'; paymentReference: string | null; requesterName: string | null; requesterEmail: string | null; assignedTo: string | null; createdAt: string; updatedAt: string; unread: number; canReopen: boolean; canClose: boolean; chatOpen: boolean }
export interface TicketMessage { id: string; senderRole: 'USER' | 'ADMIN'; senderName: string; body: string | null; fileMessageId: string | null; fileName: string | null; fileMime: string | null; createdAt: string; readAt: string | null; mine: boolean }
export interface TicketDetail { ticket: TicketView; messages: TicketMessage[]; events: { actorRole: string; actorName: string; type: string; from: string | null; to: string | null; note: string | null; createdAt: string }[] }
export interface Paged<T> { items: T[]; page: number; size: number; total: number }

export interface AuditItem { id: string; scope: string; type: string; outcome: string; actorEmail: string | null; actorRole: string | null; ip: string | null; details: string | null; at: string }
export interface AdminDashboard {
  usersByStatus: Record<string, number>; newUsers7d: number; newUsers30d: number; transactions24h: number; transactions7d: number; paymentsByStatus: Record<string, number>; failedPayments24h: number;
  paymentVolume7d: { currency: string; amount: string; count: string }[]; openDisputes: number; refunds7d: number; openTickets: number; urgentOpenTickets: number; unassignedTickets: number; avgFirstResponseHours: number | null;
  health: { dbLatencyMs: number; liveConnections: number; heapUsedMb: number; heapMaxMb: number; uptimeMinutes: number; schemaVersion: string; staleBackgroundWork: number; checks: string[] };
  recentAudit: AuditItem[]; activity: { day: string; transactions: number; payments: number; newUsers: number }[];
}
export interface AdminUser { id: string; email: string; fullName: string; status: string; createdAt: string; emailVerified: boolean; lastLogin: string | null; accounts: number; openTickets: number }
export interface AdminPayment { id: string; reference: string; payer: string; payee: string; amount: string; currency: string; status: string; failureReason: string | null; createdAt: string; flags: string[]; disputeStatus: string | null }
export interface AnnouncementView { id: string; title: string; body: string; audience: string; status: 'DRAFT' | 'PUBLISHED'; createdAt: string; publishedAt: string | null; delivered: number; read: number; targetCount: number }
export interface DefaultCategory { id: string; name: string; kind: 'INCOME' | 'EXPENSE'; color: string | null; archived: boolean; usage: number }
export interface Setting { key: string; value: string; description: string; updatedAt: string }
export interface InvestigationSummary { id: string; userName: string; userEmail: string; status: string; expiresAt: string; accounts: number; balancesByCurrency: Record<string, string>; transactions90d: number; payments90d: number; openBills: number; budgets: number; recentPayments: { reference: string; status: string; amount: string }[]; notes: string[] }

export interface HouseholdSummary { id: string; name: string; currency: string; owner: boolean; members: number; myBalance: string }
export interface HMember { userId: string; name: string; role: string; balance: string }
export interface HInvitation { id: string; householdId: string; householdName: string; invitedBy: string | null; expiresAt: string; invitee: string | null; status: string }
export interface HouseholdDetail { id: string; name: string; currency: string; owner: boolean; members: HMember[]; pendingInvitations: HInvitation[]; settleUp: { from: string; to: string; amount: string; fromUser: string; toUser: string }[]; budgets: { id: string; name: string; categoryName: string | null; limit: string; spent: string; percentUsed: number }[] }
export interface HExpense { id: string; description: string; amount: string; occurredOn: string; payer: string; payerId: string; categoryName: string | null; splitType: 'EQUAL' | 'EXACT' | 'PERCENT'; shares: { userId: string; name: string; share: string }[]; canEdit: boolean }
export interface DocField { label: string; source: string }
export interface DocTemplate { id: string; name: string; kind: string; title: string; brandName: string | null; headerText: string | null; footerText: string | null; accentColor: string; fontFamily: 'HELVETICA' | 'TIMES' | 'COURIER'; fontSize: number; orientation: 'PORTRAIT' | 'LANDSCAPE'; hasLogo: boolean; builtIn: boolean; archived: boolean; fields: DocField[] }
export interface DocPreview { template: DocTemplate; fields: { label: string; value: string; source: string }[]; disclaimer: string }
export interface FinAuditItem { id: string; action: string; entityType: string; entityId: string | null; summary: string | null; previous: string | null; next: string | null; actor: string; at: string }

export interface SafeToSpend { currency: string; liquid: string; upcomingBills: string; reservedAllocations: string; safeToSpend: string; perDayRemainingMonth: string; daysLeft: number; notes: string[] }
