import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { login as loginRequest } from '../api/auth';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../auth/AuthContext';

export function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const justRegistered = Boolean((location.state as { registered?: boolean } | null)?.registered);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await loginRequest({ email, password });
      login(response.token);
      navigate('/', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-4">
      <form onSubmit={handleSubmit} className="w-full max-w-sm rounded-lg bg-white p-6 shadow">
        <h1 className="mb-6 text-xl font-semibold text-slate-900">Log in</h1>

        {justRegistered && (
          <p className="mb-4 rounded-md bg-green-50 px-3 py-2 text-sm text-green-700">
            Registration successful. Log in with your new account.
          </p>
        )}
        {error && (
          <p role="alert" className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <label className="mb-4 block text-sm font-medium text-slate-700">
          Email
          <input
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </label>

        <label className="mb-6 block text-sm font-medium text-slate-700">
          Password
          <input
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none"
          />
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {submitting ? 'Logging in…' : 'Log in'}
        </button>

        <p className="mt-4 text-center text-sm text-slate-600">
          No account?{' '}
          <Link to="/register" className="font-medium text-slate-900 underline">
            Register
          </Link>
        </p>
      </form>
    </div>
  );
}
