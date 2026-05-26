import React, {
  useRef,
  useState,
  useEffect,
  useCallback,
  useMemo,
} from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'
import * as THREE from 'three'
import { motion, AnimatePresence } from 'framer-motion'

// ── Types ─────────────────────────────────────────────────────────────────────

export type SortAlgorithm = 'bubble' | 'insertion' | 'selection' | 'quick' | 'merge'

interface SortStep {
  array: number[]
  comparing: number[]
  swapping: number[]
  sorted: number[]
  pivot: number | null
  range: [number, number] | null
}

type BarState = 'default' | 'comparing' | 'swapping' | 'sorted' | 'pivot'

// ── Colors ────────────────────────────────────────────────────────────────────

const COLORS: Record<BarState, THREE.Color> = {
  default:   new THREE.Color(0x3B82F6),
  comparing: new THREE.Color(0xFBBF24),
  swapping:  new THREE.Color(0xF43F5E),
  sorted:    new THREE.Color(0x10B981),
  pivot:     new THREE.Color(0xA855F7),
}

const MAX_BAR_HEIGHT = 6

// ── Step Generators ───────────────────────────────────────────────────────────

function generateBubbleSteps(arr: number[]): SortStep[] {
  const a = [...arr]
  const steps: SortStep[] = []
  const sorted: number[] = []
  const n = a.length

  for (let i = 0; i < n - 1; i++) {
    for (let j = 0; j < n - 1 - i; j++) {
      steps.push({ array: [...a], comparing: [j, j + 1], swapping: [], sorted: [...sorted], pivot: null, range: null })
      if (a[j] > a[j + 1]) {
        ;[a[j], a[j + 1]] = [a[j + 1], a[j]]
        steps.push({ array: [...a], comparing: [], swapping: [j, j + 1], sorted: [...sorted], pivot: null, range: null })
      }
    }
    sorted.push(n - 1 - i)
  }
  sorted.push(0)
  steps.push({ array: [...a], comparing: [], swapping: [], sorted: Array.from({ length: n }, (_, i) => i), pivot: null, range: null })
  return steps
}

function generateInsertionSteps(arr: number[]): SortStep[] {
  const a = [...arr]
  const steps: SortStep[] = []
  const n = a.length

  for (let i = 1; i < n; i++) {
    let j = i
    while (j > 0) {
      steps.push({ array: [...a], comparing: [j - 1, j], swapping: [], sorted: [], pivot: null, range: null })
      if (a[j - 1] > a[j]) {
        ;[a[j - 1], a[j]] = [a[j], a[j - 1]]
        steps.push({ array: [...a], comparing: [], swapping: [j - 1, j], sorted: [], pivot: null, range: null })
        j--
      } else break
    }
  }
  steps.push({ array: [...a], comparing: [], swapping: [], sorted: Array.from({ length: n }, (_, i) => i), pivot: null, range: null })
  return steps
}

function generateSelectionSteps(arr: number[]): SortStep[] {
  const a = [...arr]
  const steps: SortStep[] = []
  const sorted: number[] = []
  const n = a.length

  for (let i = 0; i < n - 1; i++) {
    let minIdx = i
    for (let j = i + 1; j < n; j++) {
      steps.push({ array: [...a], comparing: [j, minIdx], swapping: [], sorted: [...sorted], pivot: i, range: null })
      if (a[j] < a[minIdx]) minIdx = j
    }
    if (minIdx !== i) {
      ;[a[i], a[minIdx]] = [a[minIdx], a[i]]
      steps.push({ array: [...a], comparing: [], swapping: [i, minIdx], sorted: [...sorted], pivot: null, range: null })
    }
    sorted.push(i)
  }
  sorted.push(n - 1)
  steps.push({ array: [...a], comparing: [], swapping: [], sorted: Array.from({ length: n }, (_, i) => i), pivot: null, range: null })
  return steps
}

function generateQuickSteps(arr: number[]): SortStep[] {
  const a = [...arr]
  const steps: SortStep[] = []
  const sorted: number[] = []

  function partition(lo: number, hi: number) {
    const pivotVal = a[hi]
    let i = lo - 1
    steps.push({ array: [...a], comparing: [], swapping: [], sorted: [...sorted], pivot: hi, range: [lo, hi] })
    for (let j = lo; j < hi; j++) {
      steps.push({ array: [...a], comparing: [j, hi], swapping: [], sorted: [...sorted], pivot: hi, range: [lo, hi] })
      if (a[j] <= pivotVal) {
        i++
        ;[a[i], a[j]] = [a[j], a[i]]
        if (i !== j) steps.push({ array: [...a], comparing: [], swapping: [i, j], sorted: [...sorted], pivot: hi, range: [lo, hi] })
      }
    }
    ;[a[i + 1], a[hi]] = [a[hi], a[i + 1]]
    steps.push({ array: [...a], comparing: [], swapping: [i + 1, hi], sorted: [...sorted], pivot: null, range: [lo, hi] })
    return i + 1
  }

  function qs(lo: number, hi: number) {
    if (lo < hi) {
      const p = partition(lo, hi)
      sorted.push(p)
      qs(lo, p - 1)
      qs(p + 1, hi)
    } else if (lo === hi) sorted.push(lo)
  }

  qs(0, a.length - 1)
  steps.push({ array: [...a], comparing: [], swapping: [], sorted: Array.from({ length: a.length }, (_, i) => i), pivot: null, range: null })
  return steps
}

function generateMergeSteps(arr: number[]): SortStep[] {
  const a = [...arr]
  const steps: SortStep[] = []

  function merge(lo: number, mid: number, hi: number) {
    const left = a.slice(lo, mid + 1)
    const right = a.slice(mid + 1, hi + 1)
    let i = 0, j = 0, k = lo
    while (i < left.length && j < right.length) {
      steps.push({ array: [...a], comparing: [lo + i, mid + 1 + j], swapping: [], sorted: [], pivot: null, range: [lo, hi] })
      if (left[i] <= right[j]) { a[k++] = left[i++] }
      else { a[k++] = right[j++] }
      steps.push({ array: [...a], comparing: [], swapping: [k - 1, k - 1], sorted: [], pivot: null, range: [lo, hi] })
    }
    while (i < left.length) { a[k++] = left[i++] }
    while (j < right.length) { a[k++] = right[j++] }
  }

  function ms(lo: number, hi: number) {
    if (lo < hi) {
      const mid = Math.floor((lo + hi) / 2)
      ms(lo, mid)
      ms(mid + 1, hi)
      merge(lo, mid, hi)
    }
  }

  ms(0, a.length - 1)
  steps.push({ array: [...a], comparing: [], swapping: [], sorted: Array.from({ length: a.length }, (_, i) => i), pivot: null, range: null })
  return steps
}

function getBarState(idx: number, step: SortStep): BarState {
  if (step.sorted.includes(idx)) return 'sorted'
  if (step.swapping.includes(idx)) return 'swapping'
  if (step.pivot === idx) return 'pivot'
  if (step.comparing.includes(idx)) return 'comparing'
  return 'default'
}

// ── SortBar (inside Canvas) ───────────────────────────────────────────────────

interface SortBarProps {
  x: number
  barWidth: number
  value: number
  maxValue: number
  state: BarState
}

const SortBar: React.FC<SortBarProps> = ({ x, barWidth, value, maxValue, state }) => {
  const meshRef = useRef<THREE.Mesh>(null!)
  const matRef = useRef<THREE.MeshStandardMaterial>(null!)
  const currentColor = useRef(new THREE.Color(COLORS.default))

  const targetHeight = Math.max(0.1, (value / maxValue) * MAX_BAR_HEIGHT)
  const targetColor = COLORS[state]

  useFrame(() => {
    if (!meshRef.current || !matRef.current) return
    // Lerp scale.y → targetHeight
    const sy = meshRef.current.scale.y
    const ns = sy + (targetHeight - sy) * 0.18
    meshRef.current.scale.y = ns
    meshRef.current.position.y = ns / 2

    // Lerp color
    currentColor.current.lerp(targetColor, 0.14)
    matRef.current.color.set(currentColor.current)
    matRef.current.emissive.set(currentColor.current)
    const targetEmissive = state === 'default' ? 0.04 : state === 'sorted' ? 0.12 : 0.35
    matRef.current.emissiveIntensity += (targetEmissive - matRef.current.emissiveIntensity) * 0.12
  })

  return (
    <mesh ref={meshRef} position={[x, 0.05, 0]} scale={[1, 0.05, 1]}>
      <boxGeometry args={[barWidth, 1, barWidth * 0.65]} />
      <meshStandardMaterial
        ref={matRef}
        color={COLORS.default}
        emissive={COLORS.default}
        emissiveIntensity={0.04}
        metalness={0.15}
        roughness={0.55}
      />
    </mesh>
  )
}

// ── SortScene (inside Canvas) ─────────────────────────────────────────────────

interface SortSceneProps {
  step: SortStep
  maxValue: number
}

const SortScene: React.FC<SortSceneProps> = ({ step, maxValue }) => {
  const n = step.array.length
  const barWidth = Math.max(0.35, Math.min(0.85, 14 / n))
  const gap = barWidth * 0.2
  const totalWidth = n * (barWidth + gap) - gap
  const startX = -totalWidth / 2 + barWidth / 2

  return (
    <>
      {/* Lighting */}
      <ambientLight intensity={0.35} />
      <directionalLight position={[10, 14, 8]} intensity={1.2} castShadow />
      <pointLight position={[-8, 10, -6]} intensity={0.6} color="#6366f1" />
      <pointLight position={[8, 4, 6]} intensity={0.4} color="#22d3ee" />

      {/* Floor grid */}
      <Grid
        args={[30, 30]}
        position={[0, 0, 0]}
        cellSize={1}
        cellThickness={0.4}
        cellColor="#1e1e2e"
        sectionSize={5}
        sectionThickness={0.8}
        sectionColor="#2d2d44"
        fadeDistance={25}
        fadeStrength={1}
        infiniteGrid
      />

      {/* Bars */}
      {step.array.map((val, i) => (
        <SortBar
          key={i}
          x={startX + i * (barWidth + gap)}
          barWidth={barWidth}
          value={val}
          maxValue={maxValue}
          state={getBarState(i, step)}
        />
      ))}
    </>
  )
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function randomArray(n: number): number[] {
  return Array.from({ length: n }, () => Math.floor(Math.random() * 95) + 5)
}

function generateSteps(algo: SortAlgorithm, arr: number[]): SortStep[] {
  switch (algo) {
    case 'bubble':    return generateBubbleSteps(arr)
    case 'insertion': return generateInsertionSteps(arr)
    case 'selection': return generateSelectionSteps(arr)
    case 'quick':     return generateQuickSteps(arr)
    case 'merge':     return generateMergeSteps(arr)
  }
}

const ALGO_LABELS: Record<SortAlgorithm, string> = {
  bubble: 'Bubble Sort',
  insertion: 'Insertion Sort',
  selection: 'Selection Sort',
  quick: 'Quick Sort',
  merge: 'Merge Sort',
}

const ALGO_COMPLEXITY: Record<SortAlgorithm, { time: string; space: string; best: string }> = {
  bubble:    { time: 'O(n²)', space: 'O(1)', best: 'O(n)' },
  insertion: { time: 'O(n²)', space: 'O(1)', best: 'O(n)' },
  selection: { time: 'O(n²)', space: 'O(1)', best: 'O(n²)' },
  quick:     { time: 'O(n log n)', space: 'O(log n)', best: 'O(n log n)' },
  merge:     { time: 'O(n log n)', space: 'O(n)', best: 'O(n log n)' },
}

// ── Main Component ────────────────────────────────────────────────────────────

const SortingVisualizer3D: React.FC = () => {
  const [algo, setAlgo] = useState<SortAlgorithm>('bubble')
  const [arraySize, setArraySize] = useState(28)
  const [speed, setSpeed] = useState(3)   // steps per second
  const [isPlaying, setIsPlaying] = useState(false)
  const [currentStep, setCurrentStep] = useState(0)
  const [initialArray, setInitialArray] = useState<number[]>(() => randomArray(28))

  const steps = useMemo(
    () => generateSteps(algo, initialArray),
    [algo, initialArray],
  )

  const maxValue = useMemo(() => Math.max(...initialArray), [initialArray])
  const step = steps[currentStep] ?? steps[steps.length - 1]
  const isFinished = currentStep >= steps.length - 1

  // Auto-play
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
    const arr = randomArray(arraySize)
    setInitialArray(arr)
    setCurrentStep(0)
    setIsPlaying(false)
  }, [arraySize])

  const handleAlgoChange = (a: SortAlgorithm) => {
    setAlgo(a)
    setCurrentStep(0)
    setIsPlaying(false)
  }

  const handleSizeChange = (size: number) => {
    setArraySize(size)
    setInitialArray(randomArray(size))
    setCurrentStep(0)
    setIsPlaying(false)
  }

  return (
    <div className="flex flex-col h-full gap-3">
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 px-4 py-3 bg-[#111113] border border-[#1e1e2e] rounded-xl">
        {/* Algorithm selector */}
        <div className="flex items-center gap-1.5">
          {(Object.keys(ALGO_LABELS) as SortAlgorithm[]).map((a) => (
            <button
              key={a}
              onClick={() => handleAlgoChange(a)}
              className={`px-2.5 py-1 rounded-lg text-xs font-semibold transition-all ${
                algo === a
                  ? 'bg-[#6366f1] text-white shadow-lg shadow-[#6366f1]/30'
                  : 'text-[#6b7280] hover:text-white hover:bg-white/5 border border-white/10'
              }`}
            >
              {ALGO_LABELS[a]}
            </button>
          ))}
        </div>

        {/* Divider */}
        <div className="h-6 w-px bg-white/10" />

        {/* Array size */}
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#6b7280] uppercase tracking-wider font-semibold">Size</span>
          <input
            type="range" min={10} max={50} step={2} value={arraySize}
            onChange={(e) => handleSizeChange(Number(e.target.value))}
            className="w-20 accent-[#6366f1]"
          />
          <span className="text-xs font-mono text-[#94a3b8] w-4">{arraySize}</span>
        </div>

        {/* Speed */}
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-[#6b7280] uppercase tracking-wider font-semibold">Speed</span>
          <input
            type="range" min={1} max={20} step={1} value={speed}
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
            ↺ New Array
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

      {/* Step info + complexity */}
      <div className="flex items-center justify-between px-4 gap-4">
        <div className="flex items-center gap-3">
          <span className="text-xs text-[#6b7280] font-mono">
            Step <span className="text-[#a5b4fc]">{currentStep + 1}</span> / {steps.length}
          </span>
          <div className="flex items-center gap-1.5">
            {step.comparing.length > 0 && <Badge color="#fbbf24">Comparing</Badge>}
            {step.swapping.length > 0 && <Badge color="#f43f5e">Swapping</Badge>}
            {step.pivot !== null && <Badge color="#a855f7">Pivot</Badge>}
            {step.sorted.length === step.array.length && <Badge color="#10b981">✓ Sorted!</Badge>}
          </div>
        </div>
        <div className="flex items-center gap-3 text-[10px] font-mono text-[#6b7280]">
          <span>Avg <span className="text-[#94a3b8]">{ALGO_COMPLEXITY[algo].time}</span></span>
          <span>Best <span className="text-[#94a3b8]">{ALGO_COMPLEXITY[algo].best}</span></span>
          <span>Space <span className="text-[#94a3b8]">{ALGO_COMPLEXITY[algo].space}</span></span>
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
          camera={{ position: [0, 7, 18], fov: 50 }}
          gl={{ antialias: true }}
          style={{ background: 'linear-gradient(to bottom, #060608, #0d0d14)' }}
        >
          <SortScene step={step} maxValue={maxValue} />
          <OrbitControls
            enableDamping
            dampingFactor={0.05}
            minDistance={8}
            maxDistance={35}
            maxPolarAngle={Math.PI / 2.1}
          />
        </Canvas>
      </div>

      {/* Legend */}
      <div className="flex items-center gap-4 px-4 pb-1">
        {Object.entries(COLORS).map(([state, color]) => (
          <div key={state} className="flex items-center gap-1.5">
            <div
              className="w-2.5 h-2.5 rounded-sm"
              style={{ backgroundColor: `#${color.getHexString()}` }}
            />
            <span className="text-[10px] text-[#6b7280] capitalize">{state}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

const Badge: React.FC<{ color: string; children: React.ReactNode }> = ({ color, children }) => (
  <AnimatePresence>
    <motion.span
      initial={{ opacity: 0, scale: 0.8 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.8 }}
      className="text-[10px] font-bold px-2 py-0.5 rounded-full border"
      style={{ color, borderColor: `${color}40`, backgroundColor: `${color}15` }}
    >
      {children}
    </motion.span>
  </AnimatePresence>
)

export default SortingVisualizer3D
