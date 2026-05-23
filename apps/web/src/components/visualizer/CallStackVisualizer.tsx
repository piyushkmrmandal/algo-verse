import React, { useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import type { StackFrame } from '@algoverse/ast-parser'
import { useVisualizerStore } from '../../stores/visualizer-store'

interface CallStackVisualizerProps {
  callStack: StackFrame[]
}

const MAX_VISIBLE_FRAMES = 8
const COLLAPSE_THRESHOLD = 10

export const CallStackVisualizer: React.FC<CallStackVisualizerProps> = ({ callStack }) => {
  const expandedFrames = useVisualizerStore((s) => s.expandedFrames)
  const toggleFrameExpanded = useVisualizerStore((s) => s.toggleFrameExpanded)

  // Active frame is always the last (top of stack)
  const activeFrameId = callStack.length > 0 ? callStack[callStack.length - 1].frameId : null

  // Determine which frames to show when there are too many
  const shouldCollapse = callStack.length > COLLAPSE_THRESHOLD
  let visibleFrames: (StackFrame | null)[] = []
  let collapsedCount = 0

  if (shouldCollapse) {
    const topN = Math.ceil(MAX_VISIBLE_FRAMES / 2)
    const bottomN = MAX_VISIBLE_FRAMES - topN
    collapsedCount = callStack.length - topN - bottomN
    visibleFrames = [
      ...callStack.slice(0, bottomN),
      null, // sentinel = collapsed divider
      ...callStack.slice(callStack.length - topN),
    ]
  } else {
    visibleFrames = [...callStack]
  }

  // Render from bottom (oldest) to top (newest), so reverse for visual stacking
  const displayFrames = [...visibleFrames].reverse()

  return (
    <div className="flex flex-col gap-1 p-2 h-full overflow-y-auto">
      {/* Header */}
      <div className="flex items-center justify-between px-1 mb-1">
        <span className="text-[11px] font-semibold text-white/40 uppercase tracking-widest">
          Call Stack
        </span>
        <span className="text-[11px] font-mono text-white/30">
          depth{' '}
          <span className="text-[#A78BFA]">{callStack.length}</span>
        </span>
      </div>

      {callStack.length === 0 && (
        <div className="flex items-center justify-center h-20 text-xs text-white/20">
          No active frames
        </div>
      )}

      <AnimatePresence initial={false} mode="sync">
        {displayFrames.map((frame, idx) => {
          // Collapsed divider
          if (frame === null) {
            return (
              <motion.div
                key="collapsed-divider"
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                className="flex items-center gap-2 px-3 py-1.5 rounded bg-white/3"
              >
                <span className="text-xs text-white/30 italic">
                  ···  {collapsedCount} more frame{collapsedCount !== 1 ? 's' : ''} collapsed  ···
                </span>
              </motion.div>
            )
          }

          const isActive = frame.frameId === activeFrameId
          const isExpanded = expandedFrames.has(frame.frameId)

          return (
            <StackFrameItem
              key={frame.frameId}
              frame={frame}
              isActive={isActive}
              isExpanded={isExpanded}
              onToggle={toggleFrameExpanded}
            />
          )
        })}
      </AnimatePresence>
    </div>
  )
}

// ─── StackFrameItem ──────────────────────────────────────────────────────────

interface StackFrameItemProps {
  frame: StackFrame
  isActive: boolean
  isExpanded: boolean
  onToggle: (frameId: string) => void
}

const StackFrameItem: React.FC<StackFrameItemProps> = React.memo(
  ({ frame, isActive, isExpanded, onToggle }) => {
    const handleClick = useCallback(() => {
      onToggle(frame.frameId)
    }, [frame.frameId, onToggle])

    const handleKeyDown = useCallback(
      (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onToggle(frame.frameId)
        }
      },
      [frame.frameId, onToggle],
    )

    const depthLabel = frame.depth > 0 ? `×${frame.depth}` : ''

    return (
      <motion.div
        layout
        initial={{ opacity: 0, y: -8, scale: 0.97 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        exit={{ opacity: 0, y: 8, scale: 0.97 }}
        transition={{ type: 'spring', stiffness: 400, damping: 35 }}
        className={[
          'rounded-lg border cursor-pointer transition-colors overflow-hidden',
          isActive
            ? 'border-[#7C3AED]/60 bg-[#7C3AED]/10 shadow-lg shadow-purple-900/20'
            : 'border-white/5 bg-white/3 hover:bg-white/5',
        ].join(' ')}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        role="button"
        tabIndex={0}
        aria-expanded={isExpanded}
        aria-label={`${frame.functionName} frame, line ${frame.line}`}
      >
        {/* Frame header */}
        <div className="flex items-center gap-2 px-3 py-2">
          {/* Active glow dot */}
          {isActive && (
            <motion.span
              className="w-1.5 h-1.5 rounded-full bg-[#7C3AED] flex-shrink-0"
              animate={{ opacity: [1, 0.3, 1] }}
              transition={{ repeat: Infinity, duration: 1.6, ease: 'easeInOut' }}
            />
          )}

          {/* Function name */}
          <span
            className={[
              'flex-1 text-xs font-mono font-semibold truncate',
              isActive ? 'text-[#C4B5FD]' : 'text-white/70',
            ].join(' ')}
          >
            {frame.functionName}
            {depthLabel && (
              <span className="ml-1 text-[10px] text-[#7C3AED] font-bold">{depthLabel}</span>
            )}
          </span>

          {/* Line number */}
          <span className="text-[10px] font-mono text-white/30 flex-shrink-0">
            :{frame.line}
          </span>

          {/* Expand chevron */}
          {frame.variables.length > 0 && (
            <motion.span
              className="text-white/30 text-[10px] flex-shrink-0"
              animate={{ rotate: isExpanded ? 180 : 0 }}
              transition={{ duration: 0.2 }}
            >
              ▾
            </motion.span>
          )}
        </div>

        {/* Variable summary when not expanded */}
        {!isExpanded && frame.variables.length > 0 && (
          <div className="px-3 pb-2 flex flex-wrap gap-1">
            {frame.variables.slice(0, 4).map((v) => (
              <span
                key={v.name}
                className="text-[10px] font-mono bg-white/5 rounded px-1.5 py-0.5 text-white/40"
              >
                {v.name}
                <span className="text-white/20">=</span>
                <span className={v.changed ? 'text-[#34D399]' : 'text-white/40'}>
                  {formatValue(v.value)}
                </span>
              </span>
            ))}
            {frame.variables.length > 4 && (
              <span className="text-[10px] text-white/20 self-center">
                +{frame.variables.length - 4} more
              </span>
            )}
          </div>
        )}

        {/* Expanded variable list */}
        <AnimatePresence>
          {isExpanded && frame.variables.length > 0 && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.2 }}
              className="border-t border-white/5 divide-y divide-white/3"
            >
              {frame.variables.map((v) => (
                <div
                  key={v.name}
                  className="flex items-center justify-between px-3 py-1.5 gap-2"
                >
                  <span className="flex items-center gap-1.5 min-w-0">
                    {v.isLoopCounter && (
                      <span className="text-[9px] text-[#F59E0B] font-bold uppercase tracking-widest flex-shrink-0">
                        i
                      </span>
                    )}
                    <span className="text-[10px] font-mono text-[#C4B5FD] truncate">
                      {v.name}
                    </span>
                    <span className="text-[9px] text-white/20 flex-shrink-0">{v.type}</span>
                  </span>
                  <span
                    className={[
                      'text-[10px] font-mono truncate max-w-[100px] text-right',
                      v.changed ? 'text-[#34D399]' : 'text-white/50',
                    ].join(' ')}
                    title={String(v.value)}
                  >
                    {formatValue(v.value)}
                  </span>
                </div>
              ))}
              {frame.returnValue !== undefined && (
                <div className="flex items-center justify-between px-3 py-1.5 gap-2 bg-[#059669]/10">
                  <span className="text-[10px] font-mono text-[#34D399]">return</span>
                  <span className="text-[10px] font-mono text-[#34D399]">
                    {formatValue(frame.returnValue)}
                  </span>
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    )
  },
)

StackFrameItem.displayName = 'StackFrameItem'

// ─── Utilities ───────────────────────────────────────────────────────────────

function formatValue(value: unknown): string {
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  if (typeof value === 'boolean') return String(value)
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string') return `"${value.length > 12 ? value.slice(0, 12) + '…' : value}"`
  if (Array.isArray(value)) {
    const inner = (value as unknown[]).slice(0, 4).map((v) => formatValue(v)).join(', ')
    return `[${inner}${value.length > 4 ? ', …' : ''}]`
  }
  if (typeof value === 'object') return '{…}'
  return String(value)
}
