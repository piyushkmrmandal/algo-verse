import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import ReactMarkdown from 'react-markdown'
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  isStreaming?: boolean
  timestamp: Date
}

interface AiTutorProps {
  problemId: string
  submissionId?: string
  isOpen: boolean
  onClose: () => void
  currentCode?: string
  language?: string
}

// ── Quick actions ─────────────────────────────────────────────────────────────

const QUICK_ACTIONS = [
  { label: 'Give me a hint', icon: '💡' },
  { label: 'Explain the pattern', icon: '🧩' },
  { label: 'Review my code', icon: '🔍' },
  { label: "What's the time complexity?", icon: '⏱' },
  { label: 'Show a simpler approach', icon: '✨' },
] as const

// ── Syntax theme matching AlgoVerse dark ──────────────────────────────────────

const PRISM_THEME: Record<string, React.CSSProperties> = {
  'code[class*="language-"]': {
    color: '#F8F8F2',
    background: 'none',
    fontFamily: '"JetBrains Mono", monospace',
    fontSize: '0.8rem',
    lineHeight: '1.5',
  },
  'pre[class*="language-"]': {
    background: '#0A0A0B',
    padding: '1rem',
    borderRadius: '0.5rem',
    overflow: 'auto',
    margin: '0.5rem 0',
    border: '1px solid #18181C',
  },
  comment: { color: '#6272A4', fontStyle: 'italic' },
  keyword: { color: '#BD93F9', fontWeight: 'bold' },
  string: { color: '#F1FA8C' },
  number: { color: '#FF79C6' },
  function: { color: '#50FA7B' },
  variable: { color: '#FFB86C' },
  operator: { color: '#FF79C6' },
  punctuation: { color: '#F8F8F2' },
  'class-name': { color: '#8BE9FD' },
  builtin: { color: '#8BE9FD' },
  constant: { color: '#BD93F9' },
}

// ── Typing cursor ─────────────────────────────────────────────────────────────

const TypingCursor: React.FC = () => (
  <motion.span
    animate={{ opacity: [1, 0] }}
    transition={{ repeat: Infinity, duration: 0.6, ease: 'steps(1)' }}
    className="inline-block w-0.5 h-4 bg-[#6366F1] ml-0.5 align-text-bottom"
  />
)

// ── Hint level stars ─────────────────────────────────────────────────────────

const HintLevelIndicator: React.FC<{ level: number; maxLevel?: number }> = ({
  level,
  maxLevel = 5,
}) => (
  <div className="flex items-center gap-0.5" title={`Hint level: ${level}/${maxLevel}`}>
    {Array.from({ length: maxLevel }, (_, i) => (
      <svg
        key={i}
        width="10"
        height="10"
        viewBox="0 0 10 10"
        fill={i < level ? '#F59E0B' : 'none'}
        stroke={i < level ? '#F59E0B' : '#475569'}
        strokeWidth="1"
      >
        <path d="M5 1l1.2 2.4 2.6.4-1.9 1.8.4 2.6L5 7l-2.3 1.2.4-2.6L1.2 3.8l2.6-.4z" />
      </svg>
    ))}
  </div>
)

// ── Code block renderer ────────────────────────────────────────────────────────

const CodeBlock: React.FC<{
  className?: string
  children?: React.ReactNode
}> = ({ className, children }) => {
  const match = /language-(\w+)/.exec(className ?? '')
  const lang = match ? match[1] : 'text'

  return (
    <SyntaxHighlighter
      language={lang}
      style={PRISM_THEME}
      PreTag="div"
    >
      {String(children).replace(/\n$/, '')}
    </SyntaxHighlighter>
  )
}

// ── Message bubble ─────────────────────────────────────────────────────────────

const MessageBubble: React.FC<{ message: Message }> = ({ message }) => {
  const isUser = message.role === 'user'

  return (
    <motion.div
      initial={{ opacity: 0, y: 8, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.2 }}
      className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-3`}
    >
      {!isUser && (
        <div className="w-7 h-7 rounded-full bg-gradient-to-br from-[#6366F1] to-[#22D3EE] flex items-center justify-center text-white text-xs font-bold shrink-0 mr-2 mt-0.5">
          AI
        </div>
      )}

      <div
        className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed ${
          isUser
            ? 'bg-[#6366F1] text-white rounded-tr-sm'
            : 'bg-[#18181C] text-[#F8F8F2] rounded-tl-sm border border-[#18181C]'
        }`}
      >
        {isUser ? (
          <span className="whitespace-pre-wrap">{message.content}</span>
        ) : (
          <div className="prose prose-invert prose-sm max-w-none [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
            <ReactMarkdown
              components={{
                code: CodeBlock as React.ComponentType<React.HTMLAttributes<HTMLElement>>,
                p: ({ children }) => <p className="mb-2 last:mb-0">{children}</p>,
                ul: ({ children }) => (
                  <ul className="list-disc list-inside mb-2 space-y-0.5">{children}</ul>
                ),
                ol: ({ children }) => (
                  <ol className="list-decimal list-inside mb-2 space-y-0.5">{children}</ol>
                ),
                li: ({ children }) => <li className="text-[#94A3B8]">{children}</li>,
                strong: ({ children }) => (
                  <strong className="text-[#F8F8F2] font-semibold">{children}</strong>
                ),
                em: ({ children }) => <em className="text-[#94A3B8]">{children}</em>,
                blockquote: ({ children }) => (
                  <blockquote className="border-l-2 border-[#6366F1] pl-3 text-[#94A3B8] italic my-2">
                    {children}
                  </blockquote>
                ),
              }}
            >
              {message.content}
            </ReactMarkdown>
            {message.isStreaming && <TypingCursor />}
          </div>
        )}
      </div>
    </motion.div>
  )
}

// ── Component ─────────────────────────────────────────────────────────────────

const AiTutor: React.FC<AiTutorProps> = ({
  problemId,
  submissionId,
  isOpen,
  onClose,
  currentCode = '',
  language = 'python',
}) => {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content:
        "Hi! I'm your AI tutor for this problem. I can give hints, explain approaches, review your code, or discuss complexity. What would you like to explore?",
      timestamp: new Date(),
    },
  ])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [hintLevel, setHintLevel] = useState(0)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, scrollToBottom])

  // Auto-resize textarea
  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value)
    e.target.style.height = 'auto'
    e.target.style.height = `${Math.min(e.target.scrollHeight, 120)}px`
  }

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim()
      if (!trimmed || isLoading) return

      const userMsg: Message = {
        id: `user-${Date.now()}`,
        role: 'user',
        content: trimmed,
        timestamp: new Date(),
      }

      setMessages((prev) => [...prev, userMsg])
      setInput('')
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto'
      }
      setIsLoading(true)

      // Streaming AI response via SSE
      const aiMsgId = `ai-${Date.now()}`
      setMessages((prev) => [
        ...prev,
        {
          id: aiMsgId,
          role: 'assistant',
          content: '',
          isStreaming: true,
          timestamp: new Date(),
        },
      ])

      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller

      try {
        const response = await fetch('/api/ai/tutor/stream', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            problemId,
            submissionId,
            message: trimmed,
            code: currentCode,
            language,
            hintLevel,
            history: messages.map((m) => ({ role: m.role, content: m.content })),
          }),
          signal: controller.signal,
        })

        if (!response.ok || !response.body) {
          throw new Error(`HTTP ${response.status}`)
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let accumulated = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          const chunk = decoder.decode(value, { stream: true })
          const lines = chunk.split('\n')

          for (const line of lines) {
            if (line.startsWith('data: ')) {
              const data = line.slice(6)
              if (data === '[DONE]') break
              try {
                const parsed = JSON.parse(data) as { token?: string }
                if (parsed.token) {
                  accumulated += parsed.token
                  setMessages((prev) =>
                    prev.map((m) =>
                      m.id === aiMsgId
                        ? { ...m, content: accumulated }
                        : m,
                    ),
                  )
                }
              } catch {
                // Ignore malformed SSE lines
              }
            }
          }
        }

        setMessages((prev) =>
          prev.map((m) =>
            m.id === aiMsgId ? { ...m, isStreaming: false } : m,
          ),
        )
      } catch (err) {
        if ((err as Error).name === 'AbortError') return
        setMessages((prev) =>
          prev.map((m) =>
            m.id === aiMsgId
              ? {
                  ...m,
                  content:
                    'Sorry, I encountered an error. Please try again.',
                  isStreaming: false,
                }
              : m,
          ),
        )
      } finally {
        setIsLoading(false)
      }
    },
    [problemId, submissionId, currentCode, language, hintLevel, isLoading, messages],
  )

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage(input)
    }
  }

  const handleQuickAction = (label: string) => {
    if (label === 'Give me a hint') {
      setHintLevel((l) => Math.min(l + 1, 5))
    }
    sendMessage(label)
  }

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ x: '100%', opacity: 0 }}
          animate={{ x: 0, opacity: 1 }}
          exit={{ x: '100%', opacity: 0 }}
          transition={{ type: 'spring', damping: 28, stiffness: 280 }}
          className="absolute inset-0 z-40 flex flex-col bg-[#111113] border-l border-[#18181C]"
        >
          {/* Header */}
          <div className="flex items-center justify-between px-4 py-3 border-b border-[#18181C] shrink-0">
            <div className="flex items-center gap-2.5">
              <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#6366F1] to-[#22D3EE] flex items-center justify-center text-white text-xs font-bold shadow-lg shadow-[#6366F1]/30">
                AI
              </div>
              <div>
                <div className="text-sm font-semibold text-[#F8F8F2]">AI Tutor</div>
                <div className="text-[11px] text-[#475569]">Context-aware assistance</div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-1.5">
                <span className="text-[11px] text-[#475569]">Hints revealed:</span>
                <HintLevelIndicator level={hintLevel} />
              </div>
              <button
                onClick={onClose}
                className="p-1.5 rounded-md text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C] transition-colors"
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

          {/* Messages */}
          <div className="flex-1 overflow-y-auto px-4 py-4 space-y-1">
            {messages.map((msg) => (
              <MessageBubble key={msg.id} message={msg} />
            ))}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick actions */}
          <div className="px-4 pb-2 shrink-0">
            <div className="flex flex-wrap gap-1.5">
              {QUICK_ACTIONS.map((action) => (
                <motion.button
                  key={action.label}
                  whileTap={{ scale: 0.95 }}
                  onClick={() => handleQuickAction(action.label)}
                  disabled={isLoading}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-full text-xs bg-[#18181C] text-[#94A3B8] border border-[#475569]/20 hover:border-[#6366F1]/40 hover:text-[#F8F8F2] hover:bg-[#6366F1]/10 disabled:opacity-40 transition-all"
                >
                  <span>{action.icon}</span>
                  <span>{action.label}</span>
                </motion.button>
              ))}
            </div>
          </div>

          {/* Input area */}
          <div className="px-4 pb-4 shrink-0">
            <div className="flex items-end gap-2 bg-[#18181C] border border-[#475569]/20 rounded-xl px-3 py-2.5 focus-within:border-[#6366F1]/50 transition-colors">
              <textarea
                ref={textareaRef}
                value={input}
                onChange={handleInputChange}
                onKeyDown={handleKeyDown}
                placeholder="Ask anything about this problem… (Enter to send, Shift+Enter for newline)"
                rows={1}
                disabled={isLoading}
                className="flex-1 bg-transparent text-sm text-[#F8F8F2] placeholder-[#475569] resize-none outline-none min-h-[20px] max-h-[120px] leading-relaxed disabled:opacity-50"
              />
              <motion.button
                whileTap={{ scale: 0.9 }}
                onClick={() => sendMessage(input)}
                disabled={!input.trim() || isLoading}
                className="p-1.5 rounded-lg bg-[#6366F1] text-white disabled:opacity-40 hover:bg-[#5254CC] transition-colors shrink-0"
              >
                {isLoading ? (
                  <motion.svg
                    width="14"
                    height="14"
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
                      strokeDasharray="20 8"
                      strokeLinecap="round"
                    />
                  </motion.svg>
                ) : (
                  <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                    <path
                      d="M2 7h10M8 3l4 4-4 4"
                      stroke="currentColor"
                      strokeWidth="1.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                )}
              </motion.button>
            </div>
          </div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

export default AiTutor
