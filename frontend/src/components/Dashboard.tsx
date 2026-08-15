import { useEffect, useState } from 'react';
import { getMyAccount, getTransactions } from '../api/accounts';
import { getErrorMessage } from '../api/errors';
import type { AccountResponse, TransactionResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { BalanceCard } from './BalanceCard';
import { TransactionList } from './TransactionList';

export function Dashboard() {
  const { email, logout } = useAuth();
  const [account, setAccount] = useState<AccountResponse | null>(null);
  const [transactions, setTransactions] = useState<TransactionResponse[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function loadDashboard() {
      try {
        const accountResponse = await getMyAccount();
        if (cancelled) return;
        setAccount(accountResponse);

        const transactionsResponse = await getTransactions(accountResponse.accountId);
        if (cancelled) return;
        setTransactions(transactionsResponse);
      } catch (err) {
        if (!cancelled) setError(getErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    loadDashboard();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="min-h-screen bg-slate-50 p-4">
      <div className="mx-auto max-w-sm space-y-4">
        <div className="flex items-center justify-between rounded-lg bg-white p-4 shadow">
          <p className="text-sm text-slate-600">
            Logged in as <span className="font-medium text-slate-900">{email}</span>
          </p>
          <button
            type="button"
            onClick={logout}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700"
          >
            Log out
          </button>
        </div>

        {loading && <p className="text-center text-sm text-slate-500">Loading…</p>}

        {error && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        {account && <BalanceCard account={account} />}
        {account && <TransactionList transactions={transactions} />}
      </div>
    </div>
  );
}
