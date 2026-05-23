import React, { useMemo } from 'react'
import { motion } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

interface StreakTrackerProps {
  currentStreak: number
  longestStreak: number
  lastActivityDate: string // ISO date string e.g. "2026-05-22"
  freezeCount: number
}

// ── Milestone badges ─────────────────────────────────────────────────────────

interface Milestone {
  days: number
  icon: string
  label: string
}

const MILESTONES: Milestone[] = [
  { days: 7, icon: '🌟', label: '1 Week' },
  { days: 30, icon: '🔥', label: '1 Month' },
  { days: 100, icon: '💎', label: '100 Days' },
  { days: 365, icon: '👑', label: '1 Year' },
]

// ── Motivational messages ─────────────────────────────────────────────────────

const getMotivationalMessage = (streak: number): string => {
  if (streak === 0) return 'Start your streak today!'
  if (streak < 3) return 'Great start! Keep it up!'
  if (streak < 7) return 'You are building momentum!'
  if (streak < 14) return 'One week warrior! Impressive!'
  if (streak < 30) return 'On fire! You are unstoppable!'
  if (streak < 100) return 'A true DSA legend in the making!'
  return 'Absolutely legendary. Hall of Fame material!'
}

// ── Date utils ────────────────────────────────────────────────────────────────

const parseDate = (iso: string): Date => {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

const toISODate = (date: Date): string =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`

const today = (): string => toISODate(new Date())
const yesterday = (): string => {
  const d = new Date()
  d.setDate(d.getDate() - 1)
  return toISODate(d)
}

const last7Days = (): string[] => {
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() - (6 - i))
    return toISODate(d)
  })
}

// ── Fire flicker animation ────────────────────────────────────────────────────

const FireEmoji: React.FC<{ size?: string }> = ({ size = 'text-4xl' }) => (
  <motion.span
    className={`${size} select-none`}
    animate={{
      scale: [1, 1.08, 0.97, 1.04, 1],
      rotate: [-2, 2, -1, 1, 0],
      filter: [
        'brightness(1)',
        'brightness(1.15)',
        'brightness(0.95)',
        'brightness(1.1)',
        'brightness(1)',
      ],
    }}
    transition={{ repeat: Infinity, duration: 1.8, ease: 'easeInOut' }}
  >
    🔥
  </motion.span>
)

// ── Day dot ───────────────────────────────────────────────────────────────────

type DayStatus = 'active' | 'missed' | 'freeze' | 'future'

const DayDot: React.FC<{ status: DayStatus; label: string }> = ({
  status,
  label,
}) => {
  const colors: Record<DayStatus, string> = {
    active: 'bg-[#10B981] shadow-[0_0_6px_#10B98160]',
    missed: 'bg-[#475569]/40',
    freeze: 'bg-[#22D3EE] shadow-[0_0_6px_#22D3EE60]',
    future: 'bg-[#18181C] border border-[#475569]/20',
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <motion.div
        className={`w-3 h-3 rounded-full ${colors[status]}`}
        initial={false}
        animate={status === 'active' ? { scale: [1, 1.1, 1] } : {}}
        transition={{ repeat: Infinity, duration: 2, delay: Math.random() }}
      />
      <span className="text-[9px] text-[#475569]">{label}</span>
    </div>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────

const StreakTracker: React.FC<StreakTrackerProps> = ({
  currentStreak,
  longestStreak,
  lastActivityDate,
  freezeCount,
}) => {
  const todayStr = today()
  const yesterdayStr = yesterday()

  const isAtRisk =
    lastActivityDate === yesterdayStr &&
    lastActivityDate !== todayStr

  const days7 = last7Days()

  // Mock: assume active on days within current streak counting back from lastActivityDate
  const activeDays = useMemo(() => {
    const last = parseDate(lastActivityDate)
    const active = new Set<string>()
    for (let i = 0; i < Math.min(currentStreak, 7); i++) {
      const d = new Date(last)
      d.setDate(d.getDate() - i)
      active.add(toISODate(d))
    }
    return active
  }, [lastActivityDate, currentStreak])

  const getDayStatus = (dateStr: string): DayStatus => {
    if (dateStr > todayStr) return 'future'
    if (activeDays.has(dateStr)) return 'active'
    // If streak was "broken" and freeze was available, show as freeze
    // For simplicity, just show missed
    return 'missed'
  }

  const unlockedMilestones = MILESTONES.filter(
    (m) => longestStreak >= m.days,
  )

  return (
    <div className="flex flex-col gap-4 p-4 rounded-2xl bg-[#111113] border border-[#18181C]">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <FireEmoji size="text-3xl" />
          <div>
            <div className="flex items-baseline gap-1.5">
              <span className="text-3xl font-black text-[#F8F8F2] leading-none">
                {currentStreak}
              </span>
              <span className="text-sm text-[#475569]">day streak</span>
            </div>
            <div className="text-xs text-[#475569] mt-0.5">
              Best:{' '}
              <span className="text-[#94A3B8] font-medium">
                {longestStreak} days
              </span>
            </div>
          </div>
        </div>

        {/* Freeze tokens */}
        <div className="flex flex-col items-end gap-1">
          <div className="flex items-center gap-1.5 text-[#22D3EE]">
            <span className="text-lg">❄️</span>
            <span className="text-sm font-bold">{freezeCount}</span>
          </div>
          <span className="text-[10px] text-[#475569]">freezes left</span>
        </div>
      </div>

      {/* At-risk warning */}
      {isAtRisk && (
        <motion.div
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center gap-2 px-3 py-2 rounded-lg bg-[#F59E0B]/10 border border-[#F59E0B]/25 text-xs text-[#F59E0B]"
        >
          <motion.span
            animate={{ opacity: [1, 0.5, 1] }}
            transition={{ repeat: Infinity, duration: 1 }}
          >
            ⚠
          </motion.span>
          <span>
            Streak at risk! Solve a problem today to keep it alive.
          </span>
        </motion.div>
      )}

      {/* 7-day calendar */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-[10px] text-[#475569] uppercase tracking-widest font-semibold">
            Last 7 Days
          </span>
        </div>
        <div className="flex justify-between">
          {days7.map((dateStr) => {
            const date = parseDate(dateStr)
            const dayLabel = date.toLocaleDateString('en', { weekday: 'narrow' })
            return (
              <DayDot
                key={dateStr}
                status={getDayStatus(dateStr)}
                label={dayLabel}
              />
            )
          })}
        </div>
      </div>

      {/* Milestone badges */}
      {MILESTONES.length > 0 && (
        <div className="space-y-2">
          <span className="text-[10px] text-[#475569] uppercase tracking-widest font-semibold">
            Milestones
          </span>
          <div className="flex gap-2">
            {MILESTONES.map((milestone) => {
              const unlocked = longestStreak >= milestone.days
              return (
                <div
                  key={milestone.days}
                  className={`flex flex-col items-center gap-1 flex-1 py-2 rounded-lg border transition-colors ${
                    unlocked
                      ? 'bg-[#6366F1]/10 border-[#6366F1]/25'
                      : 'bg-[#18181C] border-[#475569]/15 opacity-40'
                  }`}
                  title={unlocked ? `Unlocked: ${milestone.label}` : `${milestone.days} days needed`}
                >
                  <span className={`text-lg ${unlocked ? '' : 'grayscale'}`}>
                    {milestone.icon}
                  </span>
                  <span className="text-[9px] text-[#475569] font-medium">
                    {milestone.label}
                  </span>
                  {!unlocked && (
                    <span className="text-[8px] text-[#475569]">
                      {milestone.days}d
                    </span>
                  )}
                </div>
              )
            })}
          </div>
          {unlockedMilestones.length > 0 && (
            <p className="text-xs text-[#475569] text-center">
              🏆 {unlockedMilestones.length} milestone{unlockedMilestones.length > 1 ? 's' : ''} unlocked!
            </p>
          )}
        </div>
      )}

      {/* Motivational message */}
      <motion.p
        key={currentStreak}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.3 }}
        className="text-xs text-[#6366F1] text-center font-medium italic"
      >
        {getMotivationalMessage(currentStreak)}
      </motion.p>
    </div>
  )
}

export default StreakTracker
