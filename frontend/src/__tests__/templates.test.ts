import { parseOptions, validateField, defaultFor, BINDINGS, REQUIRED } from '../lib/templates';
import type { FieldDef } from '../lib/types';

const f = (p: Partial<FieldDef>): FieldDef => ({ key: 'k', label: 'Field', type: 'TEXT', binding: 'notes', required: false, options: null, defaultValue: null, min: null, max: null, ...p });

describe('template field rules', () => {
  it('parses dropdown options with value|label pairs and plain values', () => {
    expect(parseOptions('INCOME|Received, EXPENSE|Spent')).toEqual([{ value: 'INCOME', label: 'Received' }, { value: 'EXPENSE', label: 'Spent' }]);
    expect(parseOptions('a,b')).toEqual([{ value: 'a', label: 'a' }, { value: 'b', label: 'b' }]);
    expect(parseOptions(null)).toEqual([]);
  });
  it('enforces required, amount bounds and text length', () => {
    expect(validateField(f({ required: true }), '  ')).toMatch(/required/);
    expect(validateField(f({ type: 'AMOUNT', min: 1, max: 100 }), '0.5')).toMatch(/at least/);
    expect(validateField(f({ type: 'AMOUNT', min: 1, max: 100 }), '101')).toMatch(/at most/);
    expect(validateField(f({ type: 'AMOUNT' }), '12.50')).toBeNull();
    expect(validateField(f({ type: 'TEXT', max: 3 }), 'abcd')).toMatch(/at most 3/);
    expect(validateField(f({ type: 'DATE' }), '31/12/2026')).toMatch(/valid date/);
    expect(validateField(f({ type: 'DROPDOWN', options: 'a|A,b|B' }), 'c')).toMatch(/Choose one/);
    expect(validateField(f({ type: 'NUMBER', min: 0, max: 60 }), '61')).toMatch(/at most/);
  });
  it('resolves "today" defaults and checkbox defaults', () => {
    expect(defaultFor(f({ type: 'DATE', defaultValue: 'today' }))).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(defaultFor(f({ type: 'CHECKBOX' }))).toBe('false');
  });
  it('mirrors the backend: every required binding is offered for its target', () => {
    (Object.keys(REQUIRED) as (keyof typeof REQUIRED)[]).forEach((t) => REQUIRED[t].forEach((b) => expect(BINDINGS[t].some((x) => x.value === b)).toBe(true)));
  });
});
