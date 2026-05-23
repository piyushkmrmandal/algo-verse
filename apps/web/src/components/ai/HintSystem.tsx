import React, { useCallback, useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Hint {
  level: number
  text: string
  revealedAt: Date
}

interface HintSystemProps {
  problemId: string
  currentCode: string
  language: string
  onMasteryConfirmed?: () => void
  onHintUsed?: (level: number) => void
}

// ── XP cost per hint level ─────────────────────────────────────────────────────

const XP_COST_PER_LEVEL = [0, 10, 15, 20, 25, 30] // index = hint level 1..5

// ── Typewriter hook ───────────────────────────────────────────────────────────

const useTypewriter = (text: string, speed = 28): { displayed: string; done: boolean } => {
  const [displayed, setDisplayed] = useState('')
  const [done, setDone] = useState(false)
  const indexRef = useRef(0)

  useEffect(() => {
    setDisplayed('')
    setDone(false)
    indexRef.current = 0

    if (!text) {
      setDone(true)
      return
    }

    const interval = setInterval(() => {
      indexRef.current += 1
      setDisplayed(text.slice(0, indexRef.current))
      if (indexRef.current >= text.length) {
        clearInterval(interval)
        setDone(true)
      }
    }, speed)

    return () => clearInterval(interval)
  }, [text, speed])

  return { displayed, done }
}

// ── Lightbulb icon ────────────────────────────────────────────────────────────

const LightbulbIcon: React.FC<{ lit?: boolean }> = ({ lit = false }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
    <path
      d="M12 2a7 7 0 0 1 5 11.9V17a2 2 0 0 1-2 2h-6a2 2 0 0 1-2-2v-3.1A7 7 0 0 1 12 2Z"
      stroke={lit ? '#F59E0B' : '#475569'}
      strokeWidth="1.5"
      fill={lit ? '#F59E0B15' : 'none'}
    />
    <path
      d="M9 21h6M10 17v-3M14 17v-3"
      stroke={lit ? '#F59E0B' : '#475569'}
      strokeWidth="1.5"
      strokeLinecap="round"
    />
  </svg>
)

// ── Hint level bar ────────────────────────────────────────────────────────────

const HintLevelBar: React.FC<{ current: number; max?: number }> = ({
  current,
  max = 5,
}) => (
  <div className="flex items-center gap-2">
    <span className="text-xs text-[#475569] font-medium w-16 shrink-0">
      Level {current}/{max}
    </span>
    <div className="flex-1 flex items-center gap-1">
      {Array.from({ length: max }, (_, i) => (
        <motion.div
          key={i}
          className="h-1.5 flex-1 rounded-full"
          initial={false}
          animate={{
            backgroundColor:
              i < current
                ? i < 2
                  ? '#10B981'
                  : i < 4
                  ? '#F59E0B'
                  : '#EF4444'
                : '#18181C',
          }}
          transition={{ duration: 0.3, delay: i * 0.05 }}
        />
      ))}
    </div>
    {current >= max && (
      <span className="text-[10px] text-[#EF4444] shrink-0">Max</span>
    )}
  </div>
)

// ── Component ─────────────────────────────────────────────────────────────────

const HintSystem: React.FC<HintSystemProps> = ({
  problemId,
  currentCode,
  language,
  onMasteryConfirmed,
  onHintUsed,
}) => {
  const MAX_LEVEL = 5
  const [currentLevel, setCurrentLevel] = useState(0)
  const [hints, setHints] = useState<Hint[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [showHistory, setShowHistory] = useState(false)
  const [showMasteryConfirm, setShowMasteryConfirm] = useState(false)
  const [xpWarningVisible, setXpWarningVisible] = useState(false)

  const latestHint = hints[hints.length - 1]
  const { displayed: typedText, done: typingDone } = useTypewriter(
    latestHint?.text ?? '',
    20,
  )

  const totalXpCost = hints.reduce((sum, h) => sum + (XP_COST_PER_LEVEL[h.level] ?? 0), 0)

  const fetchNextHint = useCallback(async () => {
    if (currentLevel >= MAX_LEVEL || isLoading) return

    setIsLoading(true)
    try {
      const response = await fetch(`/api/problems/${problemId}/hints`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          level: currentLevel + 1,
          code: currentCode,
          language,
          hintsAlreadySeen: hints.map((h) => h.level),
        }),
      })

      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const data = (await response.json()) as { hint: string }

      const nextLevel = currentLevel + 1
      const newHint: Hint = {
        level: nextLevel,
        text: data.hint,
        revealedAt: new Date(),
      }

      setCurrentLevel(nextLevel)
      setHints((prev) => [...prev, newHint])
      onHintUsed?.(nextLevel)
    } catch {
      // In development / no API: show placeholder hints
      const nextLevel = currentLevel + 1
      const placeholders: Record<number, string> = {
        1: 'Think about what data structure would allow O(1) lookups.',
        2: 'Consider using a **hash map** to store elements you have already seen. What would you store as the key, and what as the value?',
        3: 'For each element `nums[i]`, the complement you need is `target - nums[i]`. Check if that complement already exists in your map.',
        4: 'Iterate once through the array. For each element, check the map, and if not found, insert the current element with its index.',
        5: 'Here is the complete approach:\n```python\ndef twoSum(nums, target):\n    seen = {}\n    for i, n in enumerate(nums):\n        diff = target - n\n        if diff in seen:\n            return [seen[diff], i]\n        seen[n] = i\n```',
      }
      const newHint: Hint = {
        level: nextLevel,
        text: placeholders[nextLevel] ?? 'No further hints available.',
        revealedAt: new Date(),
      }
      setCurrentLevel(nextLevel)
      setHints((prev) => [...prev, newHint])
      onHintUsed?.(nextLevel)
    } finally {
      setIsLoading(false)
      setXpWarningVisible(false)
    }
  }, [currentLevel, isLoading, problemId, currentCode, language, hints, onHintUsed])

  const handleNextHintClick = () => {
    if (!xpWarningVisible) {
      setXpWarningVisible(true)
    } else {
      fetchNextHint()
    }
  }

  return (
    <div className="flex flex-col gap-4 p-4 bg-[#111113] rounded-xl border border-[#18181C] h-full overflow-auto">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <LightbulbIcon lit={currentLevel > 0} />
          <span className="text-sm font-semibold text-[#F8F8F2]">Hint System</span>
        </div>
        {totalXpCost > 0 && (
          <span className="text-xs text-[#F59E0B] bg-[#F59E0B10] border border-[#F59E0B30] px-2 py-0.5 rounded-full">
            -{totalXpCost} XP used
          </span>
        )}
      </div>

      {/* Level bar */}
      <HintLevelBar current={currentLevel} max={MAX_LEVEL} />

      {/* Current hint card */}
      <AnimatePresence mode="wait">
        {latestHint ? (
          <motion.div
            key={latestHint.level}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.25 }}
            className="relative rounded-xl bg-[#18181C] border border-[#F59E0B]/20 p-4 text-sm text-[#F8F8F2] leading-relaxed min-h-[80px]"
          >
            <div className="absolute -top-2.5 left-3">
              <span className="text-[10px] font-bold text-[#F59E0B] bg-[#18181C] px-2 uppercase tracking-widest">
                Hint {latestHint.level}
              </span>
            </div>
            <div className="prose prose-invert prose-sm max-w-none mt-1">
              <p className="whitespace-pre-wrap text-[#94A3B8]">
                {typedText}
                {!typingDone && (
                  <motion.span
                    animate={{ opacity: [1, 0] }}
                    transition={{ repeat: Infinity, duration: 0.5 }}
                    className="inline-block w-0.5 h-3.5 bg-[#F59E0B] ml-0.5 align-middle"
                  />
                )}
              </p>
            </div>
          </motion.div>
        ) : (
          <motion.div
            key="no-hint"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="rounded-xl bg-[#18181C] border border-dashed border-[#475569]/30 p-6 text-center"
          >
            <LightbulbIcon lit={false} />
            <p className="text-sm text-[#475569] mt-2">
              No hints used yet. Try solving it yourself first!
            </p>
          </motion.div>
        )}
      </AnimatePresence>

      {/* XP warning */}
      <AnimatePresence>
        {xpWarningVisible && currentLevel < MAX_LEVEL && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="rounded-lg bg-[#F59E0B]/8 border border-[#F59E0B]/25 px-3 py-2.5 text-xs text-[#F59E0B] flex items-center gap-2"
          >
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path
                d="M7 1L13 13H1L7 1Z"
                stroke="currentColor"
                strokeWidth="1.2"
                strokeLinejoin="round"
              />
              <path d="M7 5.5v3M7 10.5v.5" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
            </svg>
            Using hint level {currentLevel + 1} will reduce your XP reward by{' '}
            <strong>{XP_COST_PER_LEVEL[currentLevel + 1]} XP</strong>. Click again to confirm.
          </motion.div>
        )}
      </AnimatePresence>

      {/* Action buttons */}
      <div className="flex items-center gap-2 flex-wrap">
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={handleNextHintClick}
          disabled={currentLevel >= MAX_LEVEL || isLoading}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-[#F59E0B]/10 text-[#F59E0B] border border-[#F59E0B]/25 hover:bg-[#F59E0B]/20 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
        >
          {isLoading ? (
            <motion.span
              animate={{ rotate: 360 }}
              transition={{ repeat: Infinity, duration: 0.8, ease: 'linear' }}
              className="inline-block"
            >
              ⟳
            </motion.span>
          ) : (
            <LightbulbIcon lit />
          )}
          <span>
            {currentLevel >= MAX_LEVEL
              ? 'No more hints'
              : xpWarningVisible
              ? 'Confirm — get hint'
              : `Next Hint (${XP_COST_PER_LEVEL[currentLevel + 1] ?? 0} XP)`}
          </span>
        </motion.button>

        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={() => setShowMasteryConfirm(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-[#10B981] border border-[#10B981]/25 hover:bg-[#10B981]/10 transition-all"
        >
          <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M1 7l4 4 7-7" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          I don&apos;t need more hints
        </motion.button>
      </div>

      {/* Mastery confirm */}
      <AnimatePresence>
        {showMasteryConfirm && (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="rounded-xl bg-[#10B981]/8 border border-[#10B981]/25 p-4 text-sm text-[#94A3B8] space-y-3"
          >
            <p>
              Confirming mastery signals to our skill model that you understood this
              problem without further hints. This improves your recommendations.
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => {
                  setShowMasteryConfirm(false)
                  onMasteryConfirmed?.()
                }}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#10B981] text-white hover:bg-[#0ea272] transition-colors"
              >
                Yes, I&apos;ve got it!
              </button>
              <button
                onClick={() => setShowMasteryConfirm(false)}
                className="px-3 py-1.5 rounded-lg text-xs text-[#475569] hover:text-[#94A3B8] transition-colors"
              >
                Cancel
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Hint history */}
      {hints.length > 0 && (
        <div className="border-t border-[#18181C] pt-3">
          <button
            onClick={() => setShowHistory((h) => !h)}
            className="flex items-center gap-1.5 text-xs text-[#475569] hover:text-[#94A3B8] transition-colors"
          >
            <motion.svg
              width="10"
              height="10"
              viewBox="0 0 10 10"
              animate={{ rotate: showHistory ? 90 : 0 }}
              transition={{ duration: 0.15 }}
            >
              <path d="M3 2l4 3-4 3" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" strokeLinejoin="round" fill="none" />
            </motion.svg>
            Hint history ({hints.length})
          </button>

          <AnimatePresence>
            {showHistory && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="mt-2 space-y-2">
                  {hints.map((hint) => (
                    <div
                      key={hint.level}
                      className="rounded-lg bg-[#0A0A0B] border border-[#18181C] px-3 py-2"
                    >
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-[10px] font-bold text-[#F59E0B] uppercase tracking-widest">
                          Level {hint.level}
                        </span>
                        <span className="text-[10px] text-[#475569]">
                          {hint.revealedAt.toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                          })}
                        </span>
                      </div>
                      <p className="text-xs text-[#94A3B8] leading-relaxed line-clamp-2">
                        {hint.text}
                      </p>
                    </div>
                  ))}
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  )
}

export default HintSystem
