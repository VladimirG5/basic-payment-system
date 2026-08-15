import { useAuth } from '../auth/AuthContext';

// Placeholder landing page - BalanceCard/TransactionList (commit 16) and
// TransferWizard (commit 17) replace this body; routing/auth wiring is final.
export function Dashboard() {
  const { email, logout } = useAuth();

  return (
    <div className="min-h-screen bg-slate-50 p-4">
      <div className="mx-auto flex max-w-sm items-center justify-between rounded-lg bg-white p-4 shadow">
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
    </div>
  );
}
