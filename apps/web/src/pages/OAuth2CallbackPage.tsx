import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuthStore } from '../stores/auth-store';
import { api, getErrorMessage } from '../lib/api';
import type { AuthResponse } from '@algoverse/shared-types';

export default function OAuth2CallbackPage() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');
    const refreshToken = params.get('refreshToken');
    const error = params.get('error');

    if (error) {
      navigate(`/login?error=${encodeURIComponent(error)}`, { replace: true });
      return;
    }

    if (!token || !refreshToken) {
      navigate('/login?error=oauth_missing_tokens', { replace: true });
      return;
    }

    // Exchange tokens — fetch current user info
    api
      .get<AuthResponse['user']>('/auth/me', {
        headers: { Authorization: `Bearer ${token}` },
      })
      .then((res) => {
        setAuth(res.data, token, refreshToken);
        navigate('/dashboard', { replace: true });
      })
      .catch((err) => {
        const msg = getErrorMessage(err);
        navigate(`/login?error=${encodeURIComponent(msg)}`, { replace: true });
      });
  }, [navigate, setAuth]);

  return (
    <div className="min-h-screen bg-bg-base flex items-center justify-center">
      <motion.div
        className="flex flex-col items-center gap-6"
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.4 }}
      >
        {/* Animated logo mark */}
        <motion.div
          className="w-16 h-16 rounded-2xl bg-gradient-to-br from-brand-primary to-brand-accent flex items-center justify-center"
          animate={{ rotate: [0, 360] }}
          transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
        >
          <svg
            className="w-8 h-8 text-white"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
        </motion.div>

        <div className="text-center space-y-2">
          <h1 className="text-text-primary text-xl font-semibold tracking-tight">
            Signing you in…
          </h1>
          <p className="text-text-muted text-sm font-mono">
            Completing OAuth2 authentication
          </p>
        </div>

        {/* Progress dots */}
        <div className="flex gap-2">
          {[0, 1, 2].map((i) => (
            <motion.div
              key={i}
              className="w-2 h-2 rounded-full bg-brand-primary"
              animate={{ opacity: [0.3, 1, 0.3] }}
              transition={{ duration: 1.2, repeat: Infinity, delay: i * 0.2 }}
            />
          ))}
        </div>
      </motion.div>
    </div>
  );
}
