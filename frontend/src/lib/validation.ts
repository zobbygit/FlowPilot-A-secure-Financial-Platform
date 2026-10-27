import { z } from 'zod';

/** Mirrors backend PasswordPolicy for instant feedback. The backend remains authoritative. */
export const passwordSchema = z
  .string()
  .min(12, 'Use at least 12 characters.')
  .max(128, 'Use at most 128 characters.')
  .refine(
    (pw) => [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(pw)).length >= 3,
    'Mix at least three of: lowercase, uppercase, digits, symbols.',
  );

const email = z.string().trim().min(1, 'Enter your email address.').email('Enter a valid email address.').max(254);

export const registerSchema = z
  .object({
    fullName: z.string().trim().min(2, 'Enter your full name.').max(120),
    email,
    password: passwordSchema,
    confirmPassword: z.string().min(1, 'Confirm your password.'),
    acceptTerms: z.boolean().refine((v) => v, 'You must accept the Terms of Service and Privacy Policy.'),
  })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match.' });

export const loginSchema = z.object({ email, password: z.string().min(1, 'Enter your password.') });
export const forgotSchema = z.object({ email });
export const resetSchema = z
  .object({ password: passwordSchema, confirmPassword: z.string().min(1, 'Confirm your password.') })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match.' });
export const changePasswordSchema = z.object({ currentPassword: z.string().min(1, 'Enter your current password.'), newPassword: passwordSchema });

export type RegisterForm = z.infer<typeof registerSchema>;
export type LoginForm = z.infer<typeof loginSchema>;
export type ForgotForm = z.infer<typeof forgotSchema>;
export type ResetForm = z.infer<typeof resetSchema>;
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>;

import { AMOUNT_RE } from './money';

export const accountSchema = z.object({
  name: z.string().trim().min(1, 'Name your account.').max(80),
  type: z.enum(['CASH', 'BANK', 'SAVINGS', 'WALLET']),
  currency: z.string().trim().toUpperCase().regex(/^[A-Z]{3}$/, 'Use a 3-letter code such as USD.'),
  openingBalance: z.string().trim().refine((v) => v === '' || (AMOUNT_RE.test(v)), 'Enter a valid amount (up to 4 decimals).'),
  allowNegative: z.boolean(),
});
export type AccountForm = z.infer<typeof accountSchema>;

export const transactionSchema = z.object({
  type: z.enum(['EXPENSE', 'INCOME', 'TRANSFER']),
  amount: z.string().trim().refine((v) => AMOUNT_RE.test(v) && Number(v) > 0, 'Enter an amount greater than zero (up to 4 decimals).'),
  occurredOn: z.string().min(1, 'Choose a date.'),
  accountId: z.string().min(1, 'Choose an account.'),
  toAccountId: z.string().optional(),
  categoryId: z.string().optional(),
  description: z.string().trim().max(200).optional(),
  merchant: z.string().trim().max(120).optional(),
}).superRefine((d, ctx) => {
  if (d.type === 'TRANSFER') {
    if (!d.toAccountId) ctx.addIssue({ code: 'custom', path: ['toAccountId'], message: 'Choose the destination account.' });
    else if (d.toAccountId === d.accountId) ctx.addIssue({ code: 'custom', path: ['toAccountId'], message: 'Choose a different account.' });
  }
});
export type TransactionFormValues = z.infer<typeof transactionSchema>;

const amountField = (msg = 'Enter an amount greater than zero (up to 4 decimals).') =>
  z.string().trim().refine((v) => AMOUNT_RE.test(v) && Number(v) > 0, msg);
const currencyField = z.string().trim().toUpperCase().regex(/^[A-Z]{3}$/, 'Use a 3-letter code such as USD.');

export const budgetSchema = z.object({
  name: z.string().trim().min(1, 'Name this budget.').max(80),
  periodType: z.enum(['MONTHLY', 'CUSTOM']),
  startDate: z.string().optional(), endDate: z.string().optional(),
  categoryId: z.string().optional(),
  limitAmount: amountField(), currency: currencyField,
  rollover: z.boolean(),
  thresholds: z.string().trim().refine((v) => v === '' || v.split(',').every((t) => /^\d{1,3}$/.test(t.trim()) && +t >= 1 && +t <= 200) && v.split(',').length <= 5,
    'Use up to 5 whole numbers between 1 and 200, separated by commas.'),
}).superRefine((d, ctx) => {
  if (d.periodType === 'CUSTOM') {
    if (!d.startDate) ctx.addIssue({ code: 'custom', path: ['startDate'], message: 'Choose a start date.' });
    if (!d.endDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'Choose an end date.' });
    if (d.startDate && d.endDate && d.endDate < d.startDate) ctx.addIssue({ code: 'custom', path: ['endDate'], message: 'End date must not be before the start date.' });
  }
});
export type BudgetFormValues = z.infer<typeof budgetSchema>;

export const billSchema = z.object({
  name: z.string().trim().min(1, 'Name this item.').max(80),
  kind: z.enum(['BILL', 'SUBSCRIPTION']),
  amount: amountField(), currency: currencyField,
  frequency: z.enum(['WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']),
  nextDueDate: z.string().min(1, 'Choose the next due date.'),
  categoryId: z.string().optional(), accountId: z.string().optional(),
  reminderDaysBefore: z.string().trim().regex(/^\d{1,2}$/, 'Use 0 to 60 days.').refine((v) => Number(v) <= 60, 'Use 0 to 60 days.'),
  notes: z.string().trim().max(1000).optional(),
});
export type BillFormValues = z.infer<typeof billSchema>;

export const goalSchema = z.object({
  name: z.string().trim().min(1, 'Name this goal.').max(80),
  goalType: z.enum(['EMERGENCY', 'TRAVEL', 'GADGET', 'OTHER']),
  targetAmount: amountField(), currency: currencyField,
  targetDate: z.string().optional(),
  contributionAmount: z.string().trim().refine((v) => v === '' || (AMOUNT_RE.test(v) && Number(v) > 0), 'Enter a valid amount.'),
  contributionFrequency: z.enum(['', 'WEEKLY', 'MONTHLY']),
}).superRefine((d, ctx) => {
  if ((d.contributionAmount !== '') !== (d.contributionFrequency !== '')) ctx.addIssue({ code: 'custom', path: ['contributionFrequency'], message: 'Set both the amount and the frequency, or neither.' });
});
export type GoalFormValues = z.infer<typeof goalSchema>;

export const amountOnlySchema = z.object({ amount: amountField() });
