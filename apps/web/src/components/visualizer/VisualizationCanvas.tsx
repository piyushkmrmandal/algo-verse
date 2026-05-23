import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import type { TraceSession, ExecutionFrame } from '@algoverse/ast-parser'
import { ArrayVisualizer } from './ArrayVisualizer'
import { TreeVisualizer } from './TreeVisualizer'
import { GraphVisualizer } from './GraphVisualizer'
import { CallStackVisualizer } from './CallStackVisualizer'
import { VariableInspector } from './VariableInspector'
import { PlaybackControls } from './PlaybackControls'
import { TraceTimeline } from './TraceTimeline'
import { PatternBadge } from './PatternBadge'
import { ComplexityDisplay } from './ComplexityDisplay'
import { useVisualizerStore } from '../../stores/visualizer-store'

interface VisualizationCanvasProps {
  traceSession: TraceSession | null
  isLoading: boolean
  onStepChange?: (step: number) => void
  className?: string
}

// Base interval (ms) at 1× speed between auto-play ticks
const BASE_INTERVAL_MS = 600

export const VisualizationCanvas: React.FC<VisualizationCanvasProps> = ({
  traceSession,
  isLoading,
  onStepChange,
  className = '',
}) => {
  // ─── Store slice ─────────────────────────────────────────────────────────
  const currentStep = useVisualizerStore((s) => s.currentStep)
  const isPlaying = useVisualizerStore((s) => s.isPlaying)
  const playbackSpeed = useVisualizerStore((s) => s.playbackSpeed)
  const currentFrame = useVisualizerStore((s) => s.currentFrame)
  const previousFrame = useVisualizerStore((s) => s.previousFrame)
  const layout = useVisualizerStore((s) => s.layout)

  const {
    setTraceSession,
    stepForward,
    stepBackward,
    goToStart,
    goToEnd,
    goToStep,
    play,
    pause,
    setPlaybackSpeed,
    setLayout,
  } = useVisualizerStore()

  // ─── Sync traceSession prop → store ──────────────────────────────────────
  const prevSessionIdRef = useRef<string | null>(null)
  useEffect(() => {
    const incomingId = traceSession?.sessionId ?? null
    if (incomingId !== prevSessionIdRef.current) {
      prevSessionIdRef.current = incomingId
      setTraceSession(traceSession)
    }
  }, [traceSession, setTraceSession])

  // ─── Notify parent of step changes ───────────────────────────────────────
  useEffect(() => {
    if (onStepChange) onStepChange(currentStep)
  }, [currentStep, onStepChange])

  // ─── Auto-play interval ───────────────────────────────────────────────────
  const playIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (playIntervalRef.current) {
      clearInterval(playIntervalRef.current)
      playIntervalRef.current = null
    }

    if (!isPlaying) return

    const intervalMs = BASE_INTERVAL_MS / playbackSpeed
    playIntervalRef.current = setInterval(() => {
      stepForward()
    }, intervalMs)

    return () => {
      if (playIntervalRef.current) clearInterval(playIntervalRef.current)
    }
  }, [isPlaying, playbackSpeed, stepForward])

  // ─── Keyboard shortcuts ───────────────────────────────────────────────────
  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      // Don't steal keypresses from focused input elements
      const tag = (e.target as HTMLElement).tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return

      switch (e.key) {
        case 'ArrowRight':
          e.preventDefault()
          stepForward()
          break
        case 'ArrowLeft':
          e.preventDefault()
          stepBackward()
          break
        case ' ':
          e.preventDefault()
          if (isPlaying) pause()
          else play()
          break
        case 'r':
        case 'R':
          e.preventDefault()
          goToStart()
          break
        case 'End':
          e.preventDefault()
          goToEnd()
          break
        case 'Home':
          e.preventDefault()
          goToStart()
          break
      }
    },
    [isPlaying, stepForward, stepBackward, play, pause, goToStart, goToEnd],
  )

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [handleKeyDown])

  // ─── Layout toggle ────────────────────────────────────────────────────────
  const [showCallStack, setShowCallStack] = useState(true)
  const [showVariables, setShowVariables] = useState(true)

  // ─── Loading skeleton ─────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <div
        className={`relative w-full h-full bg-[#0A0A0B] rounded-xl overflow-hidden flex flex-col gap-3 p-4 ${className}`}
      >
        <div className="flex items-center justify-between">
          <div className="h-4 w-32 bg-white/5 rounded animate-pulse" />
          <div className="h-4 w-24 bg-white/5 rounded animate-pulse" />
        </div>
        <div className="flex-1 grid grid-cols-3 gap-3">
          <div className="col-span-2 bg-white/3 rounded-lg animate-pulse" />
          <div className="bg-white/3 rounded-lg animate-pulse" />
        </div>
        <div className="h-20 bg-white/3 rounded-lg animate-pulse" />
        <div className="h-14 bg-white/3 rounded-lg animate-pulse" />
      </div>
    )
  }

  // ─── Empty state ──────────────────────────────────────────────────────────
  if (!traceSession) {
    return (
      <div
        className={`relative w-full h-full bg-[#0A0A0B] rounded-xl overflow-hidden flex flex-col items-center justify-center gap-4 ${className}`}
      >
        <motion.div
          className="flex flex-col items-center gap-3 text-center px-6"
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          {/* Animated code bracket icon */}
          <motion.div
            className="w-16 h-16 rounded-2xl bg-[#7C3AED]/15 border border-[#7C3AED]/20 flex items-center justify-center"
            animate={{ scale: [1, 1.04, 1] }}
            transition={{ repeat: Infinity, duration: 3, ease: 'easeInOut' }}
          >
            <svg
              width="32"
              height="32"
              viewBox="0 0 32 32"
              fill="none"
              className="text-[#7C3AED]"
            >
              <path
                d="M10 8L4 16L10 24M22 8L28 16L22 24"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
              <path
                d="M18 6L14 26"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                opacity="0.5"
              />
            </svg>
          </motion.div>
          <p className="text-white/50 text-sm font-medium">
            Paste or type your algorithm in the editor
          </p>
          <p className="text-white/25 text-xs max-w-xs">
            The visualization engine will trace execution step-by-step, detecting patterns
            and analyzing complexity automatically.
          </p>
        </motion.div>
      </div>
    )
  }

  const totalSteps = traceSession.frames.length

  // ─── Derived current-frame data ───────────────────────────────────────────
  const frame = currentFrame as ExecutionFrame | null
  const prevFrame = previousFrame as ExecutionFrame | null

  const hasArrays = frame && frame.arrays.size > 0
  const hasTree = !!frame?.tree
  const hasGraph = !!frame?.graph

  return (
    <div
      className={`relative w-full h-full bg-[#0A0A0B] rounded-xl overflow-hidden flex flex-col ${className}`}
    >
      {/* ── Top bar ──────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between gap-3 px-4 py-2.5 border-b border-white/5 bg-[#111113]/60 backdrop-blur-sm flex-shrink-0">
        {/* Patterns + complexity */}
        <div className="flex items-center gap-3 min-w-0 flex-wrap">
          <PatternBadge patterns={traceSession.summary.patterns} />
          <ComplexityDisplay
            timeComplexity={traceSession.summary.timeComplexity}
            spaceComplexity={traceSession.summary.spaceComplexity}
          />
        </div>

        {/* Layout toggles */}
        <div className="flex items-center gap-1 flex-shrink-0">
          <LayoutToggleButton
            active={showCallStack}
            onClick={() => setShowCallStack((v) => !v)}
            title="Toggle call stack"
          >
            Stack
          </LayoutToggleButton>
          <LayoutToggleButton
            active={showVariables}
            onClick={() => setShowVariables((v) => !v)}
            title="Toggle variables"
          >
            Vars
          </LayoutToggleButton>
          <LayoutToggleButton
            active={layout === 'focused-viz'}
            onClick={() =>
              setLayout(layout === 'focused-viz' ? 'split' : 'focused-viz')
            }
            title="Expand visualization"
          >
            ⤢
          </LayoutToggleButton>
        </div>
      </div>

      {/* ── Main content area ─────────────────────────────────────────────── */}
      <div className="flex-1 overflow-hidden flex min-h-0">
        {/* ── Left / main visualization panel ── */}
        <div
          className={[
            'flex flex-col gap-2 p-3 overflow-y-auto flex-1 min-w-0',
            layout === 'focused-viz' ? 'w-full' : '',
          ].join(' ')}
        >
          {/* Current step explanation */}
          <AnimatePresence mode="wait">
            {frame?.explanation && (
              <motion.div
                key={`explanation-${currentStep}`}
                className="px-3 py-2 rounded-lg bg-[#7C3AED]/10 border border-[#7C3AED]/20 text-xs text-[#C4B5FD]"
                initial={{ opacity: 0, y: -4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: 4 }}
                transition={{ duration: 0.2 }}
              >
                <span className="font-semibold text-[#A78BFA]">
                  {frame.event.type}
                </span>
                {'  ·  '}
                {frame.explanation}
              </motion.div>
            )}
          </AnimatePresence>

          {/* Arrays */}
          {hasArrays && (
            <VisualizerPanel title="Arrays">
              <ArrayVisualizer
                arrays={frame!.arrays}
                prevArrays={prevFrame?.arrays}
                step={currentStep}
              />
            </VisualizerPanel>
          )}

          {/* Tree */}
          {hasTree && (
            <VisualizerPanel title="Tree">
              <TreeVisualizer root={frame!.tree!} />
            </VisualizerPanel>
          )}

          {/* Graph */}
          {hasGraph && (
            <VisualizerPanel title="Graph">
              <GraphVisualizer graph={frame!.graph!} />
            </VisualizerPanel>
          )}

          {/* Empty data panel */}
          {!hasArrays && !hasTree && !hasGraph && (
            <div className="flex items-center justify-center flex-1 text-xs text-white/20">
              No data structures in this frame
            </div>
          )}
        </div>

        {/* ── Right sidebar ── */}
        {layout !== 'focused-viz' && (showCallStack || showVariables) && (
          <div className="flex flex-col w-64 flex-shrink-0 border-l border-white/5 bg-[#111113]/40 overflow-y-auto divide-y divide-white/5">
            {/* Call Stack */}
            {showCallStack && frame && (
              <div className="flex-1 overflow-y-auto">
                <CallStackVisualizer callStack={frame.callStack} />
              </div>
            )}

            {/* Variables */}
            {showVariables && frame && (
              <div className="flex-1 overflow-y-auto">
                <VariableInspector
                  variables={frame.variables}
                  prevVariables={prevFrame?.variables}
                />
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── Timeline ─────────────────────────────────────────────────────── */}
      <div className="flex-shrink-0 border-t border-white/5">
        <TraceTimeline
          frames={traceSession.frames}
          currentStep={currentStep}
          onSeek={goToStep}
        />
      </div>

      {/* ── Playback Controls ─────────────────────────────────────────────── */}
      <div className="flex-shrink-0">
        <PlaybackControls
          currentStep={currentStep}
          totalSteps={totalSteps}
          isPlaying={isPlaying}
          playbackSpeed={playbackSpeed}
          onPlay={play}
          onPause={pause}
          onReset={goToStart}
          onStepForward={stepForward}
          onStepBackward={stepBackward}
          onSpeedChange={setPlaybackSpeed}
          onSeek={goToStep}
        />
      </div>
    </div>
  )
}

// ─── Sub-components ──────────────────────────────────────────────────────────

const VisualizerPanel: React.FC<{
  title: string
  children: React.ReactNode
}> = ({ title, children }) => (
  <div className="flex flex-col rounded-lg border border-white/5 bg-[#111113]/50 overflow-hidden">
    <div className="px-3 py-1.5 border-b border-white/5">
      <span className="text-[11px] font-semibold text-white/30 uppercase tracking-widest">
        {title}
      </span>
    </div>
    <div className="p-2">{children}</div>
  </div>
)

interface LayoutToggleButtonProps {
  active: boolean
  onClick: () => void
  title: string
  children: React.ReactNode
}

const LayoutToggleButton: React.FC<LayoutToggleButtonProps> = ({
  active,
  onClick,
  title,
  children,
}) => (
  <button
    onClick={onClick}
    title={title}
    className={[
      'text-[10px] font-mono px-2 py-1 rounded transition-colors',
      'focus:outline-none focus-visible:ring-1 focus-visible:ring-[#7C3AED]/50',
      active
        ? 'bg-[#7C3AED]/20 text-[#C4B5FD] border border-[#7C3AED]/30'
        : 'text-white/30 hover:text-white/60 hover:bg-white/5 border border-transparent',
    ].join(' ')}
  >
    {children}
  </button>
)
