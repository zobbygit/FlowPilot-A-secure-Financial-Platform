import { AMOUNT_RE } from './money';
import type { FieldDef, FieldType, TemplateView } from './types';

/** Mirror of the backend's allowed bindings (the server remains the authority). */
export const BINDINGS: Record<TemplateView['targetType'], { value: string; label: string; types: FieldType[] }[]> = {
  TRANSACTION: [
    { value: 'amount', label: 'Amount', types: ['AMOUNT'] }, { value: 'merchant', label: 'Merchant / person', types: ['TEXT'] }, { value: 'description', label: 'Description', types: ['TEXT'] },
    { value: 'categoryId', label: 'Category', types: ['CATEGORY'] }, { value: 'accountId', label: 'Account', types: ['ACCOUNT'] }, { value: 'occurredOn', label: 'Date', types: ['DATE'] },
    { value: 'paymentMethod', label: 'Payment method', types: ['DROPDOWN', 'TEXT'] }, { value: 'type', label: 'Income / expense choice', types: ['DROPDOWN'] },
    { value: 'tax', label: 'Tax (added to notes)', types: ['AMOUNT'] }, { value: 'reference', label: 'Reference (added to notes)', types: ['TEXT'] }, { value: 'notes', label: 'Notes', types: ['TEXT'] },
    { value: 'receiptFile', label: 'Receipt file', types: ['FILE'] },
  ],
  MEMO: [
    { value: 'title', label: 'Title', types: ['TEXT'] }, { value: 'body', label: 'Description', types: ['TEXT'] }, { value: 'tags', label: 'Tags', types: ['TAGS'] },
    { value: 'followUpDate', label: 'Follow-up date', types: ['DATE'] }, { value: 'transactionId', label: 'Linked transaction', types: ['TRANSACTION'] },
  ],
  BILL: [
    { value: 'name', label: 'Name', types: ['TEXT'] }, { value: 'amount', label: 'Amount', types: ['AMOUNT'] }, { value: 'frequency', label: 'Frequency', types: ['DROPDOWN'] },
    { value: 'nextDueDate', label: 'Next date', types: ['DATE'] }, { value: 'accountId', label: 'Account', types: ['ACCOUNT'] }, { value: 'reminderDays', label: 'Reminder days', types: ['NUMBER'] },
    { value: 'categoryId', label: 'Category', types: ['CATEGORY'] }, { value: 'notes', label: 'Notes', types: ['TEXT'] },
  ],
};

export const REQUIRED: Record<TemplateView['targetType'], string[]> = { TRANSACTION: ['amount', 'accountId'], MEMO: ['title'], BILL: ['name', 'amount', 'frequency', 'nextDueDate', 'accountId'] };

export function parseOptions(options: string | null): { value: string; label: string }[] {
  if (!options) return [];
  return options.split(',').map((o) => o.trim()).filter(Boolean).map((o) => { const i = o.indexOf('|'); return i < 0 ? { value: o, label: o } : { value: o.slice(0, i), label: o.slice(i + 1) }; });
}

/** Client-side mirror of the server's per-field rules, used for instant feedback and the live preview. */
export function validateField(f: FieldDef, raw: string | undefined): string | null {
  const v = (raw ?? '').trim();
  if (f.type === 'FILE') return null;
  if (v === '') return f.required && f.type !== 'CHECKBOX' ? `${f.label} is required.` : null;
  const min = f.min === null || f.min === '' ? null : Number(f.min), max = f.max === null || f.max === '' ? null : Number(f.max);
  switch (f.type) {
    case 'TEXT': if (max !== null && v.length > max) return `${f.label} must be at most ${max} characters.`; if (min !== null && v.length < min) return `${f.label} must be at least ${min} characters.`; return null;
    case 'AMOUNT': if (!AMOUNT_RE.test(v)) return `${f.label} must be a valid amount.`; if (min !== null && Number(v) < min) return `${f.label} must be at least ${min}.`; if (max !== null && Number(v) > max) return `${f.label} must be at most ${max}.`; return null;
    case 'NUMBER': if (!/^\d{1,9}$/.test(v)) return `${f.label} must be a whole number.`; if (min !== null && Number(v) < min) return `${f.label} must be at least ${min}.`; if (max !== null && Number(v) > max) return `${f.label} must be at most ${max}.`; return null;
    case 'DATE': return /^\d{4}-\d{2}-\d{2}$/.test(v) && !Number.isNaN(Date.parse(v)) ? null : `${f.label} must be a valid date.`;
    case 'DROPDOWN': return parseOptions(f.options).some((o) => o.value === v) ? null : `Choose one of the options for ${f.label}.`;
    default: return null;
  }
}

export function defaultFor(f: FieldDef): string {
  if (f.defaultValue === 'today') return new Date().toISOString().slice(0, 10);
  if (f.type === 'CHECKBOX') return f.defaultValue === 'true' ? 'true' : 'false';
  return f.defaultValue ?? '';
}
