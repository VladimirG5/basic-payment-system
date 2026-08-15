import type { FormEvent } from 'react';
import { useTransferStore } from '../store/useTransferStore';
import { OtpInputModal } from './OtpInputModal';

interface TransferWizardProps {
  sourceAccountId: number;
  currency: string;
  onClose: () => void;
  onSuccess: () => void;
}

export function TransferWizard({ sourceAccountId, currency, onClose, onSuccess }: TransferWizardProps) {
  const { state, dispatch } = useTransferStore(sourceAccountId, currency);

  function handleFieldChange(field: 'destinationAccountId' | 'amount' | 'description', value: string) {
    dispatch({ type: 'SET_FIELD', field, value });
  }

  function handleInitiateSubmit(event: FormEvent) {
    event.preventDefault();
    // Re-asserting the current amount first guarantees a transition out of
    // ERROR into IDLE (see transferReducer's SET_FIELD handling) so retrying
    // after a failed initiate doesn't require the user to touch a field first.
    dispatch({ type: 'SET_FIELD', field: 'amount', value: state.form.amount });
    dispatch({ type: 'INITIATE_TRANSFER' });
  }

  const awaitingOtp = state.step === 'AWAITING_OTP' || state.step === 'SUBMITTING' || (state.step === 'ERROR' && state.challenge !== null);

  if (awaitingOtp && state.challenge) {
    return (
      <OtpInputModal
        expiresAt={state.challenge.expiresAt}
        submitting={state.step === 'SUBMITTING'}
        error={state.step === 'ERROR' ? state.error : null}
        onSubmit={(otpCode) => dispatch({ type: 'SUBMIT_OTP', otpCode })}
        onCancel={onClose}
      />
    );
  }

  if (state.step === 'SUCCESS' && state.result) {
    return (
      <div className="fixed inset-0 flex items-center justify-center bg-black/40 p-4">
        <div className="w-full max-w-sm rounded-lg bg-white p-6 text-center shadow">
          <h2 className="mb-2 text-lg font-semibold text-slate-900">Transfer complete</h2>
          <p className="mb-4 text-sm text-slate-600">
            Transaction #{state.result.transactionId} — new balance {state.result.newBalance.toFixed(2)} {currency}
          </p>
          <button
            type="button"
            onClick={onSuccess}
            className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white"
          >
            Done
          </button>
        </div>
      </div>
    );
  }

  const sending = state.step === 'INITIATED';

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/40 p-4">
      <form onSubmit={handleInitiateSubmit} className="w-full max-w-sm rounded-lg bg-white p-6 shadow">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">Send money</h2>

        {state.error && (
          <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {state.error}
          </p>
        )}

        <label className="mb-4 block text-sm font-medium text-slate-700">
          Recipient account ID
          <input
            type="number"
            required
            value={state.form.destinationAccountId}
            onChange={(event) => handleFieldChange('destinationAccountId', event.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </label>

        <label className="mb-4 block text-sm font-medium text-slate-700">
          Amount ({currency})
          <input
            type="number"
            min="0.01"
            max="5000"
            step="0.01"
            required
            value={state.form.amount}
            onChange={(event) => handleFieldChange('amount', event.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </label>

        <label className="mb-6 block text-sm font-medium text-slate-700">
          Description (optional)
          <input
            type="text"
            value={state.form.description}
            onChange={(event) => handleFieldChange('description', event.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </label>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={sending}
            className="flex-1 rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {sending ? 'Sending code…' : 'Continue'}
          </button>
        </div>
      </form>
    </div>
  );
}
