import { useEffect, useRef, useState, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { Client, type IMessage } from '@stomp/stompjs'
import MonacoEditor from '@monaco-editor/react'
import { useAuthStore } from '../stores/auth-store'
import { OTClient, type ServerOp } from '../lib/ot-client'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Participant {
  userId: string
  displayName: string
  role: string
  color: string
}

interface WsMessage {
  type: 'JOIN' | 'LEAVE' | 'OPERATION' | 'CURSOR' | 'LANGUAGE' | 'CHAT' | 'ROOM_STATE' | 'PRESENCE'
  roomCode: string
  userId?: string
  displayName?: string
  payload: Record<string, unknown>
}

interface RoomState {
  code: string
  content: string
  language: string
  version: number
  participants: Participant[]
}

const PARTICIPANT_COLORS = [
  '#6366F1', '#EC4899', '#10B981', '#F59E0B', '#3B82F6', '#8B5CF6',
]

const LANGUAGE_MAP: Record<string, string> = {
  javascript: 'javascript',
  typescript: 'typescript',
  python: 'python',
  java: 'java',
  cpp: 'cpp',
}

// ── ChatPanel ─────────────────────────────────────────────────────────────────

interface ChatMsg { userId: string; name: string; text: string; ts: number; color: string }

function ChatPanel({ messages, onSend, color: _color }: {
  messages: ChatMsg[]
  onSend: (text: string) => void
  color: string
}) {
  const [text, setText] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const send = (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim()) return
    onSend(text.trim())
    setText('')
  }

  return (
    <div className="flex flex-col h-full bg-[#111113] border-l border-[#18181C]">
      <div className="px-4 py-2.5 border-b border-[#18181C] text-xs font-semibold text-[#94A3B8] uppercase tracking-wider">
        Chat
      </div>
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {messages.length === 0 && (
          <p className="text-[#475569] text-xs text-center mt-4">No messages yet</p>
        )}
        {messages.map((m, i) => (
          <div key={i} className="text-xs">
            <span style={{ color: m.color }} className="font-semibold">{m.name}: </span>
            <span className="text-[#CBD5E1]">{m.text}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <form onSubmit={send} className="p-2 border-t border-[#18181C] flex gap-2">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Message…"
          className="flex-1 bg-[#18181C] border border-[#27272A] rounded-lg px-2.5 py-1.5 text-xs text-[#F8F8F2] placeholder:text-[#475569] focus:outline-none focus:border-[#6366F1]/50"
        />
        <button
          type="submit"
          disabled={!text.trim()}
          className="px-3 py-1.5 rounded-lg bg-[#6366F1] text-white text-xs font-medium disabled:opacity-40"
        >
          Send
        </button>
      </form>
    </div>
  )
}

// ── CollabRoomPage ────────────────────────────────────────────────────────────

export default function CollabRoomPage() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const accessToken = useAuthStore((s) => s.accessToken)

  // Room state
  const [content, setContent] = useState('')
  const [language, setLanguage] = useState('javascript')
  const [participants, setParticipants] = useState<Participant[]>([])
  const [connected, setConnected] = useState(false)
  const [connectionError, setConnectionError] = useState('')

  // UI state
  const [showChat, setShowChat] = useState(false)
  const [chatMessages, setChatMessages] = useState<ChatMsg[]>([])

  // Refs
  const clientRef = useRef<Client | null>(null)
  const otRef     = useRef<OTClient | null>(null)  // OT engine instance
  const contentRef = useRef('')                    // mirrors content for closure captures

  // Assign a color to this user
  const myColor = PARTICIPANT_COLORS[
    Math.abs(user?.id?.charCodeAt(0) ?? 0) % PARTICIPANT_COLORS.length
  ]

  // ── STOMP connection ─────────────────────────────────────────────────────────

  useEffect(() => {
    if (!code || !user) return

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const wsUrl = `${protocol}://${window.location.host}/ws-collab/websocket`

    // Initialise OT engine — send function publishes to STOMP
    const ot = new OTClient('', (op: ServerOp) => {
      client.publish({
        destination: '/app/collab.operation',
        body: JSON.stringify({
          type: 'OPERATION',
          roomCode: code,
          payload: op,
        }),
      })
    })
    otRef.current = ot

    const client = new Client({
      brokerURL: `${wsUrl}?token=${accessToken}`,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        setConnectionError('')

        // Subscribe to room topic
        client.subscribe(`/topic/room/${code}`, (msg: IMessage) => {
          try {
            const wsMsg = JSON.parse(msg.body) as WsMessage
            handleIncomingMessage(wsMsg)
          } catch { /* malformed */ }
        })

        // Subscribe to personal topic
        client.subscribe(`/user/queue/room`, (msg: IMessage) => {
          try {
            const wsMsg = JSON.parse(msg.body) as WsMessage
            handleIncomingMessage(wsMsg)
          } catch { /* malformed */ }
        })

        // Send JOIN
        client.publish({
          destination: '/app/collab.join',
          body: JSON.stringify({
            type: 'JOIN',
            roomCode: code,
            payload: { displayName: user.displayName },
          }),
        })
      },
      onDisconnect: () => {
        setConnected(false)
      },
      onStompError: (frame) => {
        setConnectionError(`Connection error: ${frame.headers?.message ?? 'Unknown'}`)
        setConnected(false)
      },
      onWebSocketError: () => {
        setConnectionError('WebSocket connection failed. Is the collaboration service running?')
        setConnected(false)
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      if (client.connected) {
        client.publish({
          destination: '/app/collab.leave',
          body: JSON.stringify({ type: 'LEAVE', roomCode: code, payload: {} }),
        })
      }
      client.deactivate()
    }
  }, [code, user, accessToken])

  // ── Incoming message handler ─────────────────────────────────────────────────

  const handleIncomingMessage = useCallback((msg: WsMessage) => {
    switch (msg.type) {
      case 'ROOM_STATE': {
        const state = msg.payload as unknown as RoomState
        const initialContent = state.content ?? ''
        const initialVersion = state.version ?? 0
        // Reset OT engine with authoritative server state
        otRef.current?.reset(initialContent, initialVersion)
        setContent(initialContent)
        contentRef.current = initialContent
        setLanguage(state.language ?? 'javascript')
        setParticipants(
          (state.participants ?? []).map((p, i) => ({
            ...p,
            color: PARTICIPANT_COLORS[i % PARTICIPANT_COLORS.length],
          }))
        )
        break
      }
      case 'OPERATION': {
        const opPayload = msg.payload as unknown as ServerOp
        if (msg.userId === user?.id) {
          // Server acknowledged our own operation — flush pending buffer
          const next = otRef.current?.acknowledge(opPayload.serverVersion ?? 0) ?? contentRef.current
          contentRef.current = next
          // No setContent needed — document is already correct locally
        } else {
          // Remote operation — transform via OT and apply
          const next = otRef.current?.applyRemote(opPayload) ?? contentRef.current
          contentRef.current = next
          setContent(next)
        }
        break
      }
      case 'JOIN': {
        const p: Participant = {
          userId: msg.userId ?? '',
          displayName: msg.displayName ?? 'Anonymous',
          role: 'PARTICIPANT',
          color: PARTICIPANT_COLORS[participants.length % PARTICIPANT_COLORS.length],
        }
        setParticipants((prev) => {
          if (prev.some((x) => x.userId === p.userId)) return prev
          return [...prev, p]
        })
        break
      }
      case 'LEAVE': {
        setParticipants((prev) => prev.filter((p) => p.userId !== msg.userId))
        break
      }
      case 'LANGUAGE': {
        const lang = (msg.payload as { language: string }).language
        setLanguage(lang ?? 'javascript')
        break
      }
      case 'CHAT': {
        const chatPayload = msg.payload as { text: string }
        const senderColor = PARTICIPANT_COLORS[
          Math.abs((msg.userId ?? '').charCodeAt(0)) % PARTICIPANT_COLORS.length
        ]
        setChatMessages((prev) => [...prev, {
          userId: msg.userId ?? '',
          name: msg.displayName ?? 'Anonymous',
          text: chatPayload.text,
          ts: Date.now(),
          color: senderColor,
        }])
        break
      }
    }
  }, [user?.id, participants.length])

  // ── Editor change → OT engine → broadcast operation ──────────────────────────

  const handleEditorChange = useCallback((newValue: string | undefined) => {
    const val = newValue ?? ''
    if (!clientRef.current?.connected || !otRef.current) return
    if (val === contentRef.current) return

    contentRef.current = val
    // OTClient.applyLocal diffs, buffers, transforms, and calls send() if needed
    otRef.current.applyLocal(val)
  }, [])

  // ── Language change ──────────────────────────────────────────────────────────

  const handleLanguageChange = useCallback((lang: string) => {
    setLanguage(lang)
    clientRef.current?.publish({
      destination: '/app/collab.language',
      body: JSON.stringify({ type: 'LANGUAGE', roomCode: code, payload: { language: lang } }),
    })
  }, [code])

  // ── Chat send ─────────────────────────────────────────────────────────────────

  const sendChat = useCallback((text: string) => {
    clientRef.current?.publish({
      destination: '/app/collab.chat',
      body: JSON.stringify({ type: 'CHAT', roomCode: code, payload: { text } }),
    })
    setChatMessages((prev) => [...prev, {
      userId: user?.id ?? '',
      name: user?.displayName ?? 'You',
      text,
      ts: Date.now(),
      color: myColor,
    }])
  }, [code, user, myColor])

  // ── Leave ─────────────────────────────────────────────────────────────────────

  const handleLeave = () => {
    clientRef.current?.publish({
      destination: '/app/collab.leave',
      body: JSON.stringify({ type: 'LEAVE', roomCode: code, payload: {} }),
    })
    navigate('/collaborate')
  }

  // ── Render ────────────────────────────────────────────────────────────────────

  if (connectionError) {
    return (
      <div className="flex flex-col items-center justify-center h-screen bg-[#0A0A0B] gap-4">
        <div className="text-5xl">⚡</div>
        <h2 className="text-lg font-bold text-[#F8F8F2]">Connection Failed</h2>
        <p className="text-sm text-[#94A3B8] text-center max-w-xs">{connectionError}</p>
        <button onClick={() => navigate('/collaborate')} className="px-4 py-2 bg-[#6366F1] text-white rounded-lg text-sm font-medium mt-2">
          Back to Collaborate
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-screen bg-[#0A0A0B] overflow-hidden">
      {/* Header */}
      <header className="flex items-center justify-between px-4 h-11 bg-[#111113] border-b border-[#18181C] shrink-0 z-20">
        <div className="flex items-center gap-3">
          <a href="/collaborate" className="text-sm font-bold text-[#6366F1]">AlgoVerse</a>
          <span className="text-[#27272A]">/</span>
          <span className="text-xs text-[#475569]">Collaborate</span>
          <span className="text-[#27272A]">/</span>
          {/* Room code — clickable to copy */}
          <button
            className="font-mono text-xs text-[#94A3B8] bg-[#18181C] px-2 py-0.5 rounded border border-[#27272A] hover:border-[#6366F1]/50 transition-colors"
            onClick={() => { navigator.clipboard.writeText(code ?? '') }}
            title="Click to copy room code"
            data-testid="room-code"
          >
            {code}
          </button>
        </div>

        <div className="flex items-center gap-3">
          {/* Connection indicator */}
          <div className="flex items-center gap-1.5">
            <div className={`w-2 h-2 rounded-full ${connected ? 'bg-green-400' : 'bg-yellow-400 animate-pulse'}`} />
            <span className="text-xs text-[#475569]">{connected ? 'Live' : 'Connecting…'}</span>
          </div>

          {/* Participant avatars */}
          <div className="flex -space-x-2">
            {participants.slice(0, 5).map((p) => (
              <div
                key={p.userId}
                title={p.displayName}
                style={{ background: p.color }}
                className="w-6 h-6 rounded-full border-2 border-[#111113] flex items-center justify-center text-[9px] font-bold text-white"
              >
                {p.displayName[0]?.toUpperCase()}
              </div>
            ))}
            {participants.length > 5 && (
              <div className="w-6 h-6 rounded-full border-2 border-[#111113] bg-[#27272A] flex items-center justify-center text-[9px] text-[#94A3B8]">
                +{participants.length - 5}
              </div>
            )}
          </div>

          {/* Language selector */}
          <select
            data-testid="language-selector"
            value={language}
            onChange={(e) => handleLanguageChange(e.target.value)}
            className="bg-[#18181C] border border-[#27272A] text-[#94A3B8] text-xs rounded-md px-2 py-1 focus:outline-none focus:border-[#6366F1]/50"
            aria-label="language"
          >
            {Object.entries(LANGUAGE_MAP).map(([val, label]) => (
              <option key={val} value={val}>{label}</option>
            ))}
          </select>

          {/* Chat toggle */}
          <button
            onClick={() => setShowChat((v) => !v)}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all ${
              showChat
                ? 'bg-[#6366F1]/20 text-[#6366F1] border border-[#6366F1]/30'
                : 'text-[#475569] hover:text-[#94A3B8] hover:bg-[#18181C]'
            }`}
          >
            💬 Chat
            {chatMessages.length > 0 && (
              <span className="w-4 h-4 rounded-full bg-[#6366F1] text-white text-[9px] flex items-center justify-center">
                {chatMessages.length > 9 ? '9+' : chatMessages.length}
              </span>
            )}
          </button>

          <button
            onClick={handleLeave}
            data-testid="leave-button"
            className="px-2.5 py-1.5 rounded-lg text-xs font-medium text-[#475569] hover:text-red-400 hover:bg-red-400/10 transition-all"
          >
            Leave
          </button>
        </div>
      </header>

      {/* Editor + Chat */}
      <div className="flex flex-1 overflow-hidden">
        {/* Editor */}
        <div className="flex-1 overflow-hidden">
          {!connected && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-[#0A0A0B]/80 backdrop-blur-sm">
              <div className="flex flex-col items-center gap-3">
                <svg className="w-8 h-8 animate-spin text-[#6366F1]" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
                </svg>
                <p className="text-[#94A3B8] text-sm">Connecting to room…</p>
              </div>
            </div>
          )}
          <MonacoEditor
            height="100%"
            language={LANGUAGE_MAP[language] ?? 'javascript'}
            value={content}
            onChange={handleEditorChange}
            theme="vs-dark"
            options={{
              fontSize: 14,
              fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
              fontLigatures: true,
              minimap: { enabled: false },
              padding: { top: 16 },
              scrollBeyondLastLine: false,
              wordWrap: 'on',
              automaticLayout: true,
              tabSize: 2,
              lineNumbers: 'on',
              glyphMargin: false,
              renderLineHighlight: 'line',
            }}
          />
        </div>

        {/* Chat panel */}
        <AnimatePresence>
          {showChat && (
            <motion.div
              initial={{ width: 0, opacity: 0 }}
              animate={{ width: 280, opacity: 1 }}
              exit={{ width: 0, opacity: 0 }}
              transition={{ duration: 0.2 }}
              className="shrink-0 overflow-hidden"
            >
              <ChatPanel messages={chatMessages} onSend={sendChat} color={myColor} />
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  )
}
