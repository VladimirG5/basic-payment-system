import { useMemo, useState } from 'react';
import type { TransactionResponse, TransactionType } from '../api/types';

type Filter = 'ALL' | TransactionType;

const FILTERS: { label: string; value: Filter }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Sent', value: 'SENT' },
  { label: 'Received', value: 'RECEIVED' },
];

interface TransactionListProps {
  transactions: TransactionResponse[];
}

export function TransactionList({ transactions }: TransactionListProps) {
  const [filter, setFilter] = useState<Filter>('ALL');

  const filtered = useMemo(
    () => (filter === 'ALL' ? transactions : transactions.filter((transaction) => transaction.type === filter)),
    [transactions, filter],
  );

  return (
    <div className="rounded-lg bg-white p-4 shadow">
      <div className="mb-3 flex gap-2">
        {FILTERS.map(({ label, value }) => (
          <button
            key={value}
            type="button"
            onClick={() => setFilter(value)}
            className={`rounded-full px-3 py-1 text-xs font-medium ${
              filter === value ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-600'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <p className="py-6 text-center text-sm text-slate-500">No transactions yet.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {filtered.map((transaction) => (
            <li key={transaction.transactionId} className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm font-medium text-slate-900">
                  {transaction.type === 'SENT' ? 'To ' : 'From '}
                  {transaction.counterpartyName ?? transaction.counterpartyAccount ?? 'Unknown'}
                </p>
                <p className="text-xs text-slate-500">
                  {new Date(transaction.timestamp).toLocaleString()}
                  {transaction.referenceNote ? ` · ${transaction.referenceNote}` : ''}
                </p>
              </div>
              <span className={`text-sm font-semibold ${transaction.type === 'SENT' ? 'text-red-600' : 'text-green-600'}`}>
                {transaction.type === 'SENT' ? '-' : '+'}
                {transaction.amount.toFixed(2)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
