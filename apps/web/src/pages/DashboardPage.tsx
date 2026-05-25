import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '../stores/auth-store'
import { api } from '../lib/api'
import { XpCounter } from '../components/gamification/XpCounter'
import { StreakTracker } from '../components/gamification/StreakTracker'
import AppNav from '../components/ui/AppNav'

interface XpResponse { totalXp: number; level: number; xpToNextLevel: number }
interface StreakResponse {
  currentStreak: number
  longestStreak: number
  lastSolvedDate: string | null
  weeklyActivity: boolean[]
}
interface BadgeResponse { id: string; slug: string; name: string; description: string; earnedAt: string }

const QUICK_LINKS = [
  { to: '/problems', label: 'Browse Problems', icon: '📚', color: 'from-[#6366F1] to-[#8B5CF6]', desc: 'DSA challenges' },
  { to: '/sysdesign', label: 'System Design', icon: '🏗️', color: 'from-[#10B981] to-[#0D9488]', desc: 'Architecture deep dives' },
  { to: '/collaborate', label: 'Collaborate', icon: '👥', color: 'from-[#F59E0B] to-[#EF4444]', desc: 'Code with others' },
]

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user)

  const { data: xp } = useQuery<XpResponse>({
    queryKey: ['gamification', 'xp', user?.id],
    queryFn: () => api.get<XpResponse>(`/gamification/xp/${user?.id}`).then((r) => r.data),
    enabled: !!user?.id,
    staleTime: 60_000,
  })

  const { data: streak } = useQuery<StreakResponse>({
    queryKey: ['gamification', 'streak', user?.id],
    queryFn: () => api.get<StreakResponse>(`/gamification/streak/${user?.id}`).then((r) => r.data),
    enabled: !!user?.id,
    staleTime: 60_000,
  })

  const { data: badges = [] } = useQuery<BadgeResponse[]>({
    queryKey: ['gamification', 'badges', user?.id],
    queryFn: () => api.get<BadgeResponse[]>(`/gamification/badges/${user?.id}`).then((r) => r.data),
    enabled: !!user?.id,
    staleTime: 60_000,
  })

  const hour = new Date().getHours()
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'

  return (
    <div className="min-h-screen bg-bg-base">
      <AppNav />

      <div className="max-w-6xl mx-auto px-4 py-10 space-y-8">
        {/* Greeting */}
        <motion.div initial={{ opacity: 0, y: -12 }} animate={{ opacity: 1, y: 0 }}>
          <h1 className="text-2xl font-bold text-text-primary">
            {greeting},{' '}
            <span className="text-gradient-brand">{user?.displayName?.split(' ')[0]}</span> 👋
          </h1>
          <p className="text-text-secondary text-sm mt-1">
            {streak?.currentStreak
              ? `🔥 ${streak.currentStreak}-day streak — keep it going!`
              : 'Start your first problem to build your streak.'}
          </p>
        </motion.div>

        {/* Stats row */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.08 }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
        >
          <div className="glass rounded-2xl p-6">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">XP Progress</p>
            <XpCounter
              xp={xp?.totalXp ?? 0}
              level={xp?.level ?? 1}
              xpToNext={xp?.xpToNextLevel ?? 100}
            />
          </div>

          <div className="glass rounded-2xl p-6">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">Daily Streak</p>
            <StreakTracker
              currentStreak={streak?.currentStreak ?? 0}
              longestStreak={streak?.longestStreak ?? 0}
              lastSolvedDate={streak?.lastSolvedDate ?? null}
              weeklyActivity={streak?.weeklyActivity ?? []}
            />
          </div>

          <div className="glass rounded-2xl p-6">
            <p className="text-text-muted text-xs mb-3 uppercase tracking-wider">
              Badges {badges.length > 0 && `· ${badges.length}`}
            </p>
            {badges.length === 0 ? (
              <p className="text-text-muted text-sm">Earn badges by solving problems</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {badges.slice(0, 8).map((b) => (
                  <span
                    key={b.id}
                    title={`${b.name}: ${b.description}`}
                    className="text-xl cursor-help"
                  >
                    🏅
                  </span>
                ))}
                {badges.length > 8 && (
                  <span className="text-xs text-text-muted self-center">+{badges.length - 8} more</span>
                )}
              </div>
            )}
          </div>
        </motion.div>

        {/* Quick actions */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.14 }}
        >
          <h2 className="text-sm font-semibold text-text-secondary uppercase tracking-wider mb-4">
            Jump back in
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {QUICK_LINKS.map((link) => (
              <Link
                key={link.to}
                to={link.to}
                className="group relative overflow-hidden rounded-2xl border border-border-subtle bg-bg-elevated p-5 hover:border-brand-primary/40 transition-all duration-200 hover:-translate-y-0.5"
              >
                <div className={`absolute inset-0 bg-gradient-to-br ${link.color} opacity-0 group-hover:opacity-5 transition-opacity duration-300`} />
                <span className="text-3xl block mb-3">{link.icon}</span>
                <p className="text-sm font-semibold text-text-primary group-hover:text-brand-primary transition-colors">
                  {link.label}
                </p>
                <p className="text-xs text-text-muted mt-0.5">{link.desc}</p>
                <span className="absolute bottom-4 right-4 text-text-muted group-hover:text-brand-primary text-sm transition-all duration-200 group-hover:translate-x-1">
                  →
                </span>
              </Link>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  )
}
