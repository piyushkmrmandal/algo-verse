import React, { useRef, useState, useCallback, useMemo } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Text, Line } from '@react-three/drei'
import * as THREE from 'three'
import { motion } from 'framer-motion'

// ── BST Data Structure ────────────────────────────────────────────────────────

interface BSTNode {
  id: number
  value: number
  left: BSTNode | null
  right: BSTNode | null
}

let nodeIdCounter = 0
function createNode(value: number): BSTNode {
  return { id: nodeIdCounter++, value, left: null, right: null }
}

function insertBST(root: BSTNode | null, value: number): { root: BSTNode; path: number[] } {
  const path: number[] = []
  if (!root) {
    const n = createNode(value)
    path.push(n.id)
    return { root: n, path }
  }
  // Deep clone to keep immutability
  function clone(n: BSTNode | null): BSTNode | null {
    if (!n) return null
    return { ...n, left: clone(n.left), right: clone(n.right) }
  }
  const newRoot = clone(root)!

  let cur: BSTNode = newRoot
  path.push(cur.id)
  while (true) {
    if (value < cur.value) {
      if (!cur.left) {
        cur.left = createNode(value)
        path.push(cur.left.id)
        break
      }
      cur = cur.left
      path.push(cur.id)
    } else if (value > cur.value) {
      if (!cur.right) {
        cur.right = createNode(value)
        path.push(cur.right.id)
        break
      }
      cur = cur.right
      path.push(cur.id)
    } else {
      // Duplicate — do nothing
      break
    }
  }
  return { root: newRoot, path }
}

function searchBST(root: BSTNode | null, value: number): number[] {
  const path: number[] = []
  let cur = root
  while (cur) {
    path.push(cur.id)
    if (value === cur.value) break
    cur = value < cur.value ? cur.left : cur.right
  }
  return path
}

// ── Tree Layout ───────────────────────────────────────────────────────────────

interface LayoutNode {
  id: number
  value: number
  x: number
  y: number
  left: LayoutNode | null
  right: LayoutNode | null
}

const H_SPACING = 1.8
const V_SPACING = 2.0

function layoutTree(node: BSTNode | null, depth = 0, counter = { v: 0 }): LayoutNode | null {
  if (!node) return null
  const laid: LayoutNode = { id: node.id, value: node.value, x: 0, y: -depth * V_SPACING, left: null, right: null }
  laid.left = layoutTree(node.left, depth + 1, counter)
  laid.x = counter.v * H_SPACING
  counter.v++
  laid.right = layoutTree(node.right, depth + 1, counter)
  return laid
}

function collectLayoutNodes(node: LayoutNode | null): LayoutNode[] {
  if (!node) return []
  return [node, ...collectLayoutNodes(node.left), ...collectLayoutNodes(node.right)]
}

interface EdgeDef { x1: number; y1: number; x2: number; y2: number }
function collectEdges(node: LayoutNode | null): EdgeDef[] {
  if (!node) return []
  const edges: EdgeDef[] = []
  if (node.left) {
    edges.push({ x1: node.x, y1: node.y, x2: node.left.x, y2: node.left.y })
    edges.push(...collectEdges(node.left))
  }
  if (node.right) {
    edges.push({ x1: node.x, y1: node.y, x2: node.right.x, y2: node.right.y })
    edges.push(...collectEdges(node.right))
  }
  return edges
}

// ── BSTNode3D (inside Canvas) ─────────────────────────────────────────────────

const NODE_R = 0.38

interface BSTNode3DProps {
  node: LayoutNode
  highlighted: boolean
  isFound: boolean
  centerX: number
}

const BSTNode3D: React.FC<BSTNode3DProps> = ({ node, highlighted, isFound, centerX }) => {
  const meshRef = useRef<THREE.Mesh>(null!)
  const matRef = useRef<THREE.MeshStandardMaterial>(null!)
  const currentScale = useRef(0.01)
  const targetColor = isFound
    ? new THREE.Color(0x10B981)
    : highlighted
    ? new THREE.Color(0xFBBF24)
    : new THREE.Color(0x3B82F6)

  useFrame(() => {
    if (!meshRef.current || !matRef.current) return
    currentScale.current += (1 - currentScale.current) * 0.12
    meshRef.current.scale.setScalar(currentScale.current)
    matRef.current.emissive.lerp(targetColor, 0.15)
    matRef.current.color.lerp(targetColor, 0.1)
    const targetEmissive = isFound ? 0.5 : highlighted ? 0.4 : 0.08
    matRef.current.emissiveIntensity += (targetEmissive - matRef.current.emissiveIntensity) * 0.12
  })

  const x = node.x - centerX
  const y = node.y

  return (
    <group position={[x, y, 0]}>
      <mesh ref={meshRef} scale={0.01}>
        <sphereGeometry args={[NODE_R, 32, 32]} />
        <meshStandardMaterial
          ref={matRef}
          color={new THREE.Color(0x3B82F6)}
          emissive={new THREE.Color(0x1d4ed8)}
          emissiveIntensity={0.08}
          metalness={0.3}
          roughness={0.4}
        />
      </mesh>
      <Text
        position={[0, 0, NODE_R + 0.01]}
        fontSize={0.28}
        color="white"
        anchorX="center"
        anchorY="middle"
        font={undefined}
      >
        {node.value}
      </Text>
    </group>
  )
}

// ── BST Scene ─────────────────────────────────────────────────────────────────

interface BSTSceneProps {
  layoutRoot: LayoutNode | null
  highlightedIds: Set<number>
  foundId: number | null
}

const BSTScene: React.FC<BSTSceneProps> = ({ layoutRoot, highlightedIds, foundId }) => {
  const nodes = useMemo(() => collectLayoutNodes(layoutRoot), [layoutRoot])
  const edges = useMemo(() => collectEdges(layoutRoot), [layoutRoot])
  const centerX = nodes.length > 0 ? (Math.max(...nodes.map((n) => n.x)) + Math.min(...nodes.map((n) => n.x))) / 2 : 0

  return (
    <>
      <ambientLight intensity={0.4} />
      <directionalLight position={[8, 12, 6]} intensity={1.1} />
      <pointLight position={[-6, 8, -4]} intensity={0.5} color="#6366f1" />
      <pointLight position={[6, 4, 6]} intensity={0.4} color="#22d3ee" />

      {/* Edges */}
      {edges.map((edge, i) => (
        <Line
          key={i}
          points={[
            [edge.x1 - centerX, edge.y1, 0],
            [edge.x2 - centerX, edge.y2, 0],
          ]}
          color="#334155"
          lineWidth={1.5}
        />
      ))}

      {/* Nodes */}
      {nodes.map((n) => (
        <BSTNode3D
          key={n.id}
          node={n}
          highlighted={highlightedIds.has(n.id)}
          isFound={foundId === n.id}
          centerX={centerX}
        />
      ))}

      {/* Empty state */}
      {nodes.length === 0 && (
        <Text position={[0, 0, 0]} fontSize={0.45} color="#334155" anchorX="center" anchorY="middle">
          Insert values to build the tree
        </Text>
      )}
    </>
  )
}

// ── Traversal step animator ───────────────────────────────────────────────────

const DEFAULT_SEED = [50, 30, 70, 20, 40, 60, 80, 10, 35]

// ── Main Component ────────────────────────────────────────────────────────────

const BSTVisualizer3D: React.FC = () => {
  const [bst, setBst] = useState<BSTNode | null>(() => {
    nodeIdCounter = 0
    let root: BSTNode | null = null
    for (const v of DEFAULT_SEED) {
      const r = insertBST(root, v)
      root = r.root
    }
    return root
  })

  const [highlightedIds, setHighlightedIds] = useState<Set<number>>(new Set())
  const [foundId, setFoundId] = useState<number | null>(null)
  const [inputVal, setInputVal] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [operation, setOperation] = useState<'insert' | 'search'>('insert')
  const animTimerRef = useRef<NodeJS.Timeout | null>(null)

  const layoutRoot = useMemo(() => layoutTree(bst), [bst])

  const animatePath = useCallback((path: number[], onDone?: () => void) => {
    if (animTimerRef.current) clearTimeout(animTimerRef.current)
    setHighlightedIds(new Set())
    setFoundId(null)
    let i = 0
    const step = () => {
      if (i >= path.length) {
        if (onDone) onDone()
        animTimerRef.current = setTimeout(() => {
          setHighlightedIds(new Set())
          setFoundId(null)
        }, 1000)
        return
      }
      setHighlightedIds(new Set(path.slice(0, i + 1)))
      i++
      animTimerRef.current = setTimeout(step, 400)
    }
    step()
  }, [])

  const handleInsert = useCallback(() => {
    const val = parseInt(inputVal.trim(), 10)
    if (isNaN(val) || val < 1 || val > 999) {
      setMessage('Enter a number between 1 and 999')
      return
    }
    const { root: newRoot, path } = insertBST(bst, val)
    setBst(newRoot)
    setInputVal('')
    setMessage(`Inserting ${val}…`)
    animatePath(path, () => {
      setFoundId(path[path.length - 1])
      setMessage(`${val} inserted`)
    })
  }, [inputVal, bst, animatePath])

  const handleSearch = useCallback(() => {
    const val = parseInt(inputVal.trim(), 10)
    if (isNaN(val)) {
      setMessage('Enter a number to search')
      return
    }
    const path = searchBST(bst, val)
    setMessage(`Searching for ${val}…`)
    if (path.length === 0) {
      setMessage(`${val} not found — tree is empty`)
      return
    }
    const lastNode = collectLayoutNodes(layoutRoot).find(
      (n) => n.id === path[path.length - 1],
    )
    const found = lastNode?.value === val
    animatePath(path, () => {
      if (found) {
        setFoundId(path[path.length - 1])
        setMessage(`✓ Found ${val}!`)
      } else {
        setMessage(`✗ ${val} not in tree`)
      }
    })
  }, [inputVal, bst, layoutRoot, animatePath])

  const handleClear = useCallback(() => {
    nodeIdCounter = 0
    setBst(null)
    setHighlightedIds(new Set())
    setFoundId(null)
    setMessage('Tree cleared')
  }, [])

  const handleReset = useCallback(() => {
    nodeIdCounter = 0
    let root: BSTNode | null = null
    for (const v of DEFAULT_SEED) {
      const r = insertBST(root, v)
      root = r.root
    }
    setBst(root)
    setHighlightedIds(new Set())
    setFoundId(null)
    setMessage('Tree reset to default')
  }, [])

  const nodeCount = useMemo(() => collectLayoutNodes(layoutRoot).length, [layoutRoot])

  return (
    <div className="flex flex-col h-full gap-3">
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 px-4 py-3 bg-[#111113] border border-[#1e1e2e] rounded-xl">
        {/* Operation toggle */}
        <div className="flex items-center gap-1">
          {(['insert', 'search'] as const).map((op) => (
            <button
              key={op}
              onClick={() => setOperation(op)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold capitalize transition-all ${
                operation === op
                  ? 'bg-[#6366f1] text-white shadow-lg shadow-[#6366f1]/30'
                  : 'text-[#6b7280] hover:text-white hover:bg-white/5 border border-white/10'
              }`}
            >
              {op}
            </button>
          ))}
        </div>

        {/* Input */}
        <div className="flex items-center gap-2">
          <input
            type="number"
            value={inputVal}
            onChange={(e) => setInputVal(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') operation === 'insert' ? handleInsert() : handleSearch()
            }}
            placeholder="Value (1–999)"
            className="w-28 px-2.5 py-1.5 bg-[#0d0d14] border border-white/10 rounded-lg text-xs text-white placeholder-[#4b5563] focus:outline-none focus:border-[#6366f1]/60"
          />
          <button
            onClick={operation === 'insert' ? handleInsert : handleSearch}
            className="px-4 py-1.5 rounded-lg text-xs font-bold bg-[#6366f1] text-white hover:bg-[#5558e8] transition-colors shadow-lg shadow-[#6366f1]/25"
          >
            {operation === 'insert' ? '+ Insert' : '⌕ Search'}
          </button>
        </div>

        <div className="h-6 w-px bg-white/10" />

        <button
          onClick={handleReset}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold text-[#94a3b8] hover:text-white border border-white/10 hover:bg-white/5 transition-colors"
        >
          ↺ Reset
        </button>
        <button
          onClick={handleClear}
          className="px-3 py-1.5 rounded-lg text-xs font-semibold text-[#94a3b8] hover:text-[#f43f5e] border border-white/10 hover:border-[#f43f5e]/30 hover:bg-[#f43f5e]/5 transition-colors"
        >
          ✕ Clear
        </button>

        {/* Stats */}
        <div className="ml-auto flex items-center gap-3 text-[10px] font-mono text-[#6b7280]">
          <span>Nodes: <span className="text-[#a5b4fc]">{nodeCount}</span></span>
          <span>Lookup: <span className="text-[#94a3b8]">O(log n) avg</span></span>
          <span>Insert: <span className="text-[#94a3b8]">O(log n) avg</span></span>
        </div>
      </div>

      {/* Message */}
      <div className="px-4 h-5 flex items-center">
        {message && (
          <motion.span
            key={message}
            initial={{ opacity: 0, x: -6 }}
            animate={{ opacity: 1, x: 0 }}
            className="text-xs text-[#a5b4fc] font-mono"
          >
            {message}
          </motion.span>
        )}
      </div>

      {/* Legend */}
      <div className="flex items-center gap-4 px-4">
        {[
          { color: '#3b82f6', label: 'Node' },
          { color: '#fbbf24', label: 'Traversing' },
          { color: '#10b981', label: 'Found / Inserted' },
        ].map(({ color, label }) => (
          <div key={label} className="flex items-center gap-1.5">
            <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: color }} />
            <span className="text-[10px] text-[#6b7280]">{label}</span>
          </div>
        ))}
      </div>

      {/* Canvas */}
      <div className="flex-1 rounded-xl overflow-hidden border border-[#1e1e2e]">
        <Canvas
          camera={{ position: [0, -2, 14], fov: 55 }}
          gl={{ antialias: true }}
          style={{ background: 'linear-gradient(to bottom, #060608, #0d0d14)' }}
        >
          <BSTScene layoutRoot={layoutRoot} highlightedIds={highlightedIds} foundId={foundId} />
          <OrbitControls enableDamping dampingFactor={0.06} minDistance={5} maxDistance={40} />
        </Canvas>
      </div>
    </div>
  )
}

export default BSTVisualizer3D
