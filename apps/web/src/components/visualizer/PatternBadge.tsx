import React from 'react'
import type { AlgorithmPattern } from '@algoverse/ast-parser'

interface PatternBadgeProps {
  patterns: AlgorithmPattern[]
}

const PATTERN_LABELS: Partial<Record<AlgorithmPattern, string>> = {
  'two-pointer': 'Two Pointer',
  'sliding-window': 'Sliding Window',
  'fast-slow-pointer': 'Fast & Slow',
  'tree-bfs': 'Tree BFS',
  'tree-dfs': 'Tree DFS',
  'graph-bfs': 'Graph BFS',
  'graph-dfs': 'Graph DFS',
  'dp-0-1-knapsack': '0/1 Knapsack',
  'dp-fibonacci': 'Fibonacci DP',
  'dp-lcs': 'LCS',
  'dp-lis': 'LIS',
  'dp-palindrome': 'Palindrome DP',
  'dp-unbounded-knapsack': 'Unbounded Knapsack',
  backtracking: 'Backtracking',
  'modified-binary-search': 'Binary Search',
  'monotonic-stack': 'Monotonic Stack',
  'union-find': 'Union Find',
  'prefix-sum': 'Prefix Sum',
  'merge-intervals': 'Merge Intervals',
  'topological-sort': 'Topo Sort',
  'divide-and-conquer': 'Divide & Conquer',
  greedy: 'Greedy',
  'two-heaps': 'Two Heaps',
  subsets: 'Subsets',
  unknown: 'Unknown',
}

const PATTERN_COLORS: Partial<Record<AlgorithmPattern, string>> = {
  'two-pointer': '#06B6D4',
  'sliding-window': '#8B5CF6',
  'fast-slow-pointer': '#06B6D4',
  'tree-bfs': '#10B981',
  'tree-dfs': '#059669',
  'graph-bfs': '#3B82F6',
  'graph-dfs': '#2563EB',
  'dp-0-1-knapsack': '#F59E0B',
  'dp-fibonacci': '#D97706',
  'dp-lcs': '#F59E0B',
  backtracking: '#EF4444',
  'modified-binary-search': '#8B5CF6',
  'monotonic-stack': '#F472B6',
  'union-find': '#22D3EE',
  'prefix-sum': '#34D399',
  'merge-intervals': '#A78BFA',
  'topological-sort': '#60A5FA',
  'divide-and-conquer': '#C084FC',
  greedy: '#4ADE80',
  unknown: '#6B7280',
}

export const PatternBadge: React.FC<PatternBadgeProps> = ({ patterns }) => {
  const displayPatterns = patterns.filter((p) => p !== 'unknown').slice(0, 3)

  if (displayPatterns.length === 0) return null

  return (
    <div className="flex flex-wrap gap-1.5">
      {displayPatterns.map((pattern) => {
        const color = PATTERN_COLORS[pattern] ?? '#6B7280'
        const label = PATTERN_LABELS[pattern] ?? pattern
        return (
          <span
            key={pattern}
            className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
            style={{
              color,
              backgroundColor: `${color}20`,
              border: `1px solid ${color}40`,
            }}
          >
            {label}
          </span>
        )
      })}
    </div>
  )
}
