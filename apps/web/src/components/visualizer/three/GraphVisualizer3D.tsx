import React, { useRef, useState, useCallback, useMemo, useEffect } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Text, Line } from '@react-three/drei'
import * as THREE from 'three'
import { motion, AnimatePresence } from 'framer-motion'

// ── Graph definition ──────────────────────────────────────────────────────────

interface GraphNode { id: number; label: string; position: [number, number, number] }
interface GraphEdge { from: number; to: number }

const NODES: GraphNode[] = [
  { id: 0, label: '0', position: [0, 3, 0] },
  { id: 1, label: '1', position: [-3, 1, 1] },
  { id: 2, label: '2', position: [3, 1, 1] },
  { id: 3, label: '3', position: [-4.5, -1.5, -1] },
  { id: 4, label: '4', position: [-1.5, -1.5, 2] },
  { id: 5, label: '5', position: [1.5, -1.5, -2] },
  { id: 6, label: '6', position: [4.5, -1.5, 0] },
  { id: 7, label: '7', position: [-1.5, -4, 0] },
  { id: 8, label: '8', position: [2, -4, 1] },
]

const EDGES: GraphEdge[] = [
  { from: 0, to: 1 }, { from: 0, to: 2 },
  { from: 1, to: 3 }, { from: 1, to: 4 },
  { from: 2, to: 5 }, { from: 2, to: 6 },
  { from: 3, to: 7 }, { from: 4, to: 7 }, { from: 4, to: 8 },
  { from: 5, to: 8 }, { from: 6, to: 8 },
  { from: 3, to: 4 }, { from: 5, to: 6 },
]

// Build adjacency list
const adjacency: Map<number, number[]> = new Map()
for (const n of NODES) adjacency.set(n.id, [])
for (const e of EDGES) {
  adjacency.get(e.from)!.push(e.to)
  adjacency.get(e.to)!.push(e.from)
}

// ── Traversal step generators ─────────────────────────────────────────────────

type NodeStatus = 'unvisited' | 'frontier' | 'visiting' | 'visited'

interface TraversalStep {
  statusMap: Map<number, NodeStatus>
  activeEdge: [number, number] | null
  queue: number[]   // for BFS
  stack: number[]   // for DFS
  description: string
}

function generateBFSSteps(start: number): TraversalStep[] {
  const steps: TraversalStep[] = []
  const visited = new Set<number>()
  const queue: number[] = [start]
  const statusMap: Map<number, NodeStatus> = new Map(NODES.map((n) => [n.id, 'unvisited']))

  statusMap.set(start, 'frontier')
  steps.push({
    statusMap: new Map(statusMap),
    activeEdge: null,
    queue: [...queue],
    stack: [],
    description: `Start BFS from node ${start}. Enqueue ${start}.`,
  })

  while (queue.length > 0) {
    const cur = queue.shift()!
    if (visited.has(cur)) continue
    visited.add(cur)
    statusMap.set(cur, 'visiting')
    steps.push({
      statusMap: new Map(statusMap),
      activeEdge: null,
      queue: [...queue],
      stack: [],
      description: `Dequeue ${cur}. Processing neighbors…`,
    })

    const neighbors = adjacency.get(cur) ?? []
    for (const nb of neighbors) {
      if (!visited.has(nb) && statusMap.get(nb) !== 'frontier') {
        statusMap.set(nb, 'frontier')
        queue.push(nb)
        steps.push({
          statusMap: new Map(statusMap),
          activeEdge: [cur, nb],
          queue: [...queue],
          stack: [],
          description: `Node ${cur} → neighbor ${nb}. Enqueue ${nb}.`,
        })
      }
    }
    statusMap.set(cur, 'visited')
    steps.push({
      statusMap: new Map(statusMap),
      activeEdge: null,
      queue: [...queue],
      stack: [],
      description: `Node ${cur} fully explored.`,
    })
  }

  return steps
}

function generateDFSSteps(start: number): TraversalStep[] {
  const steps: TraversalStep[] = []
  const visited = new Set<number>()
  const statusMap: Map<number, NodeStatus> = new Map(NODES.map((n) => [n.id, 'unvisited']))
  const stackTrack: number[] = []

  function dfs(node: number, parent: number | null) {
    visited.add(node)
    statusMap.set(node, 'visiting')
    stackTrack.push(node)
    steps.push({
      statusMap: new Map(statusMap),
      activeEdge: parent !== null ? [parent, node] : null,
      queue: [],
      stack: [...stackTrack],
      description: parent !== null
        ? `DFS: visit ${node} from ${parent}`
        : `DFS: start at ${node}`,
    })

    for (const nb of adjacency.get(node) ?? []) {
      if (!visited.has(nb)) {
        dfs(nb, node)
      }
    }
    statusMap.set(node, 'visited')
    stackTrack.pop()
    steps.push({
      statusMap: new Map(statusMap),
      activeEdge: null,
      queue: [],
      stack: [...stackTrack],
      description: `Backtrack from ${node}`,
    })
  }

  dfs(start, null)
  return steps
}

// ── Colors ────────────────────────────────────────────────────────────────────

const STATUS_COLORS: Record<NodeStatus, THREE.Color> = {
  unvisited: new THREE.Color(0x334155),
  frontier:  new THREE.Color(0xFBBF24),
  visiting:  new THREE.Color(0xF43F5E),
  visited:   new THREE.Color(0x10B981),
}

// ── Graph Node 3D ─────────────────────────────────────────────────────────────

interface GraphNode3DProps {
  node: GraphNode
  status: NodeStatus
  isActive: boolean
}

const GraphNode3D: React.FC<GraphNode3DProps> = ({ node, status, isActive }) => {
  const meshRef = useRef<THREE.Mesh>(null!)
  const matRef = useRef<THREE.MeshStandardMaterial>(null!)
  const currentColor = useRef(new THREE.Color(STATUS_COLORS.unvisited))

  const targetColor = STATUS_COLORS[status]

  useFrame(() => {
    if (!meshRef.current || !matRef.current) return
    currentColor.current.lerp(targetColor, 0.12)
    matRef.current.color.set(currentColor.current)
    matRef.current.emissive.set(currentColor.current)

    const targetEmissive = isActive ? 0.6 : status === 'unvisited' ? 0.03 : 0.25
    matRef.current.emissiveIntensity += (targetEmissive - matRef.current.emissiveIntensity) * 0.12

    if (isActive) {
      const s = 1 + Math.sin(Date.now() * 0.005) * 0.06
      meshRef.current.scale.setScalar(s)
    } else {
      meshRef.current.scale.lerp(new THREE.Vector3(1, 1, 1), 0.12)
    }
  })

  return (
    <group position={node.position}>
      <mesh ref={meshRef}>
        <sphereGeometry args={[0.42, 32, 32]} />
        <meshStandardMaterial
          ref={matRef}
          color={STATUS_COLORS.unvisited}
          emissive={STATUS_COLORS.unvisited}
          emissiveIntensity={0.03}
          metalness={0.3}
          roughness={0.4}
        />
      </mesh>
      <Text
        position={[0, 0, 0.44]}
        fontSize={0.3}
        color="white"
        anchorX="center"
        anchorY="middle"
      >
        {node.label}
      </Text>
    </group>
  )
}

// ── Graph Edge 3D ─────────────────────────────────────────────────────────────

interface GraphEdge3DProps {
  edge: GraphEdge
  isActive: boolean
}

const GraphEdge3D: React.FC<GraphEdge3DProps> = ({ edge, isActive }) => {
  const from = NODES[edge.from].position
  const to = NODES[edge.to].position

  return (
    <Line
      points={[from, to]}
      color={isActive ? '#fbbf24' : '#1e293b'}
      lineWidth={isActive ? 2.5 : 1}
    />
  )
}

// ── Graph Scene ───────────────────────────────────────────────────────────────

interface GraphSceneProps {
  step: TraversalStep
}

const GraphScene: React.FC<GraphSceneProps> = ({ step }) => {
  const activeNodes = new Set(step.queue.concat(step.stack))
  const activeEdge = step.activeEdge

  return (
    <>
      <ambientLight intensity={0.4} />
      <directionalLight position={[10, 14, 6]} intensity={1.1} />
      <pointLight position={[-8, 8, -4]} intensity={0.5} color="#6366f1" />
      <pointLight position={[8, 2, 8]} intensity={0.4} color="#22d3ee" />

      {EDGES.map((edge, i) => {
        const isActive =
          activeEdge !== null &&
          ((activeEdge[0] === edge.from && activeEdge[1] === edge.to) ||
            (activeEdge[0] === edge.to && activeEdge[1] === edge.from))
        return <GraphEdge3D key={i} edge={edge} isActive={isActive} />
      })}

      {NODES.map((node) => (
        <GraphNode3D
          key={node.id}
          node={node}
          status={step.statusMap.get(node.id) ?? 'unvisited'}
          isActive={activeNodes.has(node.id)}
        />
      ))}
    </>
  )
}

// ── Main Component ────────────────────────────────────────────────────────────

type TraversalAlgo = 'bfs' | 'dfs'
const START_NODE = 0

const GraphVisualizer3D: React.FC = () => {
  const [algo, setAlgo] = useState<TraversalAlgo>('bfs')
  const [speed, setSpeed] = useState(2)
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentStep, setCurrentStep] = useState(0)

  const steps = useMemo(
    () => (algo === 'bfs' ? generateBFSSteps(START_NODE) : generateDFSSteps(START_NODE)),
    [algo],
  )

  const step = steps[currentStep] ?? steps[steps.length - 1]
  const isFinished = currentStep >= steps.length - 1

  useEffect(() => {
    setCurrentStep(0)
    setIsPlaying(false)
  }, [algo])

  useEffect(() => {
    if (!isPlaying || isFinished) {
      if (isFinished) setIsPlaying(false)
      return
    }
    const interval = setInterval(() => {
      setCurrentStep((s) => Math.min(s + 1, steps.length - 1))
    }, 1000 / speed)
    return () => clearInterval(interval)
  }, [isPlaying, isFinished, speed, steps.length])

  const reset = useCallback(() => {
    setCurrentStep(0)
    setIsPlaying(false)
  }, [])

  return (
    <div className="flex flex-col h-full gap-3">
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 px-4 py-3 bg-[#111113] border border-[#1e1e2e] rounded-xl">
        {/* Algo selector */}
        <div className="flex items-center gap-1">
          {(['bfs', 'dfs'] as TraversalAlgo[]).map((a) => (
            <button
              key={a}
              onClick={() => setAlgo(a)}
              className={`px-4 py-1.5 rounded-lg text-xs font-bold uppercase tracking-wider transition-all ${
                algo === a
                  ? 'bg-[#6366f1] text-white shadow-lg shadow-[#6366f1]/30'
                  : 'text-[#6b7280] hover:text-white hover:bg-white/5 border border-white/10'
              }`}
            >
              {a === 'bfs' ? 'BFS — Queue' : 'DFS — Stack'}
            </button>
          ))}
        </div>

        <div className="h-6 w-px bg-white/10" />

        {/* Speed */}
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#6b7280] uppercase tracking-wider font-semibold">Speed</span>
          <input
            type="range" min={0.5} max={6} step={0.5} value={speed}
            onChange={(e) => setSpeed(Number(e.target.value))}
            className="w-20 accent-[#6366f1]"
          />
          <span className="text-xs font-mono text-[#94a3b8]">{speed}×</span>
        </div>

        {/* Playback */}
        <div className="flex items-center gap-2 ml-auto">
          <button
            onClick={reset}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold text-[#94a3b8] hover:text-white border border-white/10 hover:bg-white/5 transition-colors"
          >
            ↺ Reset
          </button>
          <button
            onClick={() => setCurrentStep(Math.max(0, currentStep - 1))}
            className="w-8 h-8 rounded-lg border border-white/10 text-[#94a3b8] hover:text-white hover:bg-white/5 flex items-center justify-center transition-colors"
          >
            ‹
          </button>
          <button
            onClick={() => setIsPlaying((v) => !v)}
            className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${
              isPlaying
                ? 'bg-[#f43f5e]/20 text-[#f43f5e] border border-[#f43f5e]/30 hover:bg-[#f43f5e]/30'
                : 'bg-[#6366f1] text-white shadow-lg shadow-[#6366f1]/30 hover:bg-[#5558e8]'
            }`}
          >
            {isPlaying ? '⏸ Pause' : isFinished ? '↺ Replay' : '▶ Play'}
          </button>
          <button
            onClick={() => setCurrentStep(Math.min(steps.length - 1, currentStep + 1))}
            className="w-8 h-8 rounded-lg border border-white/10 text-[#94a3b8] hover:text-white hover:bg-white/5 flex items-center justify-center transition-colors"
          >
            ›
          </button>
        </div>
      </div>

      {/* Step info */}
      <div className="flex items-center justify-between px-4 gap-4">
        <AnimatePresence mode="wait">
          <motion.div
            key={currentStep}
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            transition={{ duration: 0.18 }}
            className="flex items-center gap-3"
          >
            <span className="text-xs text-[#6b7280] font-mono">
              Step <span className="text-[#a5b4fc]">{currentStep + 1}</span> / {steps.length}
            </span>
            <span className="text-xs text-[#94a3b8] font-mono">{step.description}</span>
          </motion.div>
        </AnimatePresence>

        {/* Queue / Stack display */}
        <div className="flex items-center gap-2 font-mono text-[10px]">
          {algo === 'bfs' ? (
            <>
              <span className="text-[#6b7280]">Queue:</span>
              <div className="flex items-center gap-1">
                {step.queue.length === 0 ? (
                  <span className="text-[#334155]">[]</span>
                ) : (
                  step.queue.map((id) => (
                    <span key={id} className="px-1.5 py-0.5 rounded bg-[#fbbf24]/20 text-[#fbbf24] border border-[#fbbf24]/30">
                      {id}
                    </span>
                  ))
                )}
              </div>
            </>
          ) : (
            <>
              <span className="text-[#6b7280]">Stack:</span>
              <div className="flex items-center gap-1">
                {step.stack.length === 0 ? (
                  <span className="text-[#334155]">[]</span>
                ) : (
                  step.stack.map((id, i) => (
                    <span
                      key={i}
                      className={`px-1.5 py-0.5 rounded border ${
                        i === step.stack.length - 1
                          ? 'bg-[#f43f5e]/20 text-[#f43f5e] border-[#f43f5e]/30'
                          : 'bg-white/5 text-[#94a3b8] border-white/10'
                      }`}
                    >
                      {id}
                    </span>
                  ))
                )}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Progress bar */}
      <div className="h-1 bg-[#1e1e2e] rounded-full mx-4 overflow-hidden">
        <motion.div
          className="h-full bg-gradient-to-r from-[#6366f1] to-[#22d3ee] rounded-full"
          style={{ width: `${((currentStep + 1) / steps.length) * 100}%` }}
          transition={{ duration: 0.1 }}
        />
      </div>

      {/* Canvas */}
      <div className="flex-1 rounded-xl overflow-hidden border border-[#1e1e2e]">
        <Canvas
          camera={{ position: [0, 2, 14], fov: 55 }}
          gl={{ antialias: true }}
          style={{ background: 'linear-gradient(to bottom, #060608, #0d0d14)' }}
        >
          <GraphScene step={step} />
          <OrbitControls enableDamping dampingFactor={0.06} minDistance={6} maxDistance={30} />
        </Canvas>
      </div>

      {/* Legend */}
      <div className="flex items-center gap-4 px-4 pb-1">
        {(Object.entries(STATUS_COLORS) as [NodeStatus, THREE.Color][]).map(([status, color]) => (
          <div key={status} className="flex items-center gap-1.5">
            <div
              className="w-2.5 h-2.5 rounded-full"
              style={{ backgroundColor: `#${color.getHexString()}` }}
            />
            <span className="text-[10px] text-[#6b7280] capitalize">{status}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default GraphVisualizer3D
