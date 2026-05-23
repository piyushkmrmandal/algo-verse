import React, { useCallback, useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

export type BadgeRarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY'

export interface Badge {
  id: string
  name: string
  description: string
  icon: string // emoji or URL
  rarity: BadgeRarity
}

interface AchievementToastProps {
  badge: Badge | null
  xpGain: number
  onDismiss: () => void
}

// ── Rarity config ─────────────────────────────────────────────────────────────

interface RarityConfig {
  label: string
  textColor: string
  glowClass: string
  borderStyle: string
  bgGradient: string
  showParticles: boolean
}

const RARITY_CONFIG: Record<BadgeRarity, RarityConfig> = {
  COMMON: {
    label: 'Common',
    textColor: 'text-[#94A3B8]',
    glowClass: '',
    borderStyle: 'border-[#475569]/40',
    bgGradient: 'from-[#18181C] to-[#111113]',
    showParticles: false,
  },
  RARE: {
    label: 'Rare',
    textColor: 'text-[#22D3EE]',
    glowClass: 'shadow-[0_0_20px_#22D3EE30]',
    borderStyle: 'border-[#22D3EE]/30',
    bgGradient: 'from-[#22D3EE]/10 to-[#111113]',
    showParticles: false,
  },
  EPIC: {
    label: 'Epic',
    textColor: 'text-[#BD93F9]',
    glowClass: 'shadow-[0_0_28px_#BD93F950]',
    borderStyle: 'border-[#BD93F9]/35',
    bgGradient: 'from-[#BD93F9]/10 to-[#111113]',
    showParticles: true,
  },
  LEGENDARY: {
    label: 'Legendary',
    textColor: 'text-[#FFD700]',
    glowClass: 'shadow-[0_0_40px_#FFD70060]',
    borderStyle: 'border-transparent',
    bgGradient: 'from-[#FFD700]/15 via-[#FF8800]/8 to-[#111113]',
    showParticles: true,
  },
}

// ── Particle burst ────────────────────────────────────────────────────────────

const PARTICLE_COLORS_EPIC = ['#BD93F9', '#8B5CF6', '#E879F9', '#A78BFA']
const PARTICLE_COLORS_LEGENDARY = [
  '#FFD700', '#FF8800', '#FF4500', '#FFD700', '#FFFACD',
  '#10B981', '#22D3EE', '#BD93F9', '#FF79C6',
]

const ParticleBurst: React.FC<{ rarity: BadgeRarity }> = ({ rarity }) => {
  const colors =
    rarity === 'LEGENDARY' ? PARTICLE_COLORS_LEGENDARY : PARTICLE_COLORS_EPIC
  const count = rarity === 'LEGENDARY' ? 36 : 20

  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden rounded-2xl">
      {Array.from({ length: count }, (_, i) => {
        const angle = (i / count) * 360
        const distance = 50 + Math.random() * 60
        const delay = Math.random() * 0.3
        const color = colors[i % colors.length]

        return (
          <motion.div
            key={i}
            className="absolute rounded-full"
            style={{
              backgroundColor: color,
              width: 4 + Math.random() * 4,
              height: 4 + Math.random() * 4,
              top: '50%',
              left: '50%',
            }}
            initial={{ x: 0, y: 0, opacity: 1, scale: 0 }}
            animate={{
              x: Math.cos((angle * Math.PI) / 180) * distance,
              y: Math.sin((angle * Math.PI) / 180) * distance,
              opacity: [0, 1, 0],
              scale: [0, 1.5, 0],
              rotate: angle * 3,
            }}
            transition={{
              duration: 1.2,
              delay,
              ease: 'easeOut',
            }}
          />
        )
      })}
    </div>
  )
}

// ── Rainbow border for LEGENDARY ──────────────────────────────────────────────

const RainbowBorder: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <div className="relative p-[1.5px] rounded-2xl overflow-hidden">
    <motion.div
      className="absolute inset-0 rounded-2xl"
      animate={{ backgroundPosition: ['0% 50%', '100% 50%', '0% 50%'] }}
      transition={{ repeat: Infinity, duration: 2.5, ease: 'linear' }}
      style={{
        background:
          'linear-gradient(90deg, #FF0000, #FF8800, #FFD700, #00FF88, #22D3EE, #BD93F9, #FF79C6, #FF0000)',
        backgroundSize: '300% 100%',
      }}
    />
    <div className="relative rounded-2xl overflow-hidden">{children}</div>
  </div>
)

// ── XP gain animation ─────────────────────────────────────────────────────────

const XpGainDisplay: React.FC<{ xp: number }> = ({ xp }) => {
  const [displayed, setDisplayed] = useState(0)

  useEffect(() => {
    let frame: number
    let start: number | null = null
    const duration = 600

    const tick = (ts: number) => {
      if (start === null) start = ts
      const elapsed = ts - start
      const progress = Math.min(elapsed / duration, 1)
      const eased = 1 - Math.pow(1 - progress, 3)
      setDisplayed(Math.round(xp * eased))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }

    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [xp])

  return (
    <span className="font-black text-[#10B981]">+{displayed} XP</span>
  )
}

// ── Progress bar countdown ────────────────────────────────────────────────────

const CountdownBar: React.FC<{ durationMs: number }> = ({ durationMs }) => (
  <div className="h-0.5 bg-[#18181C] rounded-full overflow-hidden">
    <motion.div
      className="h-full bg-[#6366F1] rounded-full"
      initial={{ width: '100%' }}
      animate={{ width: '0%' }}
      transition={{ duration: durationMs / 1000, ease: 'linear' }}
    />
  </div>
)

// ── Main component ────────────────────────────────────────────────────────────

const AUTO_DISMISS_MS = 5000

const AchievementToast: React.FC<AchievementToastProps> = ({
  badge,
  xpGain,
  onDismiss,
}) => {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    if (!badge) return

    timerRef.current = setTimeout(onDismiss, AUTO_DISMISS_MS)
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [badge, onDismiss])

  const handleDismiss = useCallback(() => {
    if (timerRef.current) clearTimeout(timerRef.current)
    onDismiss()
  }, [onDismiss])

  if (!badge) return null

  const config = RARITY_CONFIG[badge.rarity]
  const showRainbow = badge.rarity === 'LEGENDARY'

  const inner = (
    <motion.div
      initial={{ x: 80, opacity: 0, scale: 0.85 }}
      animate={{ x: 0, opacity: 1, scale: 1 }}
      exit={{ x: 80, opacity: 0, scale: 0.85 }}
      transition={{ type: 'spring', damping: 22, stiffness: 320 }}
      className={`relative w-80 bg-gradient-to-br ${config.bgGradient} rounded-2xl p-4 border ${
        showRainbow ? '' : config.borderStyle
      } ${config.glowClass} overflow-hidden`}
    >
      {/* Particles */}
      {config.showParticles && <ParticleBurst rarity={badge.rarity} />}

      {/* Dismiss button */}
      <button
        onClick={handleDismiss}
        className="absolute top-3 right-3 p-1 rounded-md text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C] transition-colors z-10"
      >
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
          <path
            d="M1 1l10 10M11 1L1 11"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
          />
        </svg>
      </button>

      {/* Content */}
      <div className="flex items-start gap-3 relative z-10">
        {/* Badge icon */}
        <motion.div
          animate={
            badge.rarity === 'LEGENDARY'
              ? {
                  scale: [1, 1.1, 1],
                  rotate: [-5, 5, -5, 0],
                }
              : badge.rarity === 'EPIC'
              ? { scale: [1, 1.08, 1] }
              : {}
          }
          transition={{ repeat: Infinity, duration: 2, ease: 'easeInOut' }}
          className="text-4xl shrink-0 leading-none select-none"
        >
          {badge.icon}
        </motion.div>

        <div className="flex-1 min-w-0">
          {/* Achievement unlocked title */}
          <div className="text-[10px] font-bold text-[#475569] uppercase tracking-widest mb-0.5">
            Achievement Unlocked!
          </div>

          {/* Badge name */}
          <div className={`text-sm font-bold ${config.textColor} mb-0.5 leading-tight`}>
            {badge.name}
          </div>

          {/* Rarity tag */}
          <div className={`text-[10px] font-semibold ${config.textColor} opacity-70 mb-1`}>
            {config.label}
          </div>

          {/* Description */}
          <p className="text-xs text-[#94A3B8] leading-snug mb-2">
            {badge.description}
          </p>

          {/* XP gain */}
          <div className="text-sm font-medium">
            <XpGainDisplay xp={xpGain} />
          </div>
        </div>
      </div>

      {/* Countdown bar */}
      <div className="mt-3 relative z-10">
        <CountdownBar durationMs={AUTO_DISMISS_MS} />
      </div>
    </motion.div>
  )

  return (
    <div className="fixed top-4 right-4 z-[9999]">
      <AnimatePresence mode="wait">
        {badge && (
          <div key={badge.id}>
            {showRainbow ? <RainbowBorder>{inner}</RainbowBorder> : inner}
          </div>
        )}
      </AnimatePresence>
    </div>
  )
}

export default AchievementToast
