import React, { lazy, Suspense, useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import AppNav from '../components/ui/AppNav'

// Lazy-load heavy Three.js components
const SortingVisualizer3D = lazy(
  () => import('../components/visualizer/three/SortingVisualizer3D'),
)
const BSTVisualizer3D = lazy(
  () => import('../components/visualizer/three/BSTVisualizer3D'),
)
const GraphVisualizer3D = lazy(
  () => import('../components/visualizer/three/GraphVisualizer3D'),
)

// ── Tab config ────────────────────────────────────────────────────────────────

type Tab = 'sorting' | 'bst' | 'graph'

interface TabConfig {
  id: Tab
  label: string
  icon: string
  description: string
  details: string[]
}

const TABS: TabConfig[] = [
  {
    id: 'sorting',
    label: 'Sorting',
    icon: '▦',
    description: 'Watch classic sorting algorithms execute step-by-step in a 3D bar chart.',
    details: ['Bubble Sort', 'Insertion Sort', 'Selection Sort', 'Quick Sort', 'Merge Sort'],
  },
  {
    id: 'bst',
    label: 'Binary Search Tree',
    icon: '⬡',
    description: 'Build and traverse a BST interactively — insert, search, and explore the tree structure.',
    details: ['Insert nodes', 'Search with path highlighting', 'Animated traversal', 'O(log n) avg'],
  },
  {
    id: 'graph',
    label: 'Graph Traversal',
    icon: '⬡',
    description: 'Visualize BFS and DFS traversal on a 3D graph with live queue/stack display.',
    details: ['BFS — breadth-first', 'DFS — depth-first', 'Live queue / stack', 'Node state colors'],
  },
]

// ── Loading fallback ──────────────────────────────────────────────────────────

const VisualizerLoader: React.FC = () => (
  <div className="flex-1 flex flex-col items-center justify-center gap-4">
    <motion.div
      className="w-14 h-14 rounded-2xl bg-[#6366f1]/15 border border-[#6366f1]/20 flex items-center justify-center"
      animate={{ scale: [1, 1.08, 1], opacity: [0.7, 1, 0.7] }}
      transition={{ repeat: Infinity, duration: 1.6, ease: 'easeInOut' }}
    >
      <svg width="28" height="28" viewBox="0 0 24 24" fill="none" className="text-[#6366f1]">
        <path d="M12 2L2 7l10 5 10-5-10-5z" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M2 17l10 5 10-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M2 12l10 5 10-5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </motion.div>
    <p className="text-[#6b7280] text-sm font-mono">Initialising 3D engine…</p>
  </div>
)

// ── Page ──────────────────────────────────────────────────────────────────────

const AlgoVisualizerPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('sorting')
  const tab = TABS.find((t) => t.id === activeTab)!

  return (
    <div className="min-h-screen bg-[#060608] flex flex-col">
      <AppNav />

      {/* Header */}
      <div className="border-b border-white/5 bg-[#0a0a0f]/60 backdrop-blur-sm">
        <div className="max-w-7xl mx-auto px-6 py-5">
          <div className="flex items-start justify-between gap-6">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <div className="w-1.5 h-1.5 rounded-full bg-[#6366f1] animate-pulse" />
                <span className="text-[10px] font-bold text-[#6366f1] uppercase tracking-widest">
                  Interactive 3D Visualizer
                </span>
              </div>
              <h1 className="text-2xl font-black text-white tracking-tight">
                Algorithm Visualizer
              </h1>
              <p className="text-sm text-[#6b7280] mt-1 max-w-xl">
                {tab.description}
              </p>
            </div>

            {/* Details chips */}
            <div className="hidden md:flex flex-wrap gap-1.5 justify-end max-w-xs">
              {tab.details.map((d) => (
                <span
                  key={d}
                  className="text-[10px] px-2 py-0.5 rounded-full bg-[#6366f1]/10 text-[#a5b4fc] border border-[#6366f1]/20 font-medium"
                >
                  {d}
                </span>
              ))}
            </div>
          </div>

          {/* Tabs */}
          <div className="flex items-center gap-1 mt-4">
            {TABS.map((t) => (
              <button
                key={t.id}
                onClick={() => setActiveTab(t.id)}
                className={`relative px-4 py-2 rounded-t-lg text-sm font-semibold transition-all duration-150 ${
                  activeTab === t.id
                    ? 'text-white bg-[#111113] border border-b-0 border-white/10'
                    : 'text-[#6b7280] hover:text-[#94a3b8] hover:bg-white/5'
                }`}
              >
                {activeTab === t.id && (
                  <motion.div
                    layoutId="tab-indicator"
                    className="absolute inset-x-0 -bottom-px h-0.5 bg-gradient-to-r from-[#6366f1] to-[#22d3ee]"
                  />
                )}
                {t.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Visualizer area */}
      <div className="flex-1 max-w-7xl w-full mx-auto px-6 py-4 flex flex-col" style={{ minHeight: 0 }}>
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            className="flex-1 flex flex-col"
            style={{ minHeight: 0 }}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.22 }}
          >
            <Suspense fallback={<VisualizerLoader />}>
              {activeTab === 'sorting' && <SortingVisualizer3D />}
              {activeTab === 'bst' && <BSTVisualizer3D />}
              {activeTab === 'graph' && <GraphVisualizer3D />}
            </Suspense>
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  )
}

export default AlgoVisualizerPage
