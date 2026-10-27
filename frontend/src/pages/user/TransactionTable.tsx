import { formatMoney } from '../../lib/money';
import type { TransactionView } from '../../lib/types';
import { Button } from '../../components/Button';

export function TransactionTable({ items, onReverse, busyId }: { items: TransactionView[]; onReverse?: (t: TransactionView) => void; busyId?: string | null }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">Transactions</caption>
        <thead className="text-xs uppercase tracking-wide text-muted">
          <tr><th scope="col" className="py-2 pr-3">Description</th><th scope="col" className="pr-3">Category</th><th scope="col" className="pr-3">Date</th><th scope="col" className="pr-3 text-right">Amount</th>{onReverse && <th scope="col"><span className="sr-only">Actions</span></th>}</tr>
        </thead>
        <tbody className="divide-y divide-line">
          {items.map((t) => {
            const out = t.type === 'EXPENSE' ? true : t.type === 'INCOME' ? false : null;
            const n = Number(t.amount);
            const shown = t.type === 'TRANSFER' ? formatMoney(t.amount, t.currency) : formatMoney(t.amount, t.currency, { signed: true });
            return (
              <tr key={t.id} className={t.status === 'REVERSED' ? 'opacity-60' : ''}>
                <td className="py-3 pr-3">
                  <p className="font-medium">{t.merchant ?? t.description ?? t.type.toLowerCase()}{t.status === 'REVERSED' && <span className="ml-2 rounded-full bg-surface-2 px-2 py-0.5 text-xs text-muted">reversed</span>}{t.reversal && <span className="ml-2 rounded-full bg-surface-2 px-2 py-0.5 text-xs text-muted">reversal</span>}</p>
                  <p className="text-xs text-muted">{t.type === 'TRANSFER' ? `${t.accountName ?? '?'} → ${t.toAccountName ?? '?'}` : t.accountName}</p>
                </td>
                <td className="pr-3 text-muted">{t.categoryName ?? (t.type === 'TRANSFER' ? 'Transfer' : '—')}</td>
                <td className="num pr-3 text-muted">{t.occurredOn}</td>
                <td className={`num pr-3 text-right font-semibold ${out === null ? '' : n < 0 ? 'text-danger' : 'text-success'}`}>{shown}</td>
                {onReverse && <td className="text-right">{t.reversible && <Button variant="ghost" loading={busyId === t.id} onClick={() => onReverse(t)} aria-label={`Reverse transaction ${t.description ?? t.type}`}>Reverse</Button>}</td>}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
