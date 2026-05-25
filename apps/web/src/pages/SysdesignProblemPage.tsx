import { useState, useCallback, useRef } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { motion, AnimatePresence } from 'framer-motion'
import ReactMarkdown from 'react-markdown'
import { api } from '../lib/api'
import { useAuthStore } from '../stores/auth-store'
import AppNav from '../components/ui/AppNav'

// ── Types ─────────────────────────────────────────────────────────────────────

interface SysdesignProblem {
  id: string
  slug: string
  title: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  category: string
  descriptionMd: string
  requirements: string[]
}

interface DiagramNode {
  id: string
  type: 'service' | 'database' | 'cache' | 'queue' | 'cdn' | 'client' | 'loadbalancer' | 'custom'
  label: string
  x: number
  y: number
  color?: string
}

interface DiagramEdge {
  id: string
  from: string
  to: string
  label?: string
  protocol?: string
}

interface DiagramFeedback {
  feedbackId: string
  diagramId: string
  overallScore: number
  summary: string
  strengths: string[]
  improvements: string[]
  suggestions: Array<{ title: string; description: string }>
  generatedAt: string
}

// ── Node palette ──────────────────────────────────────────────────────────────

const NODE_TYPES: Array<{ type: DiagramNode['type']; label: string; icon: string; color: string }> = [
  { type: 'client', label: 'Client', icon: '💻', color: '#6366F1' },
  { type: 'loadbalancer', label: 'Load Balancer', icon: '⚖️', color: '#8B5CF6' },
  { type: 'service', label: 'Service', icon: '⚡', color: '#3B82F6' },
  { type: 'database', label: 'Database', icon: '🗄️', color: '#10B981' },
  { type: 'cache', label: 'Cache', icon: '🔴', color: '#EF4444' },
  { type: 'queue', label: 'Queue', icon: '📨', color: '#F59E0B' },
  { type: 'cdn', label: 'CDN', icon: '🌐', color: '#06B6D4' },
  { type: 'custom', label: 'Custom', icon: '🔧', color: '#94A3B8' },
]

const NODE_COLORS: Record<DiagramNode['type'], string> = {
  client: '#6366F1', loadbalancer: '#8B5CF6', service: '#3B82F6',
  database: '#10B981', cache: '#EF4444', queue: '#F59E0B',
  cdn: '#06B6D4', custom: '#94A3B8',
}

const NODE_ICONS: Record<DiagramNode['type'], string> = {
  client: '💻', loadbalancer: '⚖️', service: '⚡', database: '🗄️',
  cache: '🔴', queue: '📨', cdn: '🌐', custom: '🔧',
}

// ── DiagramCanvas ─────────────────────────────────────────────────────────────

interface DiagramCanvasProps {
  nodes: DiagramNode[]
  edges: DiagramEdge[]
  selectedNode: string | null
  connectingFrom: string | null
  onNodeMove: (id: string, x: number, y: number) => void
  onNodeSelect: (id: string | null) => void
  onEdgeCreate: (from: string, to: string) => void
  onStartConnect: (id: string) => void
  onCanvasClick: () => void
}

function DiagramCanvas({
  nodes, edges, selectedNode, connectingFrom,
  onNodeMove, onNodeSelect, onEdgeCreate, onStartConnect, onCanvasClick,
}: DiagramCanvasProps) {
  const svgRef = useRef<SVGSVGElement>(null)
  const dragging = useRef<{ id: string; offsetX: number; offsetY: number } | null>(null)

  const handleNodeMouseDown = (e: React.MouseEvent, node: DiagramNode) => {
    e.stopPropagation()

    if (connectingFrom && connectingFrom !== node.id) {
      onEdgeCreate(connectingFrom, node.id)
      return
    }

    onNodeSelect(node.id)
    const rect = svgRef.current!.getBoundingClientRect()
    dragging.current = {
      id: node.id,
      offsetX: e.clientX - rect.left - node.x,
      offsetY: e.clientY - rect.top - node.y,
    }
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!dragging.current) return
    const rect = svgRef.current!.getBoundingClientRect()
    onNodeMove(
      dragging.current.id,
      e.clientX - rect.left - dragging.current.offsetX,
      e.clientY - rect.top - dragging.current.offsetY,
    )
  }

  const handleMouseUp = () => { dragging.current = null }

  return (
    <svg
      ref={svgRef}
      className="w-full h-full"
      style={{ background: 'radial-gradient(circle at 1px 1px, rgba(255,255,255,0.04) 1px, transparent 0)', backgroundSize: '24px 24px' }}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onClick={onCanvasClick}
    >
      <defs>
        <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
          <path d="M0,0 L0,6 L8,3 z" fill="#475569" />
        </marker>
      </defs>

      {/* Edges */}
      {edges.map((edge) => {
        const from = nodes.find((n) => n.id === edge.from)
        const to = nodes.find((n) => n.id === edge.to)
        if (!from || !to) return null
        const mx = (from.x + to.x) / 2
        const my = (from.y + to.y) / 2
        return (
          <g key={edge.id}>
            <line
              x1={from.x + 56} y1={from.y + 28}
              x2={to.x + 56} y2={to.y + 28}
              stroke="#475569" strokeWidth="1.5"
              markerEnd="url(#arrow)"
              strokeDasharray="4 2"
            />
            {edge.label && (
              <text x={mx + 56} y={my + 24} fill="#64748B" fontSize="10" textAnchor="middle">
                {edge.label}
              </text>
            )}
          </g>
        )
      })}

      {/* Nodes */}
      {nodes.map((node) => {
        const isSelected = selectedNode === node.id
        const isConnecting = connectingFrom === node.id
        const color = node.color ?? NODE_COLORS[node.type]

        return (
          <g
            key={node.id}
            transform={`translate(${node.x}, ${node.y})`}
            onMouseDown={(e) => handleNodeMouseDown(e, node)}
            style={{ cursor: connectingFrom ? 'crosshair' : 'grab' }}
          >
            {/* Node box */}
            <rect
              width="112" height="56"
              rx="10"
              fill={`${color}18`}
              stroke={isSelected || isConnecting ? color : `${color}60`}
              strokeWidth={isSelected || isConnecting ? 2 : 1}
            />
            {/* Icon */}
            <text x="16" y="34" fontSize="18">{NODE_ICONS[node.type]}</text>
            {/* Label */}
            <text x="56" y="24" fill="#E2E8F0" fontSize="11" fontWeight="600" textAnchor="middle">{node.label}</text>
            <text x="56" y="38" fill="#64748B" fontSize="9" textAnchor="middle">{node.type}</text>

            {/* Connect anchor */}
            <circle
              cx="112" cy="28" r="6"
              fill={connectingFrom === node.id ? color : '#1E293B'}
              stroke={color} strokeWidth="1.5"
              style={{ cursor: 'crosshair' }}
              onMouseDown={(e) => { e.stopPropagation(); onStartConnect(node.id) }}
              opacity={isSelected ? 1 : 0.5}
            />
          </g>
        )
      })}
    </svg>
  )
}

// ── FeedbackPanel ─────────────────────────────────────────────────────────────

function FeedbackPanel({ feedback, isLoading }: { feedback: DiagramFeedback | null; isLoading: boolean }) {
  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-center">
        <svg className="w-8 h-8 animate-spin text-[#6366F1]" viewBox="0 0 24 24" fill="none">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2"/>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/>
        </svg>
        <p className="text-[#94A3B8] text-sm">Analyzing your design…</p>
      </div>
    )
  }

  if (!feedback) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-center px-4">
        <span className="text-4xl">🤖</span>
        <p className="text-[#F8F8F2] text-sm font-semibold">AI Feedback</p>
        <p className="text-[#475569] text-xs">Save your diagram and click "Get AI Feedback" to receive analysis</p>
      </div>
    )
  }

  const scoreColor = feedback.overallScore >= 80 ? '#10B981' : feedback.overallScore >= 50 ? '#F59E0B' : '#EF4444'

  return (
    <div className="overflow-y-auto h-full p-4 space-y-4">
      {/* Score */}
      <div className="flex items-center gap-3 p-3 rounded-xl bg-[#18181C]">
        <div className="text-3xl font-bold" style={{ color: scoreColor }}>
          {feedback.overallScore}
        </div>
        <div>
          <p className="text-xs font-semibold text-[#F8F8F2]">Overall Score</p>
          <p className="text-xs text-[#94A3B8]">{feedback.summary}</p>
        </div>
      </div>

      {/* Strengths */}
      {feedback.strengths.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-[#10B981] uppercase tracking-wider mb-2">✅ Strengths</p>
          <ul className="space-y-1">
            {feedback.strengths.map((s, i) => (
              <li key={i} className="text-xs text-[#CBD5E1] flex gap-2">
                <span className="text-[#10B981] shrink-0">•</span> {s}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Improvements */}
      {feedback.improvements.length > 0 && (
        <div>
          <p className="text-xs font-semibold text-[#F59E0B] uppercase tracking-wider mb-2">⚠️ Improvements</p>
          <ul className="space-y-1">
            {feedback.improvements.map((s, i) => (
              <li key={i} className="text-xs text-[#CBD5E1] flex gap-2">
                <span className="text-[#F59E0B] shrink-0">•</span> {s}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Suggestions */}
      {(feedback.suggestions ?? []).length > 0 && (
        <div>
          <p className="text-xs font-semibold text-[#6366F1] uppercase tracking-wider mb-2">💡 Suggestions</p>
          <div className="space-y-2">
            {feedback.suggestions.map((s, i) => (
              <div key={i} className="p-2.5 rounded-lg bg-[#18181C] border border-[#27272A]">
                <p className="text-xs font-semibold text-[#E2E8F0] mb-0.5">{s.title}</p>
                <p className="text-xs text-[#64748B] leading-snug">{s.description}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ── SysdesignProblemPage ──────────────────────────────────────────────────────

export default function SysdesignProblemPage() {
  const { slug } = useParams<{ slug: string }>()
  const user = useAuthStore((s) => s.user)
  const queryClient = useQueryClient()

  // Canvas state
  const [nodes, setNodes] = useState<DiagramNode[]>([])
  const [edges, setEdges] = useState<DiagramEdge[]>([])
  const [selectedNode, setSelectedNode] = useState<string | null>(null)
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null)
  const [leftTab, setLeftTab] = useState<'problem' | 'requirements'>('problem')
  const [rightTab, setRightTab] = useState<'canvas' | 'feedback'>('canvas')
  const [savedDiagramId, setSavedDiagramId] = useState<string | null>(null)

  const { data: problem, isLoading } = useQuery<SysdesignProblem>({
    queryKey: ['sysdesign', 'problem', slug],
    queryFn: () => api.get<SysdesignProblem>(`/sysdesign/problems/${slug}`).then((r) => r.data),
    enabled: !!slug,
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      api.post<{ id: string }>('/sysdesign/diagrams', {
        problemId: problem?.id,
        title: `${problem?.title ?? 'Diagram'} — ${user?.displayName}`,
        nodes,
        edges,
        metadata: {},
      }).then((r) => r.data),
    onSuccess: (data) => {
      setSavedDiagramId(data.id)
    },
  })

  const { data: feedback, isFetching: feedbackLoading } = useQuery<DiagramFeedback>({
    queryKey: ['sysdesign', 'feedback', savedDiagramId],
    queryFn: () =>
      api.post<DiagramFeedback>(`/sysdesign/diagrams/${savedDiagramId}/review`).then((r) => r.data),
    enabled: false, // triggered manually
  })

  const requestFeedback = () => {
    if (!savedDiagramId) {
      saveMutation.mutate(undefined, {
        onSuccess: (data) => {
          queryClient.fetchQuery({
            queryKey: ['sysdesign', 'feedback', data.id],
            queryFn: () =>
              api.post<DiagramFeedback>(`/sysdesign/diagrams/${data.id}/review`).then((r) => r.data),
          })
          setRightTab('feedback')
        },
      })
    } else {
      queryClient.fetchQuery({
        queryKey: ['sysdesign', 'feedback', savedDiagramId],
        queryFn: () =>
          api.post<DiagramFeedback>(`/sysdesign/diagrams/${savedDiagramId}/review`).then((r) => r.data),
      })
      setRightTab('feedback')
    }
  }

  // Canvas operations
  const addNode = useCallback((type: DiagramNode['type']) => {
    const id = `node-${Date.now()}`
    setNodes((prev) => [...prev, {
      id, type, label: type[0].toUpperCase() + type.slice(1),
      x: 80 + Math.random() * 400,
      y: 80 + Math.random() * 300,
    }])
  }, [])

  const moveNode = useCallback((id: string, x: number, y: number) => {
    setNodes((prev) => prev.map((n) => n.id === id ? { ...n, x: Math.max(0, x), y: Math.max(0, y) } : n))
  }, [])

  const createEdge = useCallback((from: string, to: string) => {
    if (from === to) { setConnectingFrom(null); return }
    const id = `edge-${Date.now()}`
    setEdges((prev) => [...prev, { id, from, to, label: '', protocol: 'HTTP' }])
    setConnectingFrom(null)
  }, [])

  const deleteSelected = useCallback(() => {
    if (!selectedNode) return
    setNodes((prev) => prev.filter((n) => n.id !== selectedNode))
    setEdges((prev) => prev.filter((e) => e.from !== selectedNode && e.to !== selectedNode))
    setSelectedNode(null)
  }, [selectedNode])

  const difficultyColors = { EASY: 'badge-easy', MEDIUM: 'badge-medium', HARD: 'badge-hard' }

  return (
    <div className="flex flex-col h-screen bg-[#0A0A0B] overflow-hidden">
      {/* Global nav */}
      <header className="flex items-center justify-between px-4 h-11 bg-[#111113] border-b border-[#18181C] shrink-0 z-20">
        <div className="flex items-center gap-2">
          <Link to="/" className="text-sm font-bold text-[#6366F1]">AlgoVerse</Link>
          <span className="text-[#27272A]">/</span>
          <Link to="/sysdesign" className="text-xs text-[#475569] hover:text-[#94A3B8] transition-colors">System Design</Link>
          {problem && (
            <>
              <span className="text-[#27272A]">/</span>
              <span className="text-xs text-[#94A3B8] truncate max-w-40">{problem.title}</span>
            </>
          )}
        </div>
        <div className="flex items-center gap-2">
          <motion.button
            whileTap={{ scale: 0.97 }}
            onClick={() => saveMutation.mutate()}
            disabled={saveMutation.isPending || nodes.length === 0}
            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-[#18181C] border border-[#27272A] text-[#94A3B8] hover:border-[#6366F1]/50 hover:text-[#6366F1] transition-all disabled:opacity-40"
          >
            {saveMutation.isPending ? 'Saving…' : savedDiagramId ? '✓ Saved' : 'Save'}
          </motion.button>
          <motion.button
            whileTap={{ scale: 0.97 }}
            onClick={requestFeedback}
            disabled={nodes.length === 0}
            className="px-3 py-1.5 rounded-lg text-xs font-medium bg-[#6366F1]/10 border border-[#6366F1]/30 text-[#6366F1] hover:bg-[#6366F1]/20 transition-all disabled:opacity-40"
          >
            🤖 Get AI Feedback
          </motion.button>
        </div>
      </header>

      {/* 2-panel layout */}
      <div className="flex flex-1 overflow-hidden">
        {/* Left: problem description */}
        <div className="w-[340px] shrink-0 flex flex-col bg-[#111113] border-r border-[#18181C]">
          <div className="flex border-b border-[#18181C]">
            {(['problem', 'requirements'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setLeftTab(t)}
                className={`flex-1 px-4 py-2.5 text-xs font-medium capitalize transition-colors border-b-2 -mb-px ${
                  leftTab === t
                    ? 'text-[#6366F1] border-[#6366F1]'
                    : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          <div className="flex-1 overflow-y-auto p-5">
            {isLoading ? (
              <div className="space-y-3">
                {[1, 2, 3, 4].map((i) => <div key={i} className="h-4 bg-[#18181C] rounded animate-pulse" />)}
              </div>
            ) : problem ? (
              leftTab === 'problem' ? (
                <div>
                  <div className="flex items-start justify-between mb-4">
                    <h1 className="text-base font-bold text-[#F8F8F2] leading-snug">{problem.title}</h1>
                    <span className={`ml-2 shrink-0 ${difficultyColors[problem.difficulty]}`}>
                      {problem.difficulty[0] + problem.difficulty.slice(1).toLowerCase()}
                    </span>
                  </div>
                  <div className="prose prose-invert prose-sm max-w-none text-[#CBD5E1]">
                    <ReactMarkdown>{problem.descriptionMd}</ReactMarkdown>
                  </div>
                </div>
              ) : (
                <div>
                  <h2 className="text-sm font-semibold text-[#F8F8F2] mb-3">Requirements</h2>
                  <ul className="space-y-2">
                    {problem.requirements.map((req, i) => (
                      <li key={i} className="flex gap-2 text-xs text-[#CBD5E1]">
                        <span className="text-[#6366F1] font-bold shrink-0">{i + 1}.</span>
                        {req}
                      </li>
                    ))}
                  </ul>
                </div>
              )
            ) : null}
          </div>
        </div>

        {/* Right: canvas + feedback */}
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* Tab bar + node palette */}
          <div className="flex items-center border-b border-[#18181C] bg-[#111113] shrink-0 px-2 gap-2">
            <button
              onClick={() => setRightTab('canvas')}
              className={`px-3 py-2.5 text-xs font-medium border-b-2 -mb-px transition-colors ${
                rightTab === 'canvas' ? 'text-[#6366F1] border-[#6366F1]' : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
              }`}
            >
              Canvas
            </button>
            <button
              onClick={() => setRightTab('feedback')}
              className={`px-3 py-2.5 text-xs font-medium border-b-2 -mb-px transition-colors ${
                rightTab === 'feedback' ? 'text-[#6366F1] border-[#6366F1]' : 'text-[#475569] border-transparent hover:text-[#94A3B8]'
              }`}
            >
              AI Feedback
            </button>

            {rightTab === 'canvas' && (
              <>
                <div className="w-px h-5 bg-[#27272A] mx-1" />
                <div className="flex items-center gap-1 overflow-x-auto">
                  {NODE_TYPES.map((nt) => (
                    <button
                      key={nt.type}
                      onClick={() => addNode(nt.type)}
                      title={`Add ${nt.label}`}
                      className="flex items-center gap-1 px-2 py-1 rounded-md text-xs text-[#94A3B8] hover:text-[#F8F8F2] hover:bg-[#18181C] transition-colors whitespace-nowrap shrink-0"
                    >
                      <span>{nt.icon}</span>
                      <span className="hidden sm:block">{nt.label}</span>
                    </button>
                  ))}
                </div>
                {selectedNode && (
                  <>
                    <div className="w-px h-5 bg-[#27272A] mx-1 ml-auto" />
                    <button
                      onClick={() => setConnectingFrom(connectingFrom ? null : selectedNode)}
                      className={`px-2 py-1 rounded-md text-xs transition-colors ${
                        connectingFrom ? 'bg-[#6366F1]/20 text-[#6366F1]' : 'text-[#94A3B8] hover:text-[#F8F8F2] hover:bg-[#18181C]'
                      }`}
                    >
                      {connectingFrom ? '✓ Connecting…' : 'Connect'}
                    </button>
                    <button
                      onClick={deleteSelected}
                      className="px-2 py-1 rounded-md text-xs text-[#94A3B8] hover:text-red-400 hover:bg-red-400/10 transition-colors"
                    >
                      Delete
                    </button>
                  </>
                )}
              </>
            )}
          </div>

          {/* Canvas / Feedback content */}
          <div className="flex-1 overflow-hidden relative">
            <AnimatePresence mode="wait">
              {rightTab === 'canvas' ? (
                <motion.div
                  key="canvas"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="absolute inset-0"
                >
                  {nodes.length === 0 && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 pointer-events-none text-center">
                      <span className="text-5xl">🏗️</span>
                      <p className="text-[#F8F8F2] text-sm font-semibold">Start designing</p>
                      <p className="text-[#475569] text-xs max-w-xs">Click a component in the toolbar above to add it to the canvas</p>
                    </div>
                  )}
                  <DiagramCanvas
                    nodes={nodes}
                    edges={edges}
                    selectedNode={selectedNode}
                    connectingFrom={connectingFrom}
                    onNodeMove={moveNode}
                    onNodeSelect={setSelectedNode}
                    onEdgeCreate={createEdge}
                    onStartConnect={(id) => setConnectingFrom(connectingFrom === id ? null : id)}
                    onCanvasClick={() => { setSelectedNode(null); setConnectingFrom(null) }}
                  />
                </motion.div>
              ) : (
                <motion.div
                  key="feedback"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="absolute inset-0 bg-[#111113]"
                >
                  <FeedbackPanel feedback={feedback ?? null} isLoading={feedbackLoading} />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </div>
  )
}
