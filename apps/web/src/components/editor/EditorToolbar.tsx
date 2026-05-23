import React, { useRef, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import type { SupportedLanguage, SubmissionStatus } from '../../stores/editor-store'

// ── Types ─────────────────────────────────────────────────────────────────────

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

interface EditorToolbarProps {
  language: SupportedLanguage
  onLanguageChange: (lang: SupportedLanguage) => void
  onSubmit: () => void
  onRun: () => void
  onReset: () => void
  isSubmitting: boolean
  isRunning: boolean
  submissionStatus: SubmissionStatus
  problemTitle?: string
  difficulty?: Difficulty
}

// ── Language metadata ─────────────────────────────────────────────────────────

interface LangMeta {
  label: string
  icon: string
}

const LANG_META: Record<SupportedLanguage, LangMeta> = {
  python: { label: 'Python', icon: '🐍' },
  java: { label: 'Java', icon: '☕' },
  cpp: { label: 'C++', icon: '⚙️' },
  javascript: { label: 'JavaScript', icon: '⚡' },
  go: { label: 'Go', icon: '🐹' },
  rust: { label: 'Rust', icon: '🦀' },
}

const ALL_LANGUAGES: SupportedLanguage[] = [
  'python',
  'java',
  'cpp',
  'javascript',
  'go',
  'rust',
]

// ── Difficulty badge ───────────────────────────────────────────────────────────

const DIFFICULTY_STYLES: Record<Difficulty, string> = {
  EASY: 'text-[#10B981] bg-[#10B98115] border border-[#10B98130]',
  MEDIUM: 'text-[#F59E0B] bg-[#F59E0B15] border border-[#F59E0B30]',
  HARD: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
}

// ── Submission status chip ─────────────────────────────────────────────────────

interface StatusChipConfig {
  label: string
  style: string
}

const STATUS_CHIP: Partial<Record<SubmissionStatus, StatusChipConfig>> = {
  ACCEPTED: {
    label: 'Accepted',
    style: 'text-[#10B981] bg-[#10B98115] border border-[#10B98130]',
  },
  WRONG_ANSWER: {
    label: 'Wrong Answer',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
  TIME_LIMIT_EXCEEDED: {
    label: 'TLE',
    style: 'text-[#F59E0B] bg-[#F59E0B15] border border-[#F59E0B30]',
  },
  MEMORY_LIMIT_EXCEEDED: {
    label: 'MLE',
    style: 'text-[#F59E0B] bg-[#F59E0B15] border border-[#F59E0B30]',
  },
  RUNTIME_ERROR: {
    label: 'Runtime Error',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
  COMPILATION_ERROR: {
    label: 'Compilation Error',
    style: 'text-[#EF4444] bg-[#EF444415] border border-[#EF444430]',
  },
}

// ── Spinner ────────────────────────────────────────────────────────────────────

const Spinner: React.FC<{ size?: number }> = ({ size = 14 }) => (
  <motion.svg
    width={size}
    height={size}
    viewBox="0 0 14 14"
    animate={{ rotate: 360 }}
    transition={{ repeat: Infinity, duration: 0.8, ease: 'linear' }}
  >
    <circle
      cx="7"
      cy="7"
      r="5"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeDasharray="20 10"
      strokeLinecap="round"
    />
  </motion.svg>
)

// ── Component ─────────────────────────────────────────────────────────────────

const EditorToolbar: React.FC<EditorToolbarProps> = ({
  language,
  onLanguageChange,
  onSubmit,
  onRun,
  onReset,
  isSubmitting,
  isRunning,
  submissionStatus,
  problemTitle,
  difficulty,
}) => {
  const [langOpen, setLangOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  // Close dropdown on outside click
  React.useEffect(() => {
    if (!langOpen) return
    const handler = (e: MouseEvent) => {
      if (!dropdownRef.current?.contains(e.target as Node)) {
        setLangOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [langOpen])

  const statusChip = STATUS_CHIP[submissionStatus]

  return (
    <div className="flex items-center justify-between px-3 py-2 bg-[#111113] border-b border-[#18181C] gap-2 min-h-[48px] shrink-0">
      {/* ── Left: Language selector ── */}
      <div className="relative" ref={dropdownRef}>
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={() => setLangOpen((o) => !o)}
          className="flex items-center gap-2 px-3 py-1.5 rounded-md bg-[#18181C] border border-[#475569]/30 text-[#F8F8F2] text-sm font-medium hover:border-[#6366F1]/50 hover:bg-[#6366F1]/10 transition-all duration-150 select-none"
          aria-haspopup="listbox"
          aria-expanded={langOpen}
        >
          <span className="text-base leading-none">
            {LANG_META[language].icon}
          </span>
          <span>{LANG_META[language].label}</span>
          <motion.svg
            width="12"
            height="12"
            viewBox="0 0 12 12"
            fill="none"
            animate={{ rotate: langOpen ? 180 : 0 }}
            transition={{ duration: 0.15 }}
            className="text-[#475569]"
          >
            <path
              d="M2 4l4 4 4-4"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </motion.svg>
        </motion.button>

        <AnimatePresence>
          {langOpen && (
            <motion.ul
              role="listbox"
              initial={{ opacity: 0, y: -6, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -6, scale: 0.97 }}
              transition={{ duration: 0.12 }}
              className="absolute top-full mt-1.5 left-0 z-50 w-44 bg-[#111113] border border-[#18181C] rounded-lg shadow-2xl shadow-black/60 overflow-hidden py-1"
            >
              {ALL_LANGUAGES.map((lang) => (
                <motion.li
                  key={lang}
                  whileHover={{ backgroundColor: 'rgba(99,102,241,0.1)' }}
                  role="option"
                  aria-selected={lang === language}
                  onClick={() => {
                    onLanguageChange(lang)
                    setLangOpen(false)
                  }}
                  className={`flex items-center gap-2.5 px-3 py-2 cursor-pointer text-sm transition-colors ${
                    lang === language
                      ? 'text-[#6366F1] bg-[#6366F1]/10'
                      : 'text-[#94A3B8] hover:text-[#F8F8F2]'
                  }`}
                >
                  <span className="text-base">{LANG_META[lang].icon}</span>
                  <span>{LANG_META[lang].label}</span>
                  {lang === language && (
                    <span className="ml-auto text-[#6366F1]">✓</span>
                  )}
                </motion.li>
              ))}
            </motion.ul>
          )}
        </AnimatePresence>
      </div>

      {/* ── Center: Problem title + difficulty ── */}
      <div className="flex-1 flex items-center justify-center gap-2 min-w-0">
        {problemTitle && (
          <span className="text-sm font-medium text-[#F8F8F2] truncate max-w-xs">
            {problemTitle}
          </span>
        )}
        {difficulty && (
          <span
            className={`text-xs font-semibold px-2 py-0.5 rounded-full shrink-0 ${DIFFICULTY_STYLES[difficulty]}`}
          >
            {difficulty}
          </span>
        )}
        <AnimatePresence>
          {statusChip && (
            <motion.span
              key={submissionStatus}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.8 }}
              className={`text-xs font-semibold px-2 py-0.5 rounded-full shrink-0 ${statusChip.style}`}
            >
              {statusChip.label}
            </motion.span>
          )}
        </AnimatePresence>
      </div>

      {/* ── Right: Actions ── */}
      <div className="flex items-center gap-2 shrink-0">
        {/* Reset */}
        <motion.button
          whileTap={{ scale: 0.93 }}
          onClick={onReset}
          title="Reset to default template"
          className="p-1.5 rounded-md text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C] transition-colors"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path
              d="M2 8a6 6 0 1 1 1.5 4"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
            />
            <path
              d="M2 12V8h4"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </motion.button>

        {/* Run button */}
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={onRun}
          disabled={isRunning || isSubmitting}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium bg-[#22D3EE]/10 text-[#22D3EE] border border-[#22D3EE]/30 hover:bg-[#22D3EE]/20 hover:border-[#22D3EE]/50 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-150"
        >
          {isRunning ? (
            <Spinner />
          ) : (
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <polygon
                points="3,1 12,6.5 3,12"
                fill="currentColor"
              />
            </svg>
          )}
          <span>{isRunning ? 'Running…' : 'Run'}</span>
        </motion.button>

        {/* Submit button */}
        <motion.button
          whileTap={{ scale: 0.95 }}
          onClick={onSubmit}
          disabled={isSubmitting || isRunning}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm font-medium bg-[#6366F1] text-white hover:bg-[#5254CC] disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-150 shadow-lg shadow-[#6366F1]/25"
        >
          {isSubmitting ? (
            <Spinner />
          ) : (
            <svg width="13" height="13" viewBox="0 0 13 13" fill="none">
              <path
                d="M1 7l4 4 7-8"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          )}
          <span>{isSubmitting ? 'Submitting…' : 'Submit'}</span>
        </motion.button>
      </div>
    </div>
  )
}

export default EditorToolbar
