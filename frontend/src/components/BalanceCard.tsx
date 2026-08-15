import type { AccountResponse } from '../api/types';

interface BalanceCardProps {
  account: AccountResponse;
}

export function BalanceCard({ account }: BalanceCardProps) {
  const formattedBalance = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: account.currency,
  }).format(account.balance);

  return (
    <div className="rounded-lg bg-white p-4 shadow">
      <p className="text-sm text-slate-500">Available balance</p>
      <p className="mt-1 text-3xl font-semibold text-slate-900">{formattedBalance}</p>
      <div className="mt-4 flex items-center justify-between text-sm">
        <span className="font-mono text-slate-600">{account.iban}</span>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${
            account.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
          }`}
        >
          {account.status}
        </span>
      </div>
    </div>
  );
}
