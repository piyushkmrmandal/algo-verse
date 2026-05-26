import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useMutation } from '@tanstack/react-query'
import { api } from '../lib/api'
import AppNav from '../components/ui/AppNav'

interface RoomResponse {
  id: string
  code: string
  language: string
  type: string
  status: string
}

const LANGUAGE_OPTIONS = [
  { value: 'javascript', label: 'JavaScript', icon: '🟨' },
  { value: 'python', label: 'Python', icon: '🐍' },
  { value: 'java', label: 'Java', icon: '☕' },
  { value: 'cpp', label: 'C++', icon: '⚙️' },
  { value: 'typescript', label: 'TypeScript', icon: '🔷' },
]

export default function CollaboratePage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<'create' | 'join'>('create')
  const [language, setLanguage] = useState('javascript')
  const [joinCode, setJoinCode] = useState('')
  const [joinError, setJoinError] = useState('')

  const createRoom = useMutation({
    mutationFn: () =>
      api.post<RoomResponse>('/collab/rooms', {
        language,
        type: 'PRACTICE',
        isPublic: false,
      }).then((r) => r.data),
    onSuccess: (room) => {
      navigate(`/collab/room/${room.code}`)
    },
  })

  const joinRoom = useMutation({
    mutationFn: (code: string) =>
      api.post<RoomResponse>(`/collab/rooms/join/${code.trim().toUpperCase()}`).then((r) => r.data),
    onSuccess: (room) => {
      navigate(`/collab/room/${room.code}`)
    },
    onError: () => {
      setJoinError('Room not found or has ended. Check the code and try again.')
    },
  })

  const handleJoin = (e: React.FormEvent) => {
    e.preventDefault()
    setJoinError('')
    if (!joinCode.trim()) return
    joinRoom.mutate(joinCode)
  }

  return (
    <div className="min-h-screen bg-bg-base">
      <AppNav />

      <div className="max-w-2xl mx-auto px-4 py-16">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: -16 }}
          animate={{ opacity: 1, y: 0 }}
          className="text-center mb-10"
        >
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-brand-primary/10 border border-brand-primary/20 mb-4">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" className="text-brand-primary" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-text-primary mb-2">
            Collaborative Coding
          </h1>
          <p className="text-text-secondary">
            Code together in real time. Share a room code and start solving.
          </p>
        </motion.div>

        {/* Tab toggle */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="glass rounded-2xl overflow-hidden"
        >
          <div className="flex border-b border-border-subtle" data-testid="room-tabs">
            {(['create', 'join'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                data-testid={`tab-${t}`}
                aria-selected={tab === t}
                className={`flex-1 py-3.5 text-sm font-semibold transition-all duration-200 capitalize ${
                  tab === t
                    ? 'text-brand-primary bg-brand-primary/5 border-b-2 border-brand-primary'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {t === 'create' ? '+ New Room' : '→ Join Room'}
              </button>
            ))}
          </div>

          <div className="p-8">
            <AnimatePresence mode="wait">
              {tab === 'create' ? (
                <motion.div
                  key="create"
                  initial={{ opacity: 0, x: -12 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 12 }}
                  transition={{ duration: 0.15 }}
                  className="space-y-6"
                >
                  <div>
                    <label className="block text-sm font-medium text-text-secondary mb-3">
                      Language
                    </label>
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2" data-testid="language-selector">
                      {LANGUAGE_OPTIONS.map((lang) => (
                        <button
                          key={lang.value}
                          onClick={() => setLanguage(lang.value)}
                          data-testid={`language-option-${lang.value}`}
                          aria-pressed={language === lang.value}
                          className={`flex items-center gap-2.5 px-3 py-2.5 rounded-xl border text-sm font-medium transition-all duration-150 ${
                            language === lang.value
                              ? 'border-brand-primary bg-brand-primary/10 text-brand-primary'
                              : 'border-border-subtle bg-bg-elevated text-text-secondary hover:border-border-default hover:text-text-primary'
                          }`}
                        >
                          <span>{lang.icon}</span>
                          {lang.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  <motion.button
                    whileTap={{ scale: 0.98 }}
                    onClick={() => createRoom.mutate()}
                    disabled={createRoom.isPending}
                    data-testid="create-room-btn"
                    className="btn-primary w-full py-3 text-base"
                  >
                    {createRoom.isPending ? (
                      <span className="flex items-center justify-center gap-2">
                        <svg className="animate-spin w-4 h-4" viewBox="0 0 24 24" fill="none">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                        </svg>
                        Creating room…
                      </span>
                    ) : (
                      'Create Room'
                    )}
                  </motion.button>

                  {createRoom.isError && (
                    <p className="text-sm text-error text-center">Failed to create room. Try again.</p>
                  )}
                </motion.div>
              ) : (
                <motion.div
                  key="join"
                  initial={{ opacity: 0, x: 12 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -12 }}
                  transition={{ duration: 0.15 }}
                  className="space-y-5"
                >
                  <div>
                    <label className="block text-sm font-medium text-text-secondary mb-2">
                      Room Code
                    </label>
                    <p className="text-xs text-text-muted mb-3">
                      Ask your partner to share their room code (format: ABC-DEF-GHI)
                    </p>
                    <form onSubmit={handleJoin} className="flex gap-3" data-testid="join-room-form">
                      <input
                        className="input-base flex-1 text-center tracking-[0.3em] uppercase text-lg font-mono"
                        placeholder="ABC-DEF-GHI"
                        value={joinCode}
                        onChange={(e) => {
                          setJoinError('')
                          setJoinCode(e.target.value)
                        }}
                        maxLength={11}
                        autoFocus
                        data-testid="join-code-input"
                      />
                      <motion.button
                        type="submit"
                        whileTap={{ scale: 0.97 }}
                        disabled={joinRoom.isPending || !joinCode.trim()}
                        data-testid="join-room-btn"
                        className="btn-primary px-6 shrink-0"
                      >
                        {joinRoom.isPending ? '…' : 'Join'}
                      </motion.button>
                    </form>
                    {joinError && (
                      <motion.p
                        initial={{ opacity: 0, y: -4 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="mt-2 text-sm text-error"
                        data-testid="join-error-msg"
                        role="alert"
                      >
                        {joinError}
                      </motion.p>
                    )}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </motion.div>

        {/* How it works */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15 }}
          className="mt-8 grid grid-cols-3 gap-4"
        >
          {[
            { icon: '🚀', title: 'Real-time sync', desc: 'Every keystroke is synced instantly via OT' },
            { icon: '🤖', title: 'AI Tutor', desc: 'Get hints together without spoilers' },
            { icon: '🎯', title: 'Run & submit', desc: 'Execute code and grade together' },
          ].map((item) => (
            <div key={item.title} className="text-center p-4 rounded-xl bg-bg-elevated border border-border-subtle">
              <div className="text-2xl mb-2">{item.icon}</div>
              <p className="text-xs font-semibold text-text-primary mb-1">{item.title}</p>
              <p className="text-xs text-text-muted leading-snug">{item.desc}</p>
            </div>
          ))}
        </motion.div>
      </div>
    </div>
  )
}
