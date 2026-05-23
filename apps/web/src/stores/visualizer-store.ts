import { create } from 'zustand'
import { devtools, subscribeWithSelector } from 'zustand/middleware'
import type { TraceSession, ExecutionFrame } from '@algoverse/ast-parser'

interface VisualizerState {
  // ─── Session ────────────────────────────────────────────────────────────
  traceSession: TraceSession | null
  isLoading: boolean
  error: string | null

  // ─── Playback ────────────────────────────────────────────────────────────
  currentStep: number
  isPlaying: boolean
  playbackSpeed: 0.25 | 0.5 | 1 | 2 | 4

  // ─── UI ─────────────────────────────────────────────────────────────────
  focusedVariable: string | null
  expandedFrames: Set<string>
  layout: 'split' | 'focused-viz' | 'focused-code'

  // ─── Derived ─────────────────────────────────────────────────────────────
  currentFrame: ExecutionFrame | null
  previousFrame: ExecutionFrame | null

  // ─── Actions ─────────────────────────────────────────────────────────────
  setTraceSession: (session: TraceSession | null) => void
  setLoading: (loading: boolean) => void
  setError: (error: string | null) => void
  goToStep: (step: number) => void
  stepForward: () => void
  stepBackward: () => void
  goToStart: () => void
  goToEnd: () => void
  play: () => void
  pause: () => void
  setPlaybackSpeed: (speed: VisualizerState['playbackSpeed']) => void
  setFocusedVariable: (name: string | null) => void
  toggleFrameExpanded: (frameId: string) => void
  setLayout: (layout: VisualizerState['layout']) => void
  reset: () => void
}

/** Derive currentFrame and previousFrame from a step index and session. */
function deriveFrames(
  session: TraceSession | null,
  step: number,
): { currentFrame: ExecutionFrame | null; previousFrame: ExecutionFrame | null } {
  if (!session || session.frames.length === 0) {
    return { currentFrame: null, previousFrame: null }
  }
  const clampedStep = Math.max(0, Math.min(step, session.frames.length - 1))
  const currentFrame = session.frames[clampedStep] ?? null
  const previousFrame = clampedStep > 0 ? (session.frames[clampedStep - 1] ?? null) : null
  return { currentFrame, previousFrame }
}

const initialState = {
  traceSession: null,
  isLoading: false,
  error: null,
  currentStep: 0,
  isPlaying: false,
  playbackSpeed: 1 as const,
  focusedVariable: null,
  expandedFrames: new Set<string>(),
  layout: 'split' as const,
  currentFrame: null,
  previousFrame: null,
}

export const useVisualizerStore = create<VisualizerState>()(
  devtools(
    subscribeWithSelector((set, get) => ({
      ...initialState,

      // ─── Session Actions ────────────────────────────────────────────────

      setTraceSession(session) {
        const { currentFrame, previousFrame } = deriveFrames(session, 0)
        set(
          {
            traceSession: session,
            currentStep: 0,
            isPlaying: false,
            error: null,
            currentFrame,
            previousFrame,
          },
          false,
          'setTraceSession',
        )
      },

      setLoading(loading) {
        set({ isLoading: loading }, false, 'setLoading')
      },

      setError(error) {
        set({ error, isLoading: false }, false, 'setError')
      },

      // ─── Playback Actions ───────────────────────────────────────────────

      goToStep(step) {
        const { traceSession } = get()
        if (!traceSession) return
        const clamped = Math.max(0, Math.min(step, traceSession.frames.length - 1))
        const { currentFrame, previousFrame } = deriveFrames(traceSession, clamped)
        set({ currentStep: clamped, currentFrame, previousFrame }, false, 'goToStep')
      },

      stepForward() {
        const { traceSession, currentStep } = get()
        if (!traceSession) return
        const nextStep = Math.min(currentStep + 1, traceSession.frames.length - 1)
        if (nextStep === currentStep) {
          // Already at end — stop playback
          set({ isPlaying: false }, false, 'stepForward/atEnd')
          return
        }
        const { currentFrame, previousFrame } = deriveFrames(traceSession, nextStep)
        set(
          { currentStep: nextStep, currentFrame, previousFrame },
          false,
          'stepForward',
        )
      },

      stepBackward() {
        const { traceSession, currentStep } = get()
        if (!traceSession) return
        const prevStep = Math.max(currentStep - 1, 0)
        const { currentFrame, previousFrame } = deriveFrames(traceSession, prevStep)
        set(
          { currentStep: prevStep, currentFrame, previousFrame, isPlaying: false },
          false,
          'stepBackward',
        )
      },

      goToStart() {
        const { traceSession } = get()
        const { currentFrame, previousFrame } = deriveFrames(traceSession, 0)
        set(
          { currentStep: 0, currentFrame, previousFrame, isPlaying: false },
          false,
          'goToStart',
        )
      },

      goToEnd() {
        const { traceSession } = get()
        if (!traceSession) return
        const lastStep = Math.max(0, traceSession.frames.length - 1)
        const { currentFrame, previousFrame } = deriveFrames(traceSession, lastStep)
        set(
          { currentStep: lastStep, currentFrame, previousFrame, isPlaying: false },
          false,
          'goToEnd',
        )
      },

      play() {
        const { traceSession, currentStep } = get()
        if (!traceSession) return
        // Don't start playing if already at the end
        if (currentStep >= traceSession.frames.length - 1) {
          // Restart from beginning
          const { currentFrame, previousFrame } = deriveFrames(traceSession, 0)
          set({ currentStep: 0, currentFrame, previousFrame, isPlaying: true }, false, 'play/restart')
          return
        }
        set({ isPlaying: true }, false, 'play')
      },

      pause() {
        set({ isPlaying: false }, false, 'pause')
      },

      setPlaybackSpeed(speed) {
        set({ playbackSpeed: speed }, false, 'setPlaybackSpeed')
      },

      // ─── UI Actions ─────────────────────────────────────────────────────

      setFocusedVariable(name) {
        set({ focusedVariable: name }, false, 'setFocusedVariable')
      },

      toggleFrameExpanded(frameId) {
        set((state) => {
          const next = new Set(state.expandedFrames)
          if (next.has(frameId)) {
            next.delete(frameId)
          } else {
            next.add(frameId)
          }
          return { expandedFrames: next }
        }, false, 'toggleFrameExpanded')
      },

      setLayout(layout) {
        set({ layout }, false, 'setLayout')
      },

      reset() {
        set({ ...initialState, expandedFrames: new Set<string>() }, false, 'reset')
      },
    })),
    { name: 'VisualizerStore' },
  ),
)

// ─── Selectors ──────────────────────────────────────────────────────────────

export const selectCurrentFrame = (state: VisualizerState) => state.currentFrame
export const selectPreviousFrame = (state: VisualizerState) => state.previousFrame
export const selectIsAtStart = (state: VisualizerState) => state.currentStep === 0
export const selectIsAtEnd = (state: VisualizerState) =>
  state.traceSession
    ? state.currentStep >= state.traceSession.frames.length - 1
    : true
export const selectTotalSteps = (state: VisualizerState) =>
  state.traceSession?.totalSteps ?? 0
export const selectProgress = (state: VisualizerState) => {
  const total = state.traceSession?.frames.length ?? 1
  return total > 1 ? state.currentStep / (total - 1) : 0
}
