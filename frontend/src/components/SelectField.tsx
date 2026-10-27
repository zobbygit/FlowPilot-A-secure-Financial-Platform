import { forwardRef, useId, type SelectHTMLAttributes } from 'react';

interface Props extends SelectHTMLAttributes<HTMLSelectElement> { label: string; error?: string }

export const SelectField = forwardRef<HTMLSelectElement, Props>(function SelectField({ label, error, children, className = '', ...rest }, ref) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">{label}</label>
      <select {...rest} ref={ref} id={id} aria-invalid={error ? true : undefined} aria-describedby={error ? `${id}-e` : undefined}
        className={`w-full rounded-xl border bg-surface-2/60 px-3 py-2.5 text-sm text-ink ${error ? 'border-danger' : 'border-line'}`}>{children}</select>
      {error && <p id={`${id}-e`} role="alert" className="mt-1.5 text-xs text-danger">{error}</p>}
    </div>
  );
});
