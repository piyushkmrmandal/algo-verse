import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { useAuthStore } from '../stores/auth-store';
import { XpCounter } from '../components/gamification/XpCounter';
import { StreakTracker } from '../components/gamification/StreakTracker';

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Nav */}
      <nav className="border-b border-border-subtle bg-bg-surface/80 backdrop-blur-md sticky top-0 z-40">
        <div className="max-w-6xl mx-auto px-4 h-14 flex items-center justify-between">
          <span className="font-bold text-gradient-brand text-lg">AlgoVerse</span>
          <div className="flex items-center gap-4">
            <Link to="/problems" className="text-text-secondary hover:text-text-primary text-sm transition-colors">
              Problems
            </Link>
            <button onClick={logout} className="btn-ghost text-sm py-1.5">
              Sign out
            </button>
          </div>
        </div>
      </nav>

      <div className="max-w-6xl mx-auto px-4 py-12 space-y-8">
        {/* Greeting */}
        <motion.div initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }}>
          <h1 className="text-2xl font-bold text-text-primary">
            Welcome back, <span className="text-gradient-brand">{user?.displayName}</span>
          </h1>
          <p className="text-text-secondary text-sm mt-1">Keep the streak alive — one problem a day.</p>
        </motion.div>

        {/* Stats row */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
        >
          <div className="glass rounded-2xl p-6">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">XP Progress</p>
            <XpCounter xp={0} level={1} xpToNext={100} />
          </div>

          <div className="glass rounded-2xl p-6">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">Daily Streak</p>
            <StreakTracker currentStreak={0} longestStreak={0} lastSolvedDate={null} weeklyActivity={[]} />
          </div>

          <div className="glass rounded-2xl p-6 flex flex-col justify-between">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">Quick Actions</p>
            <Link to="/problems" className="btn-primary text-sm py-2">
              Browse Problems →
            </Link>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
