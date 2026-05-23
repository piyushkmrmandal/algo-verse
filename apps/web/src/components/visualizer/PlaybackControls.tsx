import React, { useCallback, useRef } from 'react'
import { motion } from 'framer-motion'

interface PlaybackControlsProps {
  currentStep: number
  totalSteps: number
  isPlaying: boolean
  playbackSpeed: 0.25 | 0.5 | 1 | 2 | 4
  onPlay: () => void
  onPause: () => void
  onReset: () => void
  onStepForward: () => void
  onStepBackward: () => void
  onSpeedChange: (speed: 0.25 | 0.5 | 1 | 2 | 4) => void
  onSeek: (step: number) => void
}

const SPEED_OPTIONS: { label: string; value: 0.25 | 0.5 | 1 | 2 | 4 }[] = [
  { label: '0.25×', value: 0.25 },
  { label: '0.5×', value: 0.5 },
  { label: '1×', value: 1 },
  { label: '2×', value: 2 },
  { label: '4×', value: 4 },
]

export const PlaybackControls: React.FC<PlaybackControlsProps> = ({
  currentStep,
  totalSteps,
  isPlaying,
  playbackSpeed,
  onPlay,
  onPause,
  onReset,
  onStepForward,
  onStepBackward,
  onSpeedChange,
  onSeek,
}) => {
  const scrubberRef = useRef<HTMLDivElement>(null)

  const progress = totalSteps > 1 ? currentStep / (totalSteps - 1) : 0

  const handleScrubberClick = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      if (!scrubberRef.current || totalSteps <= 1) return
      const rect = scrubberRef.current.getBoundingClientRect()
      const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
      const targetStep = Math.round(ratio * (totalSteps - 1))
      onSeek(targetStep)
    },
    [totalSteps, onSeek],
  )

  const handleScrubberKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>) => {
      if (e.key === 'ArrowRight') onStepForward()
      if (e.key === 'ArrowLeft') onStepBackward()
    },
    [onStepForward, onStepBackward],
  )

  return (
    <div className="flex flex-col gap-2 px-4 py-3 bg-[#111113]/80 backdrop-blur-sm border-t border-white/5 rounded-b-xl select-none">
      {/* Progress Scrubber */}
      <div
        ref={scrubberRef}
        className="relative w-full h-2 bg-white/10 rounded-full cursor-pointer group"
        onClick={handleScrubberClick}
        onKeyDown={handleScrubberKeyDown}
        role="slider"
        aria-valuemin={0}
        aria-valuemax={Math.max(0, totalSteps - 1)}
        aria-valuenow={currentStep}
        tabIndex={0}
        aria-label="Playback scrubber"
      >
        {/* Filled track */}
        <div
          className="absolute inset-y-0 left-0 bg-[#7C3AED] rounded-full transition-none"
          style={{ width: `${progress * 100}%` }}
        />
        {/* Thumb */}
        <motion.div
          className="absolute top-1/2 -translate-y-1/2 w-3 h-3 bg-white rounded-full shadow-lg shadow-purple-500/50 group-hover:scale-125 transition-transform"
          style={{ left: `calc(${progress * 100}% - 6px)` }}
          layout
        />
      </div>

      {/* Controls Row */}
      <div className="flex items-center justify-between gap-3">
        {/* Step Counter */}
        <span className="text-xs font-mono text-white/50 tabular-nums min-w-[88px]">
          Step{' '}
          <span className="text-[#A78BFA] font-semibold">
            {currentStep.toLocaleString()}
          </span>{' '}
          /{' '}
          <span className="text-white/40">
            {Math.max(0, totalSteps - 1).toLocaleString()}
          </span>
        </span>

        {/* Transport Buttons */}
        <div className="flex items-center gap-1">
          {/* Reset to start */}
          <ControlButton
            onClick={onReset}
            aria-label="Go to start"
            title="Go to start (Home)"
          >
            <SkipBackIcon />
          </ControlButton>

          {/* Step backward */}
          <ControlButton
            onClick={onStepBackward}
            disabled={currentStep === 0}
            aria-label="Step backward"
            title="Step backward (←)"
          >
            <StepBackIcon />
          </ControlButton>

          {/* Play / Pause */}
          <motion.button
            onClick={isPlaying ? onPause : onPlay}
            whileTap={{ scale: 0.92 }}
            className="flex items-center justify-center w-9 h-9 rounded-full bg-[#7C3AED] hover:bg-[#6D28D9] active:bg-[#5B21B6] text-white shadow-lg shadow-purple-900/40 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-[#7C3AED]/70"
            aria-label={isPlaying ? 'Pause' : 'Play'}
            title={isPlaying ? 'Pause (Space)' : 'Play (Space)'}
          >
            {isPlaying ? <PauseIcon /> : <PlayIcon />}
          </motion.button>

          {/* Step forward */}
          <ControlButton
            onClick={onStepForward}
            disabled={currentStep >= totalSteps - 1}
            aria-label="Step forward"
            title="Step forward (→)"
          >
            <StepForwardIcon />
          </ControlButton>

          {/* Skip to end */}
          <ControlButton
            onClick={() => onSeek(Math.max(0, totalSteps - 1))}
            aria-label="Go to end"
            title="Go to end (End)"
          >
            <SkipForwardIcon />
          </ControlButton>
        </div>

        {/* Speed Selector */}
        <div className="relative">
          <select
            value={playbackSpeed}
            onChange={(e) => onSpeedChange(Number(e.target.value) as 0.25 | 0.5 | 1 | 2 | 4)}
            className="appearance-none bg-white/5 hover:bg-white/10 border border-white/10 text-white/70 text-xs font-mono rounded-md px-3 py-1.5 pr-6 cursor-pointer focus:outline-none focus-visible:ring-1 focus-visible:ring-[#7C3AED]/60 transition-colors"
            aria-label="Playback speed"
            title="Playback speed"
          >
            {SPEED_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value} className="bg-[#111113]">
                {opt.label}
              </option>
            ))}
          </select>
          <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-white/40 text-[10px]">
            ▾
          </span>
        </div>
      </div>

      {/* Keyboard hint */}
      <p className="text-[10px] text-white/20 text-center">
        <kbd className="px-1 py-0.5 rounded bg-white/5 text-white/30 font-mono">←</kbd>
        {' '}prev{' '}
        <kbd className="px-1 py-0.5 rounded bg-white/5 text-white/30 font-mono">→</kbd>
        {' '}next{' '}
        <kbd className="px-1 py-0.5 rounded bg-white/5 text-white/30 font-mono">Space</kbd>
        {' '}play/pause{' '}
        <kbd className="px-1 py-0.5 rounded bg-white/5 text-white/30 font-mono">R</kbd>
        {' '}reset
      </p>
    </div>
  )
}

// ─── Sub-components ──────────────────────────────────────────────────────────

interface ControlButtonProps {
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
  'aria-label'?: string
  title?: string
}

const ControlButton: React.FC<ControlButtonProps> = ({
  onClick,
  disabled = false,
  children,
  'aria-label': ariaLabel,
  title,
}) => (
  <motion.button
    onClick={onClick}
    disabled={disabled}
    whileTap={disabled ? {} : { scale: 0.9 }}
    className={[
      'flex items-center justify-center w-8 h-8 rounded-lg transition-colors',
      'focus:outline-none focus-visible:ring-1 focus-visible:ring-white/30',
      disabled
        ? 'text-white/15 cursor-not-allowed'
        : 'text-white/60 hover:text-white hover:bg-white/8 active:bg-white/12',
    ].join(' ')}
    aria-label={ariaLabel}
    title={title}
  >
    {children}
  </motion.button>
)

// ─── Icons ───────────────────────────────────────────────────────────────────

const PlayIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <path d="M2.5 1.5l10 5.5-10 5.5V1.5z" />
  </svg>
)

const PauseIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <rect x="2" y="1.5" width="3.5" height="11" rx="1" />
    <rect x="8.5" y="1.5" width="3.5" height="11" rx="1" />
  </svg>
)

const SkipBackIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <rect x="1.5" y="1.5" width="2" height="11" rx="1" />
    <path d="M11.5 1.5L4 7l7.5 5.5V1.5z" />
  </svg>
)

const SkipForwardIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <rect x="10.5" y="1.5" width="2" height="11" rx="1" />
    <path d="M2.5 1.5L10 7l-7.5 5.5V1.5z" />
  </svg>
)

const StepBackIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <path d="M8 2L3 7l5 5V2z" />
    <path d="M12.5 2L7.5 7l5 5V2z" />
  </svg>
)

const StepForwardIcon = () => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="currentColor">
    <path d="M6 2l5 5-5 5V2z" />
    <path d="M1.5 2l5 5-5 5V2z" />
  </svg>
)
