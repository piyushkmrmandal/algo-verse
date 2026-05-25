import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'

import CodeEditor from '../components/editor/CodeEditor'
import EditorToolbar from '../components/editor/EditorToolbar'
import SubmissionPanel, {
  type Submission,
  type TestResult,
} from '../components/editor/SubmissionPanel'
import ProblemStatement, {
  type ProblemDetail,
} from '../components/problem/ProblemStatement'
import AiTutor from '../components/ai/AiTutor'
import VisualizerPanel from '../components/visualizer/VisualizerPanel'
import { useEditorStore, type SupportedLanguage } from '../stores/editor-store'
import AchievementToast, {
  type Badge,
} from '../components/gamification/AchievementToast'

// ── Types ─────────────────────────────────────────────────────────────────────

type LeftTab = 'problem' | 'editorial' | 'solutions' | 'discussion'
type RightPanel = 'visualizer' | 'ai-tutor'

interface SubmissionResponse {
  id: string
  status: string
}

interface WsMessage {
  type: 'status' | 'progress' | 'result' | 'error'
  verdict?: Submission['verdict']
  passedTestCases?: number
  totalTestCases?: number
  runtimeMs?: number
  memoryKb?: number
  runtimePercentile?: number
  memoryPercentile?: number
  errorMessage?: string
  testResults?: TestResult[]
  badge?: Badge
  xpGain?: number
}

// ── API helpers ───────────────────────────────────────────────────────────────

const fetchProblem = async (slug: string): Promise<ProblemDetail> => {
  const res = await fetch(`/api/problems/${slug}`)
  if (res.status === 404) throw new Error('NOT_FOUND')
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<ProblemDetail>
}

const submitSolution = async (payload: {
  problemSlug: string
  language: string
  code: string
  type: 'RUN' | 'SUBMIT'
}): Promise<SubmissionResponse> => {
  const res = await fetch('/api/submissions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json() as Promise<SubmissionResponse>
}

// ── Loading skeleton ──────────────────────────────────────────────────────────

const Skeleton: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-[#18181C] rounded animate-pulse ${className}`} />
)

const ProblemSkeleton: React.FC = () => (
  <div className="p-5 space-y-4">
    <Skeleton className="h-7 w-2/3" />
    <div className="flex gap-2">
      <Skeleton className="h-5 w-16 rounded-full" />
      <Skeleton className="h-5 w-24" />
    </div>
    <Skeleton className="h-4 w-full" />
    <Skeleton className="h-4 w-5/6" />
    <Skeleton className="h-4 w-4/5" />
    <Skeleton className="h-32 w-full rounded-xl" />
    <Skeleton className="h-4 w-full" />
    <Skeleton className="h-4 w-3/4" />
  </div>
)

// ── Drag handle ───────────────────────────────────────────────────────────────

interface DragHandleProps {
  onDrag: (dx: number) => void
}

const DragHandle: React.FC<DragHandleProps> = ({ onDrag }) => {
  const dragging = useRef(false)
  const lastX = useRef(0)

  const handleMouseDown = (e: React.MouseEvent) => {
    dragging.current = true
    lastX.current = e.clientX
    e.preventDefault()
  }

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!dragging.current) return
      const dx = e.clientX - lastX.current
      lastX.current = e.clientX
      onDrag(dx)
    }
    const onUp = () => { dragging.current = false }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    return () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
  }, [onDrag])

  return (
    <div
      onMouseDown={handleMouseDown}
      className="w-1.5 cursor-col-resize shrink-0 bg-[#18181C] hover:bg-[#6366F1]/40 transition-colors group relative flex items-center justify-center"
      title="Drag to resize"
    >
      <div className="w-0.5 h-8 bg-[#475569]/40 group-hover:bg-[#6366F1]/60 rounded-full transition-colors" />
    </div>
  )
}

// ── Keyboard shortcut modal ────────────────────────────────────────────────────

const ShortcutModal: React.FC<{ onClose: () => void }> = ({ onClose }) => {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [onClose])

  const shortcuts = [
    { keys: ['Ctrl', 'Enter'], action: 'Submit solution' },
    { keys: ['Ctrl', 'Shift', 'F'], action: 'Format code' },
    { keys: ['?'], action: 'Show keyboard shortcuts' },
    { keys: ['Esc'], action: 'Close this modal' },
  ]

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.92, y: 20 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 20 }}
        transition={{ type: 'spring', damping: 25, stiffness: 350 }}
        onClick={(e) => e.stopPropagation()}
        className="bg-[#111113] border border-[#18181C] rounded-2xl p-6 w-96 shadow-2xl shadow-black/60"
      >
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-base font-bold text-[#F8F8F2]">Keyboard Shortcuts</h2>
          <button onClick={onClose} className="text-[#475569] hover:text-[#94A3B8] transition-colors">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M2 2l12 12M14 2L2 14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </button>
        </div>
        <div className="space-y-2.5">
          {shortcuts.map(({ keys, action }) => (
            <div key={action} className="flex items-center justify-between">
              <span className="text-sm text-[#94A3B8]">{action}</span>
              <div className="flex items-center gap-1">
                {keys.map((k, i) => (
                  <React.Fragment key={k}>
                    {i > 0 && <span className="text-[#475569] text-xs">+</span>}
                    <kbd className="px-2 py-0.5 rounded bg-[#18181C] border border-[#475569]/30 text-xs font-mono text-[#F8F8F2]">
                      {k}
                    </kbd>
                  </React.Fragment>
                ))}
              </div>
            </div>
          ))}
        </div>
      </motion.div>
    </motion.div>
  )
}

// ── 404 State ─────────────────────────────────────────────────────────────────

const NotFound: React.FC = () => (
  <div className="flex flex-col items-center justify-center h-full text-center gap-6">
    <div className="text-7xl">🔍</div>
    <div>
      <h1 className="text-2xl font-bold text-[#F8F8F2] mb-2">Problem Not Found</h1>
      <p className="text-[#94A3B8]">
        This problem doesn&apos;t exist or has been removed.
      </p>
    </div>
    <a
      href="/problems"
      className="px-4 py-2 rounded-lg bg-[#6366F1] text-white text-sm font-medium hover:bg-[#5254CC] transition-colors"
    >
      Browse Problems
    </a>
  </div>
)

// ── Component ─────────────────────────────────────────────────────────────────

const MIN_PANEL_PX = 240

const ProblemPage: React.FC = () => {
  const { slug } = useParams<{ slug: string }>()
  const queryClient = useQueryClient()
  const containerRef = useRef<HTMLDivElement>(null)

  // Panel widths (percentages of container)
  const [leftPct, setLeftPct] = useState(35)
  const [centerPct, setCenterPct] = useState(40)

  // UI state
  const [leftTab, setLeftTab] = useState<LeftTab>('problem')
  const [rightPanel, setRightPanel] = useState<RightPanel>('visualizer')
  const [showAiTutor, setShowAiTutor] = useState(false)
  const [showSubmissionPanel, setShowSubmissionPanel] = useState(false)
  const [showShortcuts, setShowShortcuts] = useState(false)

  // Submission state
  const [submission, setSubmission] = useState<Submission | null>(null)
  const [testResults, setTestResults] = useState<TestResult[]>([])

  // Gamification
  const [pendingBadge, setPendingBadge] = useState<Badge | null>(null)
  const [pendingXp, setPendingXp] = useState(0)

  // Current code ref (for AiTutor context)
  const [currentCode, setCurrentCode] = useState('')

  const wsRef = useRef<WebSocket | null>(null)

  const {
    currentLanguage,
    isSubmitting,
    isRunning,
    submissionStatus,
    setLanguage,
    setSubmitting,
    setRunning,
    setSubmissionStatus,
    setLastSubmissionId,
  } = useEditorStore()

  // ── Data fetching ────────────────────────────────────────────────────────────

  const {
    data: problem,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['problem', slug],
    queryFn: () => fetchProblem(slug!),
    enabled: !!slug,
    retry: (_, err) => (err as Error).message !== 'NOT_FOUND',
    staleTime: 5 * 60_000,
  })

  // ── SEO ──────────────────────────────────────────────────────────────────────

  useEffect(() => {
    if (problem) {
      document.title = `${problem.title} — AlgoVerse`
    } else if (!isLoading) {
      document.title = 'AlgoVerse'
    }
    return () => { document.title = 'AlgoVerse' }
  }, [problem, isLoading])

  // ── Keyboard shortcuts ───────────────────────────────────────────────────────

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (
        e.key === '?' &&
        !(e.target instanceof HTMLInputElement) &&
        !(e.target instanceof HTMLTextAreaElement)
      ) {
        setShowShortcuts((s) => !s)
      }
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [])

  // ── WebSocket for submission updates ─────────────────────────────────────────

  const connectSubmissionWs = useCallback(
    (submissionId: string) => {
      wsRef.current?.close()

      const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
      const ws = new WebSocket(
        `${protocol}://${window.location.host}/ws/submissions/${submissionId}`,
      )
      wsRef.current = ws

      ws.onmessage = (event: MessageEvent<string>) => {
        try {
          const msg = JSON.parse(event.data) as WsMessage

          if (msg.type === 'progress') {
            setSubmission((prev) => ({
              ...(prev ?? { id: submissionId, verdict: 'RUNNING', language: currentLanguage }),
              passedTestCases: msg.passedTestCases,
              totalTestCases: msg.totalTestCases,
            }))
          }

          if (msg.type === 'result' && msg.verdict) {
            const finalSubmission: Submission = {
              id: submissionId,
              verdict: msg.verdict,
              language: currentLanguage,
              runtimeMs: msg.runtimeMs,
              memoryKb: msg.memoryKb,
              runtimePercentile: msg.runtimePercentile,
              memoryPercentile: msg.memoryPercentile,
              errorMessage: msg.errorMessage,
              passedTestCases: msg.passedTestCases,
              totalTestCases: msg.totalTestCases,
            }

            setSubmission(finalSubmission)
            setTestResults(msg.testResults ?? [])
            setSubmissionStatus(msg.verdict)
            setSubmitting(false)
            setRunning(false)

            if (msg.verdict === 'ACCEPTED') {
              if (msg.badge) {
                setPendingBadge(msg.badge)
                setPendingXp(msg.xpGain ?? 0)
              }
              // Invalidate gamification data
              queryClient.invalidateQueries({ queryKey: ['user-stats'] })
            }

            ws.close()
          }

          if (msg.type === 'error') {
            setSubmitting(false)
            setRunning(false)
            setSubmissionStatus('RUNTIME_ERROR')
            ws.close()
          }
        } catch {
          // Malformed WS message
        }
      }

      ws.onerror = () => {
        setSubmitting(false)
        setRunning(false)
        ws.close()
      }
    },
    [currentLanguage, queryClient, setSubmissionStatus, setSubmitting, setRunning],
  )

  useEffect(() => () => { wsRef.current?.close() }, [])

  // ── Submit / Run mutations ────────────────────────────────────────────────────

  const submitMutation = useMutation({
    mutationFn: submitSolution,
    onSuccess: (data) => {
      setLastSubmissionId(data.id)
      setSubmission({
        id: data.id,
        verdict: 'RUNNING',
        language: currentLanguage,
        totalTestCases: 5,
        passedTestCases: 0,
      })
      setShowSubmissionPanel(true)
      connectSubmissionWs(data.id)
    },
    onError: () => {
      setSubmitting(false)
      setRunning(false)
      setSubmissionStatus('RUNTIME_ERROR')
    },
  })

  const handleSubmit = useCallback(
    (code?: string) => {
      if (!problem || isSubmitting) return
      const resolvedCode =
        code ?? useEditorStore.getState().getCode(problem.slug, currentLanguage) ?? ''
      setSubmitting(true)
      setSubmissionStatus('SUBMITTING')
      setTestResults([])
      submitMutation.mutate({
        problemSlug: problem.slug,
        language: currentLanguage,
        code: resolvedCode,
        type: 'SUBMIT',
      })
    },
    [problem, isSubmitting, currentLanguage, setSubmitting, setSubmissionStatus, submitMutation],
  )

  const handleRun = useCallback(() => {
    if (!problem || isRunning) return
    const code =
      useEditorStore.getState().getCode(problem.slug, currentLanguage) ?? ''
    setRunning(true)
    setSubmissionStatus('RUNNING')
    setShowSubmissionPanel(true)
    setTestResults([])
    submitMutation.mutate({
      problemSlug: problem.slug,
      language: currentLanguage,
      code,
      type: 'RUN',
    })
  }, [problem, isRunning, currentLanguage, setRunning, setSubmissionStatus, submitMutation])

  const handleReset = useCallback(() => {
    if (!problem) return
    useEditorStore.getState().resetCode(problem.slug, currentLanguage)
  }, [problem, currentLanguage])

  // ── Panel resize ──────────────────────────────────────────────────────────────

  const handleLeftDrag = useCallback(
    (dx: number) => {
      const container = containerRef.current
      if (!container) return
      const total = container.clientWidth
      const deltaPercent = (dx / total) * 100
      setLeftPct((prev) => {
        const newLeft = Math.max(
          (MIN_PANEL_PX / total) * 100,
          Math.min(55, prev + deltaPercent),
        )
        return newLeft
      })
    },
    [],
  )

  const handleRightDrag = useCallback(
    (dx: number) => {
      const container = containerRef.current
      if (!container) return
      const total = container.clientWidth
      const deltaPercent = (dx / total) * 100
      setCenterPct((prev) => {
        const newCenter = Math.max(
          (MIN_PANEL_PX / total) * 100,
          Math.min(70, prev + deltaPercent),
        )
        return newCenter
      })
    },
    [],
  )

  const rightPct = Math.max(5, 100 - leftPct - centerPct)

  // ── Left tabs ─────────────────────────────────────────────────────────────────

  const LEFT_TABS: { id: LeftTab; label: string }[] = [
    { id: 'problem', label: 'Problem' },
    { id: 'editorial', label: 'Editorial' },
    { id: 'solutions', label: 'Solutions' },
    { id: 'discussion', label: 'Discussion' },
  ]

  // ── Render ────────────────────────────────────────────────────────────────────

  const is404 = (error as Error | null)?.message === 'NOT_FOUND'

  return (
    <div className="flex flex-col h-screen bg-[#0A0A0B] overflow-hidden">
      {/* Global nav bar */}
      <header className="flex items-center justify-between px-4 h-11 bg-[#111113] border-b border-[#18181C] shrink-0 z-20">
        <div className="flex items-center gap-3">
          <a href="/" className="text-sm font-bold text-[#6366F1]">
            AlgoVerse
          </a>
          <span className="text-[#18181C]">/</span>
          <a href="/problems" className="text-xs text-[#475569] hover:text-[#94A3B8] transition-colors">
            Problems
          </a>
          {problem && (
            <>
              <span className="text-[#18181C]">/</span>
              <span className="text-xs text-[#94A3B8] truncate max-w-48">
                {problem.title}
              </span>
            </>
          )}
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowShortcuts(true)}
            title="Keyboard shortcuts (?)"
            className="p-1.5 rounded text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C] transition-colors text-xs"
          >
            ?
          </button>
          <button
            onClick={() => setShowAiTutor((t) => !t)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
              showAiTutor
                ? 'bg-[#6366F1]/20 text-[#6366F1] border border-[#6366F1]/30'
                : 'text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C]'
            }`}
          >
            <span>🤖</span>
            <span>AI Tutor</span>
          </button>
        </div>
      </header>

      {/* 3-panel layout */}
      <div ref={containerRef} className="flex flex-1 overflow-hidden">
        {/* Left panel */}
        <div
          className="flex flex-col bg-[#111113] border-r border-[#18181C] overflow-hidden shrink-0"
          style={{ width: `${leftPct}%` }}
        >
          {/* Tab bar */}
          <div className="flex border-b border-[#18181C] bg-[#0A0A0B] shrink-0 px-1">
            {LEFT_TABS.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setLeftTab(tab.id)}
                className={`px-3 py-2.5 text-xs font-medium transition-colors border-b-2 -mb-px ${
                  leftTab === tab.id
                    ? 'text-[#6366F1] border-[#6366F1]'
                    : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-hidden">
            {isLoading && <ProblemSkeleton />}

            {is404 && <NotFound />}

            {!isLoading && !is404 && problem && leftTab === 'problem' && (
              <ProblemStatement problem={problem} isPro={false} />
            )}

            {!isLoading && !is404 && leftTab === 'editorial' && (
              <div className="flex items-center justify-center h-full text-[#475569] text-sm">
                Editorial coming soon…
              </div>
            )}

            {!isLoading && !is404 && leftTab === 'solutions' && (
              <div className="flex items-center justify-center h-full text-[#475569] text-sm">
                Community solutions coming soon…
              </div>
            )}

            {!isLoading && !is404 && leftTab === 'discussion' && (
              <div className="flex items-center justify-center h-full text-[#475569] text-sm">
                Discussion coming soon…
              </div>
            )}
          </div>
        </div>

        {/* Left drag handle */}
        <DragHandle onDrag={handleLeftDrag} />

        {/* Center panel: Editor */}
        <div
          className="flex flex-col bg-[#0A0A0B] overflow-hidden"
          style={{ width: `${centerPct}%` }}
        >
          <EditorToolbar
            language={currentLanguage}
            onLanguageChange={(lang) => setLanguage(lang as SupportedLanguage)}
            onSubmit={handleSubmit}
            onRun={handleRun}
            onReset={handleReset}
            isSubmitting={isSubmitting}
            isRunning={isRunning}
            submissionStatus={submissionStatus}
            problemTitle={problem?.title}
            difficulty={problem?.difficulty}
          />

          <div className="flex-1 overflow-hidden">
            <CodeEditor
              problemSlug={slug ?? ''}
              language={currentLanguage}
              onCodeChange={setCurrentCode}
              onSubmit={handleSubmit}
              className="h-full"
            />
          </div>

          <SubmissionPanel
            submission={submission}
            testResults={testResults}
            isVisible={showSubmissionPanel}
            onClose={() => setShowSubmissionPanel(false)}
          />
        </div>

        {/* Right drag handle */}
        <DragHandle onDrag={handleRightDrag} />

        {/* Right panel */}
        <div
          className="relative flex flex-col bg-[#111113] overflow-hidden"
          style={{ width: `${rightPct}%` }}
        >
          {/* Right panel header */}
          <div className="flex items-center border-b border-[#18181C] bg-[#0A0A0B] px-2 shrink-0">
            <button
              onClick={() => setRightPanel('visualizer')}
              className={`px-3 py-2.5 text-xs font-medium transition-colors border-b-2 -mb-px ${
                rightPanel === 'visualizer' && !showAiTutor
                  ? 'text-[#6366F1] border-[#6366F1]'
                  : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
              }`}
            >
              Visualizer
            </button>
            <button
              onClick={() => { setRightPanel('ai-tutor'); setShowAiTutor(true) }}
              className={`px-3 py-2.5 text-xs font-medium transition-colors border-b-2 -mb-px ${
                showAiTutor
                  ? 'text-[#6366F1] border-[#6366F1]'
                  : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
              }`}
            >
              AI Tutor
            </button>
          </div>

          {/* Visualizer */}
          {!showAiTutor && (
            <div className="flex-1 overflow-hidden">
              <VisualizerPanel
                language={currentLanguage}
                code={currentCode}
                onRun={handleRun}
                isRunning={isRunning}
              />
            </div>
          )}

          {/* AI Tutor */}
          {problem && (
            <AiTutor
              problemId={problem.id}
              isOpen={showAiTutor}
              onClose={() => setShowAiTutor(false)}
              currentCode={currentCode}
              language={currentLanguage}
            />
          )}
        </div>
      </div>

      {/* Keyboard shortcut modal */}
      <AnimatePresence>
        {showShortcuts && (
          <ShortcutModal onClose={() => setShowShortcuts(false)} />
        )}
      </AnimatePresence>

      {/* Achievement toast */}
      <AchievementToast
        badge={pendingBadge}
        xpGain={pendingXp}
        onDismiss={() => { setPendingBadge(null); setPendingXp(0) }}
      />
    </div>
  )
}

export default ProblemPage
