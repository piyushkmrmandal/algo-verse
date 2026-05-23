import React, { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import ReactMarkdown from 'react-markdown'

// ── Types ─────────────────────────────────────────────────────────────────────

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface Example {
  id: number
  input: string
  output: string
  explanation?: string
}

export interface ProblemTag {
  id: string
  name: string
  slug: string
}

export interface CompanyTag {
  id: string
  name: string
  logoUrl?: string
}

export interface RelatedProblem {
  slug: string
  title: string
  difficulty: Difficulty
  acceptanceRate: number
}

export interface ProblemDetail {
  id: string
  slug: string
  title: string
  difficulty: Difficulty
  description: string
  constraints: string[]
  examples: Example[]
  tags: ProblemTag[]
  companyTags: CompanyTag[]
  acceptanceRate: number
  totalSubmissions: number
  isPro?: boolean
  relatedProblems: RelatedProblem[]
  hints?: string[]
}

interface ProblemStatementProps {
  problem: ProblemDetail
  isPro?: boolean
}

// ── Difficulty badge ───────────────────────────────────────────────────────────

const DIFFICULTY_STYLES: Record<Difficulty, string> = {
  EASY: 'text-[#10B981] bg-[#10B98115] border-[#10B98130]',
  MEDIUM: 'text-[#F59E0B] bg-[#F59E0B15] border-[#F59E0B30]',
  HARD: 'text-[#EF4444] bg-[#EF444415] border-[#EF444430]',
}

// ── Code block ────────────────────────────────────────────────────────────────

const InlineCode: React.FC<{ children?: React.ReactNode }> = ({ children }) => (
  <code className="font-mono text-[#8BE9FD] bg-[#18181C] px-1.5 py-0.5 rounded text-[0.85em]">
    {children}
  </code>
)

const BlockCode: React.FC<{ children?: React.ReactNode }> = ({ children }) => (
  <pre className="bg-[#0A0A0B] border border-[#18181C] rounded-lg p-4 font-mono text-sm text-[#F8F8F2] overflow-x-auto my-3 leading-relaxed">
    <code>{children}</code>
  </pre>
)

// ── Copy button for example I/O ───────────────────────────────────────────────

const CopyableBlock: React.FC<{ label: string; value: string }> = ({
  label,
  value,
}) => {
  const [copied, setCopied] = useState(false)

  const handleCopy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-semibold text-[#475569] uppercase tracking-widest">
          {label}
        </span>
        <button
          onClick={handleCopy}
          className="text-[10px] text-[#475569] hover:text-[#94A3B8] transition-colors"
        >
          {copied ? '✓ Copied' : 'Copy'}
        </button>
      </div>
      <pre className="bg-[#0A0A0B] rounded-lg px-3 py-2.5 text-sm text-[#F8F8F2] font-mono overflow-x-auto border border-[#18181C]">
        {value}
      </pre>
    </div>
  )
}

// ── Markdown renderer ─────────────────────────────────────────────────────────

const MarkdownContent: React.FC<{ content: string }> = ({ content }) => (
  <ReactMarkdown
    components={{
      code: ({ className, children, ...props }) => {
        const isBlock = !props.ref
        return isBlock ? (
          <BlockCode>{children}</BlockCode>
        ) : (
          <InlineCode>{children}</InlineCode>
        )
      },
      p: ({ children }) => (
        <p className="text-[#94A3B8] leading-relaxed mb-3 last:mb-0">{children}</p>
      ),
      ul: ({ children }) => (
        <ul className="list-disc list-inside text-[#94A3B8] space-y-1 mb-3 pl-1">
          {children}
        </ul>
      ),
      ol: ({ children }) => (
        <ol className="list-decimal list-inside text-[#94A3B8] space-y-1 mb-3 pl-1">
          {children}
        </ol>
      ),
      li: ({ children }) => <li className="leading-relaxed">{children}</li>,
      strong: ({ children }) => (
        <strong className="font-semibold text-[#F8F8F2]">{children}</strong>
      ),
      em: ({ children }) => <em className="text-[#8BE9FD]">{children}</em>,
      h3: ({ children }) => (
        <h3 className="text-base font-semibold text-[#F8F8F2] mt-4 mb-2">{children}</h3>
      ),
      blockquote: ({ children }) => (
        <blockquote className="border-l-2 border-[#6366F1] pl-4 text-[#94A3B8] italic my-3">
          {children}
        </blockquote>
      ),
    }}
  >
    {content}
  </ReactMarkdown>
)

// ── Component ─────────────────────────────────────────────────────────────────

const ProblemStatement: React.FC<ProblemStatementProps> = ({
  problem,
  isPro = false,
}) => {
  const [bookmarked, setBookmarked] = useState(false)
  const [shareToast, setShareToast] = useState(false)

  const handleShare = async () => {
    const url = `${window.location.origin}/problem/${problem.slug}`
    await navigator.clipboard.writeText(url)
    setShareToast(true)
    setTimeout(() => setShareToast(false), 2000)
  }

  const handleBookmark = () => setBookmarked((b) => !b)

  const formatNumber = (n: number): string => {
    if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
    if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
    return String(n)
  }

  return (
    <div className="h-full overflow-y-auto bg-[#111113] text-[#F8F8F2]">
      <div className="p-5 space-y-6">
        {/* Title row */}
        <div className="space-y-2">
          <div className="flex items-start justify-between gap-3">
            <h1 className="text-xl font-bold text-[#F8F8F2] leading-tight">
              {problem.title}
            </h1>
            <div className="flex items-center gap-2 shrink-0">
              {/* Bookmark */}
              <motion.button
                whileTap={{ scale: 0.85 }}
                onClick={handleBookmark}
                title={bookmarked ? 'Remove bookmark' : 'Bookmark'}
                className="p-1.5 rounded-md hover:bg-[#18181C] transition-colors"
              >
                <motion.svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill={bookmarked ? '#6366F1' : 'none'}
                  stroke={bookmarked ? '#6366F1' : '#475569'}
                  strokeWidth="1.5"
                  animate={{ scale: bookmarked ? [1, 1.3, 1] : 1 }}
                  transition={{ duration: 0.3 }}
                >
                  <path
                    d="M3 2h10v13l-5-3-5 3V2Z"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </motion.svg>
              </motion.button>

              {/* Share */}
              <div className="relative">
                <motion.button
                  whileTap={{ scale: 0.9 }}
                  onClick={handleShare}
                  title="Copy link"
                  className="p-1.5 rounded-md hover:bg-[#18181C] transition-colors text-[#475569] hover:text-[#94A3B8]"
                >
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <circle cx="13" cy="3" r="1.5" />
                    <circle cx="3" cy="8" r="1.5" />
                    <circle cx="13" cy="13" r="1.5" />
                    <path d="M4.5 7.2L11.5 3.8M4.5 8.8L11.5 12.2" strokeLinecap="round" />
                  </svg>
                </motion.button>
                <AnimatePresence>
                  {shareToast && (
                    <motion.div
                      initial={{ opacity: 0, y: 4 }}
                      animate={{ opacity: 1, y: 0 }}
                      exit={{ opacity: 0, y: 4 }}
                      className="absolute right-0 top-full mt-1 text-xs bg-[#18181C] text-[#10B981] px-2 py-1 rounded shadow-lg whitespace-nowrap z-10"
                    >
                      Link copied!
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>
            </div>
          </div>

          {/* Stats row */}
          <div className="flex items-center gap-3 flex-wrap">
            <span
              className={`text-xs font-semibold px-2.5 py-0.5 rounded-full border ${DIFFICULTY_STYLES[problem.difficulty]}`}
            >
              {problem.difficulty}
            </span>
            <span className="text-xs text-[#475569]">
              Acceptance:{' '}
              <span className="text-[#94A3B8]">
                {problem.acceptanceRate.toFixed(1)}%
              </span>
            </span>
            <span className="text-xs text-[#475569]">
              Submissions:{' '}
              <span className="text-[#94A3B8]">
                {formatNumber(problem.totalSubmissions)}
              </span>
            </span>
          </div>
        </div>

        {/* Description */}
        <section>
          <MarkdownContent content={problem.description} />
        </section>

        {/* Examples */}
        {problem.examples.length > 0 && (
          <section className="space-y-4">
            {problem.examples.map((example, idx) => (
              <div
                key={example.id}
                className="rounded-xl bg-[#18181C] border border-[#18181C] p-4 space-y-3"
              >
                <div className="text-xs font-bold text-[#475569] uppercase tracking-widest">
                  Example {idx + 1}
                </div>
                <CopyableBlock label="Input" value={example.input} />
                <CopyableBlock label="Output" value={example.output} />
                {example.explanation && (
                  <div className="pt-1 border-t border-[#0A0A0B]">
                    <span className="text-[11px] font-semibold text-[#475569] uppercase tracking-widest">
                      Explanation
                    </span>
                    <p className="text-sm text-[#94A3B8] mt-1 leading-relaxed">
                      {example.explanation}
                    </p>
                  </div>
                )}
              </div>
            ))}
          </section>
        )}

        {/* Constraints */}
        {problem.constraints.length > 0 && (
          <section>
            <h3 className="text-sm font-semibold text-[#F8F8F2] mb-2">Constraints</h3>
            <ul className="space-y-1.5">
              {problem.constraints.map((c, i) => (
                <li key={i} className="flex items-start gap-2 text-sm text-[#94A3B8]">
                  <span className="text-[#6366F1] mt-0.5 shrink-0">•</span>
                  <code className="font-mono text-[0.83rem] text-[#8BE9FD]">{c}</code>
                </li>
              ))}
            </ul>
          </section>
        )}

        {/* Tags */}
        {problem.tags.length > 0 && (
          <section>
            <h3 className="text-xs font-semibold text-[#475569] uppercase tracking-widest mb-2">
              Topics
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {problem.tags.map((tag) => (
                <a
                  key={tag.id}
                  href={`/topic/${tag.slug}`}
                  className="text-xs px-2.5 py-1 rounded-full bg-[#6366F1]/10 text-[#6366F1] border border-[#6366F1]/20 hover:bg-[#6366F1]/20 transition-colors"
                >
                  {tag.name}
                </a>
              ))}
            </div>
          </section>
        )}

        {/* Company tags */}
        {problem.companyTags.length > 0 && (
          <section>
            <h3 className="text-xs font-semibold text-[#475569] uppercase tracking-widest mb-2">
              Companies
            </h3>
            <div className="flex flex-wrap gap-1.5">
              {problem.companyTags.map((company) =>
                isPro ? (
                  <span
                    key={company.id}
                    className="text-xs px-2.5 py-1 rounded-full bg-[#18181C] text-[#94A3B8] border border-[#475569]/20"
                  >
                    {company.name}
                  </span>
                ) : (
                  <span
                    key={company.id}
                    className="flex items-center gap-1 text-xs px-2.5 py-1 rounded-full bg-[#18181C] text-[#475569] border border-[#475569]/20 cursor-not-allowed select-none"
                    title="Upgrade to Pro to see company tags"
                  >
                    <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                      <rect x="2" y="4.5" width="6" height="4.5" rx="1" stroke="currentColor" strokeWidth="1" />
                      <path d="M3.5 4.5V3a1.5 1.5 0 1 1 3 0v1.5" stroke="currentColor" strokeWidth="1" strokeLinecap="round" />
                    </svg>
                    ••••••
                  </span>
                ),
              )}
            </div>
          </section>
        )}

        {/* Related problems */}
        {problem.relatedProblems.length > 0 && (
          <section>
            <h3 className="text-xs font-semibold text-[#475569] uppercase tracking-widest mb-2">
              Related Problems
            </h3>
            <div className="flex gap-2 overflow-x-auto pb-1 scrollbar-thin scrollbar-thumb-[#18181C]">
              {problem.relatedProblems.map((rp) => (
                <a
                  key={rp.slug}
                  href={`/problem/${rp.slug}`}
                  className="shrink-0 rounded-xl bg-[#18181C] border border-[#475569]/15 hover:border-[#6366F1]/30 px-3 py-2.5 space-y-1 transition-colors group"
                >
                  <div className="text-xs text-[#F8F8F2] group-hover:text-[#6366F1] font-medium max-w-[140px] truncate transition-colors">
                    {rp.title}
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span
                      className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-full border ${DIFFICULTY_STYLES[rp.difficulty]}`}
                    >
                      {rp.difficulty}
                    </span>
                    <span className="text-[10px] text-[#475569]">
                      {rp.acceptanceRate.toFixed(0)}%
                    </span>
                  </div>
                </a>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  )
}

export default ProblemStatement
