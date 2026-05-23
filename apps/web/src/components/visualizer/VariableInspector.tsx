import React, { useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import type { VariableState } from '@algoverse/ast-parser'
import { useVisualizerStore } from '../../stores/visualizer-store'

interface VariableInspectorProps {
  variables: Map<string, VariableState>
  prevVariables?: Map<string, VariableState>
}

const TYPE_COLORS: Record<string, string> = {
  number: '#60A5FA',
  string: '#34D399',
  boolean: '#F59E0B',
  array: '#A78BFA',
  object: '#F472B6',
  pointer: '#22D3EE',
  null: '#9CA3AF',
  undefined: '#6B7280',
}

export const VariableInspector: React.FC<VariableInspectorProps> = React.memo(
  ({ variables, prevVariables }) => {
    const focusedVariable = useVisualizerStore((s) => s.focusedVariable)
    const setFocusedVariable = useVisualizerStore((s) => s.setFocusedVariable)

    const entries = Array.from(variables.entries())

    if (entries.length === 0) {
      return (
        <div className="flex items-center justify-center h-16 text-xs text-white/20">
          No variables in scope
        </div>
      )
    }

    return (
      <div className="flex flex-col gap-1 p-2">
        <span className="text-[11px] font-semibold text-white/40 uppercase tracking-widest px-1 mb-1">
          Variables
        </span>

        <AnimatePresence initial={false}>
          {entries.map(([name, varState]) => {
            const prevVal = prevVariables?.get(name)?.value
            const changed = varState.value !== prevVal
            const isFocused = focusedVariable === name

            return (
              <VariableRow
                key={name}
                name={name}
                varState={varState}
                changed={changed}
                isFocused={isFocused}
                onFocus={setFocusedVariable}
              />
            )
          })}
        </AnimatePresence>
      </div>
    )
  },
)

VariableInspector.displayName = 'VariableInspector'

// ─── VariableRow ──────────────────────────────────────────────────────────────

interface VariableRowProps {
  name: string
  varState: VariableState
  changed: boolean
  isFocused: boolean
  onFocus: (name: string | null) => void
}

const VariableRow: React.FC<VariableRowProps> = React.memo(
  ({ name, varState, changed, isFocused, onFocus }) => {
    const handleClick = useCallback(() => {
      onFocus(isFocused ? null : name)
    }, [name, isFocused, onFocus])

    const typeColor = TYPE_COLORS[varState.type] ?? '#9CA3AF'
    const displayValue = formatVariableValue(varState.value)

    return (
      <motion.div
        layout
        initial={{ opacity: 0, x: -8 }}
        animate={{ opacity: 1, x: 0 }}
        exit={{ opacity: 0, x: 8 }}
        transition={{ type: 'spring', stiffness: 400, damping: 35 }}
        className={[
          'flex items-center gap-2 px-2 py-1.5 rounded-md cursor-pointer transition-colors',
          isFocused
            ? 'bg-[#7C3AED]/15 border border-[#7C3AED]/30'
            : 'hover:bg-white/4 border border-transparent',
        ].join(' ')}
        onClick={handleClick}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter') handleClick() }}
        aria-pressed={isFocused}
      >
        {/* Loop counter badge */}
        {varState.isLoopCounter && (
          <span className="text-[8px] font-bold text-[#F59E0B] uppercase tracking-widest flex-shrink-0">
            i
          </span>
        )}

        {/* Variable name */}
        <span
          className="text-[11px] font-mono font-semibold flex-shrink-0"
          style={{ color: typeColor }}
        >
          {name}
        </span>

        {/* Type badge */}
        <span
          className="text-[9px] font-mono px-1 rounded flex-shrink-0 opacity-50"
          style={{ color: typeColor, backgroundColor: `${typeColor}18` }}
        >
          {varState.type}
        </span>

        {/* Separator */}
        <span className="text-white/20 text-xs">=</span>

        {/* Value */}
        <motion.span
          className={[
            'text-[11px] font-mono truncate flex-1 text-right',
            changed ? 'text-[#34D399]' : 'text-white/60',
          ].join(' ')}
          animate={changed ? { scale: [1, 1.06, 1] } : {}}
          transition={{ duration: 0.3 }}
          title={String(varState.value)}
        >
          {changed && (
            <motion.span
              className="inline-block w-1.5 h-1.5 rounded-full bg-[#34D399] mr-1 align-middle"
              initial={{ opacity: 1, scale: 1.5 }}
              animate={{ opacity: 0.4, scale: 1 }}
              transition={{ duration: 0.8 }}
            />
          )}
          {displayValue}
        </motion.span>

        {/* Pointer arrow if applicable */}
        {varState.pointsTo && (
          <span className="text-[9px] text-[#22D3EE] font-mono flex-shrink-0">
            → {varState.pointsTo}
          </span>
        )}
      </motion.div>
    )
  },
)

VariableRow.displayName = 'VariableRow'

// ─── Utilities ───────────────────────────────────────────────────────────────

function formatVariableValue(value: unknown): string {
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  if (typeof value === 'boolean') return String(value)
  if (typeof value === 'number') return String(value)
  if (typeof value === 'string') {
    const escaped = value.length > 20 ? value.slice(0, 20) + '…' : value
    return `"${escaped}"`
  }
  if (Array.isArray(value)) {
    const preview = (value as unknown[]).slice(0, 5).map(formatVariableValue).join(', ')
    return `[${preview}${value.length > 5 ? `, … +${value.length - 5}` : ''}]`
  }
  if (typeof value === 'object') {
    const keys = Object.keys(value as object).slice(0, 3)
    return `{${keys.join(', ')}${Object.keys(value as object).length > 3 ? ', …' : ''}}`
  }
  return String(value)
}
