import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '../stores/auth-store';

// ─── Matrix rain canvas ───────────────────────────────────────────────────────

function MatrixCanvas() {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d')!;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    const CHARS = '{}[]()<>=>/\\|;:.,+-*&^%$#@!~`01ABCDEFfunctionclassreturnconst';
    const COL_W = 18;
    const cols = Math.ceil(window.innerWidth / COL_W);
    const drops: number[] = Array(cols).fill(0).map(() => Math.random() * -50);

    const draw = () => {
      ctx.fillStyle = 'rgba(10,10,11,0.06)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.font = '13px monospace';

      for (let i = 0; i < drops.length; i++) {
        const ch = CHARS[Math.floor(Math.random() * CHARS.length)];
        const y = drops[i] * COL_W;

        // Lead character — bright purple
        if (drops[i] > 0) {
          ctx.fillStyle = 'rgba(168,85,247,0.9)';
          ctx.fillText(ch, i * COL_W, y);
        }

        // Trail — dim teal/green
        ctx.fillStyle = `rgba(20,184,166,${Math.random() * 0.25})`;
        ctx.fillText(CHARS[Math.floor(Math.random() * CHARS.length)], i * COL_W, y - COL_W);

        if (y > canvas.height && Math.random() > 0.97) drops[i] = 0;
        drops[i] += 0.4;
      }
    };

    const id = setInterval(draw, 50);
    return () => {
      clearInterval(id);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full"
      style={{ opacity: 0.35 }}
    />
  );
}

// ─── Typewriter cycling through DSA snippets ──────────────────────────────────

const SNIPPETS = [
  'twoSum(nums, target)  →  O(n)',
  'graph.bfs(src)  →  O(V + E)',
  'dp[i] = max(dp[i-1], nums[i])',
  'new MinHeap().push(val)',
  'LRU Cache: HashMap + DLL',
  'quickSort(lo, hi)  →  O(n log n)',
  'class TrieNode { children: Map }',
  'unionFind.union(a, b)',
];

function Typewriter() {
  const [idx, setIdx] = useState(0);
  const [displayed, setDisplayed] = useState('');
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const target = SNIPPETS[idx];
    let timeout: ReturnType<typeof setTimeout>;

    if (!deleting && displayed.length < target.length) {
      timeout = setTimeout(() => setDisplayed(target.slice(0, displayed.length + 1)), 45);
    } else if (!deleting && displayed.length === target.length) {
      timeout = setTimeout(() => setDeleting(true), 1800);
    } else if (deleting && displayed.length > 0) {
      timeout = setTimeout(() => setDisplayed(displayed.slice(0, -1)), 22);
    } else if (deleting && displayed.length === 0) {
      setDeleting(false);
      setIdx((i) => (i + 1) % SNIPPETS.length);
    }

    return () => clearTimeout(timeout);
  }, [displayed, deleting, idx]);

  return (
    <span className="font-mono text-teal-400 text-base sm:text-lg">
      {displayed}
      <span className="animate-pulse text-purple-400">|</span>
    </span>
  );
}

// ─── Feature badges ───────────────────────────────────────────────────────────

const FEATURES = [
  { icon: '⚡', label: 'AST Execution Tracing' },
  { icon: '🤖', label: 'AI Mentor (Open-Source LLM)' },
  { icon: '🎯', label: '200+ DSA Problems' },
  { icon: '🏗️', label: 'System Design Arena' },
  { icon: '👥', label: 'Real-time Collaboration' },
  { icon: '📊', label: 'XP & Leaderboards' },
];

// ─── Ambient glow orbs ────────────────────────────────────────────────────────

function GlowOrbs() {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      <div
        className="absolute rounded-full blur-3xl"
        style={{
          width: 600, height: 600,
          top: '10%', left: '15%',
          background: 'radial-gradient(circle, rgba(168,85,247,0.12) 0%, transparent 70%)',
        }}
      />
      <div
        className="absolute rounded-full blur-3xl"
        style={{
          width: 500, height: 500,
          bottom: '10%', right: '10%',
          background: 'radial-gradient(circle, rgba(20,184,166,0.1) 0%, transparent 70%)',
        }}
      />
      <div
        className="absolute rounded-full blur-2xl"
        style={{
          width: 300, height: 300,
          top: '50%', left: '50%',
          transform: 'translate(-50%,-50%)',
          background: 'radial-gradient(circle, rgba(99,102,241,0.08) 0%, transparent 70%)',
        }}
      />
    </div>
  );
}

// ─── Main page ────────────────────────────────────────────────────────────────

export default function IntroPage() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // Brief delay so canvas renders before content fades in
    const t = setTimeout(() => setReady(true), 200);
    return () => clearTimeout(t);
  }, []);

  const handlePrimary = () => navigate(isAuthenticated ? '/problems' : '/register');
  const handleSecondary = () => navigate(isAuthenticated ? '/dashboard' : '/login');

  return (
    <div className="relative min-h-screen bg-[#0a0a0b] overflow-hidden flex items-center justify-center">
      {/* Layer 1 — matrix rain */}
      <MatrixCanvas />

      {/* Layer 2 — ambient glow */}
      <GlowOrbs />

      {/* Layer 3 — vignette overlay */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background:
            'radial-gradient(ellipse 70% 70% at 50% 50%, transparent 0%, rgba(10,10,11,0.5) 100%)',
        }}
      />

      {/* Layer 4 — content */}
      <AnimatePresence>
        {ready && (
          <motion.div
            key="content"
            initial={{ opacity: 0, y: 32 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.8, ease: [0.22, 1, 0.36, 1] }}
            className="relative z-10 flex flex-col items-center text-center px-6 max-w-3xl w-full"
          >
            {/* Logo */}
            <motion.div
              initial={{ scale: 0.85, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              transition={{ delay: 0.1, duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
              className="mb-6"
            >
              <div className="inline-flex items-center gap-3 mb-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center text-xl font-bold"
                  style={{
                    background: 'linear-gradient(135deg, #a855f7 0%, #6366f1 50%, #14b8a6 100%)',
                  }}
                >
                  A
                </div>
                <h1
                  className="text-5xl sm:text-6xl font-extrabold tracking-tight"
                  style={{
                    background: 'linear-gradient(135deg, #c084fc 0%, #818cf8 45%, #2dd4bf 100%)',
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                  }}
                >
                  AlgoVerse
                </h1>
              </div>
              <p className="text-zinc-400 text-base sm:text-lg font-light tracking-wide">
                Master DSA & System Design — the cinematic way
              </p>
            </motion.div>

            {/* Typewriter */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.35 }}
              className="mb-10 h-8 flex items-center justify-center"
            >
              <span className="text-zinc-600 font-mono text-sm mr-2">{'>'}</span>
              <Typewriter />
            </motion.div>

            {/* Feature badges */}
            <motion.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.5, duration: 0.5 }}
              className="flex flex-wrap justify-center gap-2 mb-12"
            >
              {FEATURES.map((f, i) => (
                <motion.span
                  key={f.label}
                  initial={{ opacity: 0, scale: 0.85 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.55 + i * 0.07 }}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border"
                  style={{
                    background: 'rgba(255,255,255,0.04)',
                    borderColor: 'rgba(168,85,247,0.25)',
                    color: '#a1a1aa',
                  }}
                >
                  <span>{f.icon}</span>
                  {f.label}
                </motion.span>
              ))}
            </motion.div>

            {/* CTAs */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.9, duration: 0.5 }}
              className="flex flex-col sm:flex-row items-center gap-4 w-full max-w-sm"
            >
              <button
                onClick={handlePrimary}
                className="w-full sm:flex-1 relative overflow-hidden px-8 py-3.5 rounded-xl text-white font-semibold text-sm transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]"
                style={{
                  background: 'linear-gradient(135deg, #a855f7 0%, #6366f1 100%)',
                  boxShadow: '0 0 24px rgba(168,85,247,0.4), inset 0 1px 0 rgba(255,255,255,0.15)',
                }}
              >
                <span className="relative z-10">
                  {isAuthenticated ? 'Continue Coding →' : 'Start Your Journey →'}
                </span>
              </button>

              <button
                onClick={handleSecondary}
                className="w-full sm:flex-1 px-8 py-3.5 rounded-xl font-semibold text-sm transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]"
                style={{
                  background: 'rgba(255,255,255,0.05)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: '#a1a1aa',
                }}
              >
                {isAuthenticated ? 'Dashboard' : 'Sign In'}
              </button>
            </motion.div>

            {/* Bottom hint */}
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 1.2 }}
              className="mt-10 text-zinc-700 text-xs font-mono"
            >
              Powered by open-source AI · No cloud lock-in · Runs locally with Ollama
            </motion.p>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
