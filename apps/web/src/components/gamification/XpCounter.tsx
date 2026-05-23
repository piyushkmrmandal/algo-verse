import React, { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence, useMotionValue, useTransform, animate } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

interface XpCounterProps {
  totalXp: number
  level: number
  weeklyXp: number
  weeklyHistory?: number[] // 7 values, index 0 = 6 days ago, 6 = today
}

// ── Level thresholds ───────────────────────────────────────────────────────────

// Simple exponential curve: level n requires n*100 + (n-1)*50 XP
const xpForLevel = (level: number): number => {
  if (level <= 1) return 0
  return Math.floor(100 * level + 50 * (level - 1))
}

const xpForNextLevel = (level: number): number => xpForLevel(level + 1)

// ── Level color tier ──────────────────────────────────────────────────────────

interface TierStyle {
  color: string
  gradient: string
  label: string
  isRainbow?: boolean
}

const getLevelTier = (level: number): TierStyle => {
  if (level === 100)
    return {
      color: '#FFD700',
      gradient: 'from-[#FF0000] via-[#FFD700] to-[#00FF88]',
      label: 'MYTHIC',
      isRainbow: true,
    }
  if (level >= 76)
    return {
      color: '#FFD700',
      gradient: 'from-[#FFD700] to-[#FFA500]',
      label: 'GOLD',
    }
  if (level >= 51)
    return {
      color: '#BD93F9',
      gradient: 'from-[#BD93F9] to-[#8B5CF6]',
      label: 'PURPLE',
    }
  if (level >= 26)
    return {
      color: '#22D3EE',
      gradient: 'from-[#22D3EE] to-[#0891B2]',
      label: 'BLUE',
    }
  if (level >= 11)
    return {
      color: '#10B981',
      gradient: 'from-[#10B981] to-[#059669]',
      label: 'GREEN',
    }
  return {
    color: '#94A3B8',
    gradient: 'from-[#94A3B8] to-[#475569]',
    label: 'GRAY',
  }
}

// ── Animated count-up hook ────────────────────────────────────────────────────

const useCountUp = (target: number, duration = 0.8): number => {
  const [value, setValue] = useState(target)
  const prev = useRef(target)

  useEffect(() => {
    const from = prev.current
    prev.current = target

    if (from === target) return

    const mv = { current: from }
    const controls = animate(from, target, {
      duration,
      ease: 'easeOut',
      onUpdate: (v) => setValue(Math.round(v)),
    })

    return () => controls.stop()
  }, [target, duration])

  return value
}

// ── XP floating toast ─────────────────────────────────────────────────────────

const XpGainToast: React.FC<{ gain: number }> = ({ gain }) => (
  <motion.div
    initial={{ opacity: 0, y: 0, scale: 0.8 }}
    animate={{ opacity: [0, 1, 1, 0], y: [-10, -40, -60, -80], scale: [0.8, 1.1, 1, 0.9] }}
    transition={{ duration: 1.4, times: [0, 0.2, 0.7, 1] }}
    className="absolute -top-2 right-0 pointer-events-none z-50"
  >
    <span className="text-sm font-bold text-[#10B981] bg-[#10B981]/10 border border-[#10B981]/30 px-2 py-0.5 rounded-full shadow-lg">
      +{gain} XP
    </span>
  </motion.div>
)

// ── Circular progress ring ─────────────────────────────────────────────────────

const CircularRing: React.FC<{
  progress: number // 0–1
  size: number
  strokeWidth: number
  color: string
  trackColor?: string
}> = ({ progress, size, strokeWidth, color, trackColor = '#18181C' }) => {
  const r = (size - strokeWidth) / 2
  const circumference = 2 * Math.PI * r
  const dashOffset = circumference * (1 - Math.min(1, Math.max(0, progress)))

  return (
    <svg width={size} height={size} className="-rotate-90">
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={trackColor}
        strokeWidth={strokeWidth}
      />
      <motion.circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={color}
        strokeWidth={strokeWidth}
        strokeLinecap="round"
        strokeDasharray={circumference}
        initial={{ strokeDashoffset: circumference }}
        animate={{ strokeDashoffset: dashOffset }}
        transition={{ duration: 1, ease: 'easeOut' }}
        style={{ filter: `drop-shadow(0 0 6px ${color}60)` }}
      />
    </svg>
  )
}

// ── Weekly bar chart ───────────────────────────────────────────────────────────

const WeeklyChart: React.FC<{ data: number[] }> = ({ data }) => {
  const max = Math.max(...data, 1)
  const days = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

  return (
    <div className="flex items-end gap-1 h-10">
      {data.map((val, i) => {
        const heightPct = (val / max) * 100
        const isToday = i === data.length - 1
        return (
          <div key={i} className="flex-1 flex flex-col items-center gap-1">
            <div className="w-full flex items-end" style={{ height: 32 }}>
              <motion.div
                className="w-full rounded-t"
                style={{
                  backgroundColor: isToday
                    ? '#6366F1'
                    : val > 0
                    ? '#22D3EE40'
                    : '#18181C',
                  boxShadow: isToday ? '0 0 8px #6366F180' : undefined,
                }}
                initial={{ height: 0 }}
                animate={{ height: `${Math.max(2, heightPct)}%` }}
                transition={{ duration: 0.5, delay: i * 0.06 }}
              />
            </div>
            <span className="text-[9px] text-[#475569]">{days[i]}</span>
          </div>
        )
      })}
    </div>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────

const XpCounter: React.FC<XpCounterProps> = ({
  totalXp,
  level,
  weeklyXp,
  weeklyHistory = [0, 0, 0, 0, 0, 0, 0],
}) => {
  const displayedXp = useCountUp(totalXp)
  const displayedLevel = useCountUp(level, 0.4)
  const tier = getLevelTier(level)

  const xpForThisLevel = xpForLevel(level)
  const xpNeeded = xpForNextLevel(level)
  const xpIntoLevel = totalXp - xpForThisLevel
  const xpRange = xpNeeded - xpForThisLevel
  const progress = xpRange > 0 ? Math.min(1, xpIntoLevel / xpRange) : 1

  const xpToNext = xpNeeded - totalXp
  const prevXp = useRef(totalXp)
  const [toastGain, setToastGain] = useState<number | null>(null)

  useEffect(() => {
    const diff = totalXp - prevXp.current
    if (diff > 0) {
      setToastGain(diff)
      const t = setTimeout(() => setToastGain(null), 1500)
      prevXp.current = totalXp
      return () => clearTimeout(t)
    }
    prevXp.current = totalXp
  }, [totalXp])

  return (
    <div className="relative flex flex-col items-center gap-3 p-4 rounded-2xl bg-[#111113] border border-[#18181C]">
      {/* XP toast */}
      <AnimatePresence>
        {toastGain !== null && <XpGainToast gain={toastGain} />}
      </AnimatePresence>

      {/* Ring + level badge */}
      <div className="relative flex items-center justify-center" title={`${xpToNext} XP to next level`}>
        <CircularRing
          progress={progress}
          size={96}
          strokeWidth={5}
          color={tier.color}
          trackColor="#18181C"
        />

        {/* Center content */}
        <div className="absolute flex flex-col items-center gap-0.5">
          {tier.isRainbow ? (
            <motion.span
              animate={{ backgroundPosition: ['0% 50%', '100% 50%', '0% 50%'] }}
              transition={{ repeat: Infinity, duration: 3, ease: 'linear' }}
              className="text-lg font-black"
              style={{
                background: 'linear-gradient(90deg, #FF0000, #FF8800, #FFD700, #00FF88, #22D3EE, #BD93F9, #FF0000)',
                backgroundSize: '300% 100%',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
              }}
            >
              L{displayedLevel}
            </motion.span>
          ) : (
            <span className="text-lg font-black" style={{ color: tier.color }}>
              L{displayedLevel}
            </span>
          )}
          <span className="text-[9px] font-bold text-[#475569] uppercase tracking-wider">
            {tier.label}
          </span>
        </div>
      </div>

      {/* XP number */}
      <div className="text-center">
        <div className="text-2xl font-black text-[#F8F8F2] tracking-tight">
          {displayedXp.toLocaleString()}
          <span className="text-sm font-normal text-[#475569] ml-1">XP</span>
        </div>
        <div className="text-xs text-[#475569] mt-0.5">
          {xpToNext > 0 ? (
            <>
              <span className="text-[#94A3B8]">{xpToNext.toLocaleString()}</span> to Level{' '}
              {level + 1}
            </>
          ) : (
            <span className="text-[#FFD700]">Max level reached!</span>
          )}
        </div>
      </div>

      {/* Weekly chart */}
      <div className="w-full">
        <div className="flex items-center justify-between mb-1">
          <span className="text-[10px] text-[#475569] uppercase tracking-widest font-semibold">
            This Week
          </span>
          <span className="text-[10px] text-[#6366F1] font-bold">
            +{weeklyXp.toLocaleString()} XP
          </span>
        </div>
        <WeeklyChart data={weeklyHistory} />
      </div>
    </div>
  )
}

export default XpCounter
