import React, { useEffect, useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

export type TestCaseVerdict =
  | 'PASSED'
  | 'FAILED'
  | 'PENDING'
  | 'RUNNING'

export interface TestResult {
  id: number
  label: string
  input: string
  expectedOutput: string
  actualOutput: string
  verdict: TestCaseVerdict
  executionTimeMs?: number
  memoryKb?: number
}

export type SubmissionVerdict =
  | 'PENDING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'

export interface Submission {
  id: string
  verdict: SubmissionVerdict
  language: string
  runtimeMs?: number
  memoryKb?: number
  runtimePercentile?: number
  memoryPercentile?: number
  errorMessage?: string
  errorLine?: number
  failedTestCase?: number
  totalTestCases?: number
  passedTestCases?: number
  judgedAt?: string
}

interface SubmissionPanelProps {
  submission: Submission | null
  testResults: TestResult[]
  isVisible: boolean
  onClose: () => void
}

type PanelTab = 'test-cases' | 'output' | 'performance'

// ── Helpers ───────────────────────────────────────────────────────────────────

const formatMemory = (kb: number): string => {
  if (kb >= 1024) return `${(kb / 1024).toFixed(1)} MB`
  return `${kb} KB`
}

// ── Mini confetti burst (CSS-only, no canvas dep) ─────────────────────────────

const ConfettiBurst: React.FC = () => {
  const particles = Array.from({ length: 24 }, (_, i) => i)
  const colors = ['#6366F1', '#22D3EE', '#10B981', '#F59E0B', '#FF79C6', '#BD93F9', '#F1FA8C']

  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden">
      {particles.map((i) => {
        const angle = (i / 24) * 360
        const distance = 80 + Math.random() * 80
        const color = colors[i % colors.length]
        return (
          <motion.div
            key={i}
            className="absolute w-2 h-2 rounded-sm"
            style={{
              backgroundColor: color,
              top: '50%',
              left: '50%',
              originX: '50%',
              originY: '50%',
            }}
            initial={{ x: 0, y: 0, opacity: 1, scale: 0 }}
            animate={{
              x: Math.cos((angle * Math.PI) / 180) * distance,
              y: Math.sin((angle * Math.PI) / 180) * distance,
              opacity: 0,
              scale: [0, 1.2, 0],
              rotate: angle * 2,
            }}
            transition={{ duration: 0.9, delay: i * 0.02, ease: 'easeOut' }}
          />
        )
      })}
    </div>
  )
}

// ── Test case diff view ────────────────────────────────────────────────────────

const DiffView: React.FC<{
  expected: string
  actual: string
}> = ({ expected, actual }) => {
  const expectedLines = expected.split('\n')
  const actualLines = actual.split('\n')
  const maxLen = Math.max(expectedLines.length, actualLines.length)

  return (
    <div className="grid grid-cols-2 gap-2 text-xs font-mono">
      <div>
        <div className="text-[#10B981] text-[11px] uppercase tracking-widest mb-1.5 font-sans font-semibold">
          Expected
        </div>
        <div className="bg-[#10B98108] border border-[#10B98130] rounded-lg p-3 space-y-0.5">
          {Array.from({ length: maxLen }, (_, i) => (
            <div
              key={i}
              className={`px-1 py-0.5 rounded ${
                expectedLines[i] !== actualLines[i]
                  ? 'bg-[#10B98120] text-[#10B981]'
                  : 'text-[#94A3B8]'
              }`}
            >
              {expectedLines[i] ?? ''}
            </div>
          ))}
        </div>
      </div>
      <div>
        <div className="text-[#EF4444] text-[11px] uppercase tracking-widest mb-1.5 font-sans font-semibold">
          Your Output
        </div>
        <div className="bg-[#EF444408] border border-[#EF444430] rounded-lg p-3 space-y-0.5">
          {Array.from({ length: maxLen }, (_, i) => (
            <div
              key={i}
              className={`px-1 py-0.5 rounded ${
                expectedLines[i] !== actualLines[i]
                  ? 'bg-[#EF444420] text-[#EF4444]'
                  : 'text-[#94A3B8]'
              }`}
            >
              {actualLines[i] ?? ''}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ── Performance bar ────────────────────────────────────────────────────────────

const PercentileBar: React.FC<{
  label: string
  percentile: number
  value: string
  color: string
}> = ({ label, percentile, value, color }) => (
  <div className="space-y-2">
    <div className="flex items-center justify-between text-sm">
      <span className="text-[#94A3B8]">{label}</span>
      <span className="font-medium text-[#F8F8F2]">{value}</span>
    </div>
    <div className="h-2 bg-[#18181C] rounded-full overflow-hidden">
      <motion.div
        className="h-full rounded-full"
        style={{ backgroundColor: color }}
        initial={{ width: 0 }}
        animate={{ width: `${percentile}%` }}
        transition={{ duration: 1, delay: 0.2, ease: 'easeOut' }}
      />
    </div>
    <div className="text-xs text-[#475569]">
      Faster than{' '}
      <span style={{ color }}>{percentile}%</span> of submissions
    </div>
  </div>
)

// ── Judging progress ───────────────────────────────────────────────────────────

const JudgingView: React.FC<{ submission: Submission }> = ({ submission }) => {
  const passed = submission.passedTestCases ?? 0
  const total = submission.totalTestCases ?? 5
  const progress = total > 0 ? (passed / total) * 100 : 0

  return (
    <div className="flex flex-col items-center justify-center h-full gap-6 py-12">
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ repeat: Infinity, duration: 1, ease: 'linear' }}
        className="w-12 h-12 rounded-full border-2 border-[#18181C] border-t-[#6366F1]"
      />
      <div className="text-center space-y-1">
        <div className="text-[#F8F8F2] font-semibold">Judging…</div>
        <div className="text-[#94A3B8] text-sm">
          Test case {passed} / {total}
        </div>
      </div>
      <div className="w-64 h-1.5 bg-[#18181C] rounded-full overflow-hidden">
        <motion.div
          className="h-full bg-[#6366F1] rounded-full"
          animate={{ width: `${progress}%` }}
          transition={{ duration: 0.4 }}
        />
      </div>
    </div>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────

const SubmissionPanel: React.FC<SubmissionPanelProps> = ({
  submission,
  testResults,
  isVisible,
  onClose,
}) => {
  const [activeTab, setActiveTab] = useState<PanelTab>('test-cases')
  const [selectedTest, setSelectedTest] = useState<number>(0)
  const [showConfetti, setShowConfetti] = useState(false)

  const prevVerdict = useRef<SubmissionVerdict | null>(null)

  useEffect(() => {
    if (
      submission?.verdict === 'ACCEPTED' &&
      prevVerdict.current !== 'ACCEPTED'
    ) {
      setShowConfetti(true)
      const t = setTimeout(() => setShowConfetti(false), 1200)
      return () => clearTimeout(t)
    }
    prevVerdict.current = submission?.verdict ?? null
    return undefined
  }, [submission?.verdict])

  const failedTest = testResults.find((t) => t.verdict === 'FAILED')
  const selectedTestResult = testResults[selectedTest]

  const TABS: { id: PanelTab; label: string }[] = [
    { id: 'test-cases', label: 'Test Cases' },
    { id: 'output', label: 'Output' },
    { id: 'performance', label: 'Performance' },
  ]

  return (
    <AnimatePresence>
      {isVisible && (
        <motion.div
          initial={{ height: 0, opacity: 0 }}
          animate={{ height: 280, opacity: 1 }}
          exit={{ height: 0, opacity: 0 }}
          transition={{ duration: 0.25, ease: 'easeInOut' }}
          className="relative overflow-hidden bg-[#111113] border-t border-[#18181C] flex flex-col shrink-0"
        >
          {showConfetti && <ConfettiBurst />}

          {/* Header */}
          <div className="flex items-center justify-between px-4 py-2 border-b border-[#18181C] shrink-0">
            {/* Tabs */}
            <div className="flex items-center gap-1">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={`px-3 py-1.5 rounded text-xs font-medium transition-colors ${
                    activeTab === tab.id
                      ? 'bg-[#6366F1]/15 text-[#6366F1]'
                      : 'text-[#475569] hover:text-[#94A3B8]'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Verdict pill + close */}
            <div className="flex items-center gap-3">
              {submission && submission.verdict !== 'PENDING' && submission.verdict !== 'RUNNING' && (
                <VerdictPill verdict={submission.verdict} />
              )}
              <button
                onClick={onClose}
                className="p-1 rounded text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C] transition-colors"
              >
                <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                  <path
                    d="M1 1l12 12M13 1L1 13"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                  />
                </svg>
              </button>
            </div>
          </div>

          {/* Body */}
          <div className="flex-1 overflow-auto p-4">
            {/* Judging state */}
            {submission?.verdict === 'RUNNING' && (
              <JudgingView submission={submission} />
            )}

            {/* Compilation error */}
            {submission?.verdict === 'COMPILATION_ERROR' && (
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-[#EF4444]">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5" />
                    <path d="M8 5v4M8 11v.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                  </svg>
                  <span className="font-semibold text-sm">Compilation Error</span>
                </div>
                <pre className="bg-[#EF444408] border border-[#EF444430] rounded-lg p-4 text-xs text-[#EF4444] font-mono overflow-auto whitespace-pre-wrap">
                  {submission.errorMessage ?? 'Unknown compilation error.'}
                </pre>
              </div>
            )}

            {/* Test Cases tab */}
            {activeTab === 'test-cases' &&
              submission?.verdict !== 'RUNNING' &&
              submission?.verdict !== 'COMPILATION_ERROR' && (
                <div className="space-y-3">
                  {/* Test case selector */}
                  <div className="flex items-center gap-2 flex-wrap">
                    {testResults.map((t, i) => (
                      <button
                        key={t.id}
                        onClick={() => setSelectedTest(i)}
                        className={`flex items-center gap-1.5 px-2.5 py-1 rounded text-xs font-medium transition-colors ${
                          selectedTest === i
                            ? 'bg-[#18181C] text-[#F8F8F2] border border-[#475569]/50'
                            : 'text-[#475569] hover:text-[#94A3B8]'
                        }`}
                      >
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            t.verdict === 'PASSED'
                              ? 'bg-[#10B981]'
                              : t.verdict === 'FAILED'
                              ? 'bg-[#EF4444]'
                              : t.verdict === 'RUNNING'
                              ? 'bg-[#6366F1] animate-pulse'
                              : 'bg-[#475569]'
                          }`}
                        />
                        {t.label}
                      </button>
                    ))}
                  </div>

                  {/* Selected test detail */}
                  {selectedTestResult && (
                    <div className="space-y-3">
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <div className="text-[#475569] text-[11px] uppercase tracking-widest mb-1 font-semibold">
                            Input
                          </div>
                          <pre className="bg-[#0A0A0B] rounded-lg p-2.5 text-xs text-[#94A3B8] font-mono overflow-auto max-h-24">
                            {selectedTestResult.input}
                          </pre>
                        </div>
                        {selectedTestResult.executionTimeMs !== undefined && (
                          <div className="text-xs text-[#475569] space-y-1">
                            <div>
                              Time:{' '}
                              <span className="text-[#94A3B8]">
                                {selectedTestResult.executionTimeMs} ms
                              </span>
                            </div>
                            {selectedTestResult.memoryKb !== undefined && (
                              <div>
                                Memory:{' '}
                                <span className="text-[#94A3B8]">
                                  {formatMemory(selectedTestResult.memoryKb)}
                                </span>
                              </div>
                            )}
                          </div>
                        )}
                      </div>

                      {selectedTestResult.verdict === 'FAILED' && (
                        <DiffView
                          expected={selectedTestResult.expectedOutput}
                          actual={selectedTestResult.actualOutput}
                        />
                      )}
                    </div>
                  )}
                </div>
              )}

            {/* Output tab */}
            {activeTab === 'output' &&
              submission?.verdict !== 'RUNNING' &&
              submission?.verdict !== 'COMPILATION_ERROR' && (
                <div className="space-y-2">
                  {failedTest ? (
                    <DiffView
                      expected={failedTest.expectedOutput}
                      actual={failedTest.actualOutput}
                    />
                  ) : (
                    <div className="text-center py-8 text-[#475569] text-sm">
                      {submission?.verdict === 'ACCEPTED'
                        ? 'All test cases passed ✓'
                        : 'No output to display.'}
                    </div>
                  )}
                </div>
              )}

            {/* Performance tab */}
            {activeTab === 'performance' &&
              submission?.verdict === 'ACCEPTED' &&
              submission.runtimeMs !== undefined && (
                <div className="grid grid-cols-2 gap-6">
                  <PercentileBar
                    label="Runtime"
                    percentile={submission.runtimePercentile ?? 50}
                    value={`${submission.runtimeMs} ms`}
                    color="#10B981"
                  />
                  {submission.memoryKb !== undefined && (
                    <PercentileBar
                      label="Memory"
                      percentile={submission.memoryPercentile ?? 50}
                      value={formatMemory(submission.memoryKb)}
                      color="#22D3EE"
                    />
                  )}
                </div>
              )}

            {activeTab === 'performance' && submission?.verdict !== 'ACCEPTED' && (
              <div className="text-center py-8 text-[#475569] text-sm">
                Performance data available after acceptance.
              </div>
            )}
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

// ── Verdict pill ───────────────────────────────────────────────────────────────

const VERDICT_PILL_STYLES: Record<
  Exclude<SubmissionVerdict, 'PENDING' | 'RUNNING'>,
  { label: string; style: string }
> = {
  ACCEPTED: {
    label: '✓ Accepted',
    style: 'text-[#10B981] bg-[#10B98115] border border-[#10B98130]',
  },
  WRONG_ANSWER: {
    label: '✗ Wrong Answer',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
  TIME_LIMIT_EXCEEDED: {
    label: '⏱ Time Limit',
    style: 'text-[#F59E0B] bg-[#F59E0B15] border border-[#F59E0B30]',
  },
  MEMORY_LIMIT_EXCEEDED: {
    label: '💾 Memory Limit',
    style: 'text-[#F59E0B] bg-[#F59E0B15] border border-[#F59E0B30]',
  },
  RUNTIME_ERROR: {
    label: '⚠ Runtime Error',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
  COMPILATION_ERROR: {
    label: '⚠ Compile Error',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
}

const VerdictPill: React.FC<{
  verdict: Exclude<SubmissionVerdict, 'PENDING' | 'RUNNING'>
}> = ({ verdict }) => {
  const config = VERDICT_PILL_STYLES[verdict]
  return (
    <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${config.style}`}>
      {config.label}
    </span>
  )
}

export default SubmissionPanel
