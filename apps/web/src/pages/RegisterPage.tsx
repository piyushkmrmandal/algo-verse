import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { motion } from 'framer-motion';
import { api, getErrorMessage } from '../lib/api';
import { useAuthStore } from '../stores/auth-store';
import type { AuthResponse } from '@algoverse/shared-types';

export default function RegisterPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const { mutate, isPending } = useMutation({
    mutationFn: (data: { email: string; password: string; displayName: string }) =>
      api.post<AuthResponse>('/auth/register', data).then((r) => r.data),
    onSuccess: (data) => {
      setAuth(data.user, data.accessToken, data.refreshToken);
      navigate('/problems', { replace: true });
    },
    onError: (err) => setError(getErrorMessage(err)),
  });

  return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center px-4">
      <motion.div
        initial={{ opacity: 0, y: 24 }}
        animate={{ opacity: 1, y: 0 }}
        className="w-full max-w-sm"
      >
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-gradient-brand mb-2">AlgoVerse</h1>
          <p className="text-text-secondary text-sm">Create your account</p>
        </div>

        <div className="glass rounded-2xl p-8">
          <form
            onSubmit={(e) => { e.preventDefault(); setError(''); mutate({ email, password, displayName }); }}
            className="space-y-4"
          >
            <div>
              <label className="block text-text-secondary text-xs mb-1.5 font-medium">Display Name</label>
              <input
                type="text"
                className="input-base"
                placeholder="Ada Lovelace"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                required
                minLength={2}
              />
            </div>

            <div>
              <label className="block text-text-secondary text-xs mb-1.5 font-medium">Email</label>
              <input
                type="email"
                className="input-base"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div>
              <label className="block text-text-secondary text-xs mb-1.5 font-medium">
                Password <span className="text-text-muted">(min 8 chars)</span>
              </label>
              <input
                type="password"
                className="input-base"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
              />
            </div>

            {error && (
              <motion.p
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                className="text-danger text-xs bg-danger/10 rounded-lg px-3 py-2"
              >
                {error}
              </motion.p>
            )}

            <button type="submit" className="btn-primary w-full py-2.5" disabled={isPending}>
              {isPending ? 'Creating account…' : 'Create account'}
            </button>
          </form>

          <p className="text-center text-text-muted text-xs mt-6">
            Already have an account?{' '}
            <Link to="/login" className="text-brand-primary hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </motion.div>
    </div>
  );
}
