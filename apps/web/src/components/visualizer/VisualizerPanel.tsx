import { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { api } from '../../lib/api'
import { PlaybackControls } from './PlaybackControls'
import { VariableInspector } from './VariableInspector'

// ── Types from visualization-service ─────────────────────────────────────────

interface TraceStep {
  step: number
  event: 'call' | 'line' | 'return' | 'exception'
  line: number
  function: string
  locals: Record<string, string>
  stdout: string
  returnValue?: string
  exception?: string
}

interface TraceResponse {
  language: string
  steps: TraceStep[]
  finalOutput: string
  error: string | null
  truncated: boolean
}

interface VisualizerPanelProps {
  language: string
  code: string
  onRun?: () => void
  isRunning?: boolean
}

// ── Event badge ───────────────────────────────────────────────────────────────

const EVENT_COLOR: Record<string, string> = {
  line: '#6366F1',
  call: '#10B981',
  return: '#F59E0B',
  exception: '#EF4444',
}

// ── VisualizerPanel ───────────────────────────────────────────────────────────

export default function VisualizerPanel({ language, code, onRun, isRunning }: VisualizerPanelProps) {
  const [trace, setTrace] = useState<TraceResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [step, setStep] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [speed, setSpeed] = useState<0.25 | 0.5 | 1 | 2 | 4>(1)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const supportsTrace = language === 'python' || language === 'javascript'
  const steps = trace?.steps ?? []
  const currentStep = steps[step] ?? null
  const totalSteps = steps.length

  // Auto-advance playback
  useEffect(() => {
    if (!isPlaying) {
      if (intervalRef.current) clearInterval(intervalRef.current)
      return
    }
    const ms = 1000 / speed
    intervalRef.current = setInterval(() => {
      setStep((s) => {
        if (s >= totalSteps - 1) {
          setIsPlaying(false)
          return s
        }
        return s + 1
      })
    }, ms)
    return () => { if (intervalRef.current) clearInterval(intervalRef.current) }
  }, [isPlaying, speed, totalSteps])

  const runTrace = async () => {
    if (!code.trim()) return
    setLoading(true)
    setError(null)
    setTrace(null)
    setStep(0)
    setIsPlaying(false)
    try {
      const res = await api.post<TraceResponse>('/visualize/trace', {
        language,
        code,
        input: '',
      })
      setTrace(res.data)
      if (res.data.error) {
        setError(res.data.error)
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Trace request failed')
    } finally {
      setLoading(false)
    }
  }

  // ── Empty state ─────────────────────────────────────────────────────────────

  if (!supportsTrace) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-center p-6">
        <span className="text-4xl">🔍</span>
        <p className="text-sm font-semibold text-[#F8F8F2]">Step-by-step Trace</p>
        <p className="text-xs text-[#475569]">
          Execution tracing is available for Python and JavaScript. Switch the editor language to visualize.
        </p>
      </div>
    )
  }

  if (!trace && !loading) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4 text-center p-6">
        <div className="text-5xl">🎬</div>
        <div>
          <p className="text-sm font-semibold text-[#F8F8F2] mb-1">Algorithm Visualizer</p>
          <p className="text-xs text-[#475569] max-w-[180px]">
            Run your code to see a step-by-step execution trace with variable inspection
          </p>
        </div>
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={runTrace}
          className="px-3 py-1.5 rounded-lg text-xs font-medium bg-[#22D3EE]/10 text-[#22D3EE] border border-[#22D3EE]/30 hover:bg-[#22D3EE]/20 transition-all"
        >
          Visualize
        </motion.button>
      </div>
    )
  }

  // ── Loading ─────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3">
        <svg className="w-7 h-7 animate-spin text-[#6366F1]" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
        <p className="text-xs text-[#94A3B8]">Tracing execution…</p>
      </div>
    )
  }

  // ── Error ───────────────────────────────────────────────────────────────────

  if (error && (!trace || steps.length === 0)) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-center p-4">
        <span className="text-3xl">⚠️</span>
        <p className="text-xs font-semibold text-[#F8F8F2]">Trace Error</p>
        <p className="text-xs text-[#94A3B8] font-mono leading-relaxed">{error}</p>
        <button onClick={runTrace} className="text-xs text-[#6366F1] hover:underline mt-1">
          Try again
        </button>
      </div>
    )
  }

  // ── Trace view ──────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-[#18181C] shrink-0">
        <div className="flex items-center gap-2">
          <span
            className="px-1.5 py-0.5 rounded text-[10px] font-bold uppercase"
            style={{
              background: `${EVENT_COLOR[currentStep?.event ?? 'line']}20`,
              color: EVENT_COLOR[currentStep?.event ?? 'line'],
            }}
          >
            {currentStep?.event ?? '—'}
          </span>
          <span className="text-xs text-[#94A3B8] font-mono">
            line {currentStep?.line ?? '—'} · {currentStep?.function ?? '—'}
          </span>
        </div>
        <div className="flex items-center gap-2">
          {trace?.truncated && (
            <span className="text-[10px] text-[#F59E0B] bg-[#F59E0B]/10 px-1.5 py-0.5 rounded">
              truncated
            </span>
          )}
          <button
            onClick={runTrace}
            className="text-[10px] text-[#475569] hover:text-[#94A3B8] transition-colors"
          >
            re-run
          </button>
        </div>
      </div>

      {/* Variables */}
      <div className="flex-1 overflow-y-auto p-3">
        {currentStep?.locals && Object.keys(currentStep.locals).length > 0 ? (
          <div className="space-y-1">
            <p className="text-[10px] uppercase tracking-wider text-[#475569] mb-2">Variables</p>
            {Object.entries(currentStep.locals).map(([k, v]) => (
              <div key={k} className="flex items-baseline justify-between gap-2 px-2 py-1.5 rounded-lg bg-[#18181C]">
                <span className="text-xs text-[#94A3B8] font-mono">{k}</span>
                <span className="text-xs text-[#A78BFA] font-mono truncate max-w-[120px]">{v}</span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-[#475569] text-center mt-4">No local variables at this step</p>
        )}

        {/* Return value */}
        {currentStep?.returnValue && (
          <div className="mt-3 px-2 py-1.5 rounded-lg bg-[#10B981]/10 border border-[#10B981]/20">
            <span className="text-[10px] text-[#10B981] uppercase tracking-wider">return </span>
            <span className="text-xs text-[#A7F3D0] font-mono">{currentStep.returnValue}</span>
          </div>
        )}

        {/* Exception */}
        {currentStep?.exception && (
          <div className="mt-3 px-2 py-1.5 rounded-lg bg-[#EF4444]/10 border border-[#EF4444]/20">
            <p className="text-[10px] text-[#EF4444] uppercase tracking-wider mb-0.5">Exception</p>
            <p className="text-xs text-[#FCA5A5] font-mono">{currentStep.exception}</p>
          </div>
        )}

        {/* Stdout */}
        {currentStep?.stdout && (
          <div className="mt-3">
            <p className="text-[10px] uppercase tracking-wider text-[#475569] mb-1">Output so far</p>
            <pre className="text-xs text-[#94A3B8] font-mono bg-[#18181C] rounded-lg p-2 whitespace-pre-wrap leading-relaxed">
              {currentStep.stdout}
            </pre>
          </div>
        )}
      </div>

      {/* Playback controls */}
      <div className="shrink-0">
        <PlaybackControls
          currentStep={step}
          totalSteps={totalSteps}
          isPlaying={isPlaying}
          playbackSpeed={speed}
          onPlay={() => setIsPlaying(true)}
          onPause={() => setIsPlaying(false)}
          onReset={() => { setStep(0); setIsPlaying(false) }}
          onStepForward={() => setStep((s) => Math.min(s + 1, totalSteps - 1))}
          onStepBackward={() => setStep((s) => Math.max(s - 1, 0))}
          onSpeedChange={setSpeed}
          onSeek={setStep}
        />
      </div>
    </div>
  )
}
