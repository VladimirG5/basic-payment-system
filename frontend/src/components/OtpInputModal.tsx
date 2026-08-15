import { useEffect, useState, type FormEvent } from 'react';

interface OtpInputModalProps {
  expiresAt: string;
  submitting: boolean;
  error: string | null;
  onSubmit: (otpCode: string) => void;
  onCancel: () => void;
}

function secondsUntil(isoTimestamp: string): number {
  return Math.round((new Date(isoTimestamp).getTime() - Date.now()) / 1000);
}

export function OtpInputModal({ expiresAt, submitting, error, onSubmit, onCancel }: OtpInputModalProps) {
  const [otpCode, setOtpCode] = useState('');
  const [secondsLeft, setSecondsLeft] = useState(() => secondsUntil(expiresAt));

  useEffect(() => {
    setSecondsLeft(secondsUntil(expiresAt));
    const interval = setInterval(() => setSecondsLeft(secondsUntil(expiresAt)), 1000);
    return () => clearInterval(interval);
  }, [expiresAt]);

  const expired = secondsLeft <= 0;
  const minutes = Math.floor(Math.max(secondsLeft, 0) / 60);
  const seconds = Math.max(secondsLeft, 0) % 60;
  const countdown = `${minutes}:${seconds.toString().padStart(2, '0')}`;

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit(otpCode);
  }

  return (
    <div className="fixed inset-0 flex items-center justify-center bg-black/40 p-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-lg bg-white p-6 shadow">
        <h2 className="mb-2 text-lg font-semibold text-slate-900">Enter the code we sent you</h2>
        <p className={`mb-4 text-sm ${expired ? 'text-red-600' : 'text-slate-500'}`}>
          {expired ? 'Code expired.' : `Expires in ${countdown}`}
        </p>

        {error && (
          <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <input
          type="text"
          inputMode="numeric"
          pattern="[0-9]{6}"
          maxLength={6}
          required
          disabled={expired || submitting}
          value={otpCode}
          onChange={(event) => setOtpCode(event.target.value.replace(/\D/g, ''))}
          placeholder="123456"
          className="mb-4 w-full rounded-md border border-slate-300 px-3 py-2 text-center text-lg tracking-widest focus:border-slate-500 focus:outline-none"
        />

        <div className="flex gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting || expired || otpCode.length !== 6}
            className="flex-1 rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {submitting ? 'Confirming…' : 'Confirm'}
          </button>
        </div>
      </form>
    </div>
  );
}
