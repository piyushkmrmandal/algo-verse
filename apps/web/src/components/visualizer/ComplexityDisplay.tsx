import React from 'react'
import type { ComplexityClass } from '@algoverse/ast-parser'

interface ComplexityDisplayProps {
  timeComplexity: ComplexityClass
  spaceComplexity: ComplexityClass
}

const COMPLEXITY_COLOR: Record<ComplexityClass, string> = {
  'O(1)': '#34D399',
  'O(log n)': '#6EE7B7',
  'O(n)': '#60A5FA',
  'O(n log n)': '#A78BFA',
  'O(n²)': '#FBBF24',
  'O(n³)': '#F97316',
  'O(2^n)': '#EF4444',
  'O(n!)': '#DC2626',
  'O(√n)': '#67E8F9',
}

export const ComplexityDisplay: React.FC<ComplexityDisplayProps> = ({
  timeComplexity,
  spaceComplexity,
}) => {
  return (
    <div className="flex items-center gap-3">
      <ComplexityPill label="Time" value={timeComplexity} />
      <ComplexityPill label="Space" value={spaceComplexity} />
    </div>
  )
}

const ComplexityPill: React.FC<{ label: string; value: ComplexityClass }> = ({
  label,
  value,
}) => {
  const color = COMPLEXITY_COLOR[value] ?? '#9CA3AF'
  return (
    <div className="flex items-center gap-1.5">
      <span className="text-[10px] text-white/30 font-medium">{label}</span>
      <span
        className="text-[11px] font-mono font-bold px-2 py-0.5 rounded-full"
        style={{
          color,
          backgroundColor: `${color}18`,
          border: `1px solid ${color}35`,
        }}
      >
        {value}
      </span>
    </div>
  )
}
