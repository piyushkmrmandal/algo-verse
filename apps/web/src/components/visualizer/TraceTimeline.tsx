import React, { useCallback, useRef } from 'react'
import { motion } from 'framer-motion'
import type { ExecutionFrame } from '@algoverse/ast-parser'

interface TraceTimelineProps {
  frames: ExecutionFrame[]
  currentStep: number
  onSeek: (step: number) => void
}

// Group event types into colour categories for the mini-map
const EVENT_COLOR: Record<string, string> = {
  'loop-enter': '#F59E0B',
  'loop-iteration': '#F59E0B',
  'loop-exit': '#F59E0B',
  'function-call': '#A78BFA',
  'function-return': '#A78BFA',
  'recursion-enter': '#C4B5FD',
  'recursion-base': '#C4B5FD',
  'recursion-return': '#C4B5FD',
  'variable-declare': '#34D399',
  'variable-assign': '#34D399',
  'array-swap': '#F472B6',
  'array-compare': '#FBBF24',
  'array-write': '#60A5FA',
  'array-read': '#60A5FA',
  'condition-eval': '#22D3EE',
  'tree-visit': '#7C3AED',
  'graph-visit': '#6366F1',
}

export const TraceTimeline: React.FC<TraceTimelineProps> = React.memo(
  ({ frames, currentStep, onSeek }) => {
    const railRef = useRef<HTMLDivElement>(null)

    const handleClick = useCallback(
      (e: React.MouseEvent<HTMLDivElement>) => {
        if (!railRef.current || frames.length <= 1) return
        const rect = railRef.current.getBoundingClientRect()
        const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
        onSeek(Math.round(ratio * (frames.length - 1)))
      },
      [frames.length, onSeek],
    )

    if (frames.length === 0) return null

    const progress = frames.length > 1 ? currentStep / (frames.length - 1) : 0

    return (
      <div className="flex flex-col gap-1 px-4 py-2 bg-[#0D0D0F]">
        {/* Event type legend */}
        <div className="flex flex-wrap gap-x-3 gap-y-0.5 mb-1">
          {(['loop-iteration', 'function-call', 'array-swap', 'condition-eval', 'tree-visit'] as const).map(
            (evType) => (
              <span key={evType} className="flex items-center gap-1 text-[9px] text-white/30">
                <span
                  className="w-2 h-2 rounded-sm"
                  style={{ backgroundColor: EVENT_COLOR[evType] ?? '#6B7280' }}
                />
                {evType.replace(/-/g, ' ')}
              </span>
            ),
          )}
        </div>

        {/* Mini-map rail */}
        <div
          ref={railRef}
          className="relative w-full h-5 bg-[#111113] rounded cursor-pointer overflow-hidden"
          onClick={handleClick}
          role="slider"
          aria-valuemin={0}
          aria-valuemax={Math.max(0, frames.length - 1)}
          aria-valuenow={currentStep}
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'ArrowRight') onSeek(Math.min(currentStep + 1, frames.length - 1))
            if (e.key === 'ArrowLeft') onSeek(Math.max(currentStep - 1, 0))
          }}
          aria-label="Trace timeline"
        >
          {/* Event dots — sample every N frames to avoid render overload */}
          {frames.map((frame, idx) => {
            // Sample: show every 2nd tick for large traces
            if (frames.length > 200 && idx % 2 !== 0) return null
            const color = EVENT_COLOR[frame.event.type] ?? '#4B5563'
            const leftPct = frames.length > 1 ? (idx / (frames.length - 1)) * 100 : 0
            return (
              <div
                key={idx}
                className="absolute top-1 w-0.5 h-3 rounded-sm opacity-70"
                style={{
                  left: `${leftPct}%`,
                  backgroundColor: color,
                }}
              />
            )
          })}

          {/* Current position indicator */}
          <motion.div
            className="absolute top-0 bottom-0 w-0.5 bg-white/80"
            style={{ left: `${progress * 100}%` }}
            layout
            transition={{ type: 'spring', stiffness: 500, damping: 40 }}
          />

          {/* Hover highlight */}
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/2 to-transparent pointer-events-none" />
        </div>

        {/* Current event explanation */}
        {frames[currentStep] && (
          <p className="text-[10px] text-white/35 truncate">
            <span
              className="font-semibold"
              style={{ color: EVENT_COLOR[frames[currentStep].event.type] ?? '#9CA3AF' }}
            >
              {frames[currentStep].event.type}
            </span>
            {frames[currentStep].explanation && (
              <>
                {'  ·  '}
                {frames[currentStep].explanation}
              </>
            )}
          </p>
        )}
      </div>
    )
  },
)

TraceTimeline.displayName = 'TraceTimeline'
