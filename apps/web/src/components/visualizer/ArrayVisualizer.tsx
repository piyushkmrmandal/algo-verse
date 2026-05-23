import React, { useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import type { ArrayState } from '@algoverse/ast-parser'

// Virtualisation threshold: beyond this, only show a window of ±10 around active pointers
const VIRTUALIZE_THRESHOLD = 50

interface ArrayVisualizerProps {
  arrays: Map<string, ArrayState>
  prevArrays?: Map<string, ArrayState>
  step: number
}

export const ArrayVisualizer: React.FC<ArrayVisualizerProps> = React.memo(
  ({ arrays, prevArrays, step }) => {
    const arrayEntries = useMemo(() => Array.from(arrays.entries()), [arrays])

    if (arrayEntries.length === 0) {
      return (
        <div className="flex items-center justify-center h-16 text-xs text-white/20">
          No arrays in this frame
        </div>
      )
    }

    return (
      <div className="flex flex-col gap-4 p-2">
        {arrayEntries.map(([name, arrState]) => {
          const prevArr = prevArrays?.get(name)
          return (
            <SingleArrayView
              key={name}
              name={name}
              arrState={arrState}
              prevArrState={prevArr}
              step={step}
            />
          )
        })}
      </div>
    )
  },
)

ArrayVisualizer.displayName = 'ArrayVisualizer'

// ─── SingleArrayView ──────────────────────────────────────────────────────────

interface SingleArrayViewProps {
  name: string
  arrState: ArrayState
  prevArrState: ArrayState | undefined
  step: number
}

const SingleArrayView: React.FC<SingleArrayViewProps> = React.memo(
  ({ name, arrState, prevArrState, step }) => {
    const { elements, pointers } = arrState
    const isLong = elements.length > VIRTUALIZE_THRESHOLD

    // Build a set of pointer indices for quick lookup
    const pointerByIndex = useMemo<Map<number, { name: string; color: string }[]>>(() => {
      const map = new Map<number, { name: string; color: string }[]>()
      for (const ptr of pointers) {
        const list = map.get(ptr.index) ?? []
        list.push({ name: ptr.name, color: ptr.color })
        map.set(ptr.index, list)
      }
      return map
    }, [pointers])

    // For long arrays: determine window of indices to show
    const visibleIndices = useMemo<number[]>(() => {
      if (!isLong) return elements.map((_, i) => i)
      const activeIndices = new Set<number>()
      for (const ptr of pointers) {
        for (let d = -10; d <= 10; d++) {
          const idx = ptr.index + d
          if (idx >= 0 && idx < elements.length) activeIndices.add(idx)
        }
      }
      // Also include any highlighted/swapped/comparing indices
      elements.forEach((el, i) => {
        if (el.highlighted || el.swapped || el.comparing) activeIndices.add(i)
      })
      // Always show first and last 3
      for (let i = 0; i < Math.min(3, elements.length); i++) activeIndices.add(i)
      for (let i = Math.max(0, elements.length - 3); i < elements.length; i++) activeIndices.add(i)
      return Array.from(activeIndices).sort((a, b) => a - b)
    }, [elements, pointers, isLong])

    // Compute max numeric value for bar height scaling
    const maxVal = useMemo(() => {
      const nums = elements.map((e) => (typeof e.value === 'number' ? e.value : 0))
      return Math.max(1, ...nums)
    }, [elements])

    const isNumeric = elements.length > 0 && elements.some((e) => typeof e.value === 'number')

    return (
      <div className="flex flex-col gap-1.5">
        {/* Array name label */}
        <div className="flex items-center gap-2">
          <span className="text-[11px] font-mono font-semibold text-[#A78BFA]">{name}</span>
          <span className="text-[10px] text-white/25 font-mono">
            [{elements.length}]
          </span>
        </div>

        {/* Pointer labels row */}
        {pointers.length > 0 && (
          <PointerLabelRow
            pointers={pointers}
            totalElements={elements.length}
            visibleIndices={visibleIndices}
          />
        )}

        {/* Cells row */}
        <div className="flex items-end gap-0.5 overflow-x-auto pb-1">
          <AnimatePresence mode="popLayout">
            {visibleIndices.map((idx, visiblePos) => {
              const el = elements[idx]
              const ptrs = pointerByIndex.get(idx)
              const prevEl = prevArrState?.elements[idx]

              // Detect gaps in visible indices for ellipsis
              const prevVisible = visibleIndices[visiblePos - 1]
              const showEllipsisBefore =
                isLong && visiblePos > 0 && idx - prevVisible > 1

              return (
                <React.Fragment key={`${idx}`}>
                  {showEllipsisBefore && (
                    <div className="flex flex-col items-center justify-center w-8 shrink-0">
                      <div className="text-white/20 text-xs tracking-widest">···</div>
                    </div>
                  )}
                  <ArrayCell
                    index={idx}
                    value={el.value}
                    prevValue={prevEl?.value}
                    highlighted={el.highlighted}
                    swapped={el.swapped}
                    comparing={el.comparing}
                    color={el.color}
                    pointers={ptrs}
                    isNumeric={isNumeric}
                    maxVal={maxVal}
                    step={step}
                  />
                </React.Fragment>
              )
            })}
          </AnimatePresence>
        </div>

        {/* Index labels row */}
        <div className="flex gap-0.5 overflow-x-auto">
          {visibleIndices.map((idx, visiblePos) => {
            const prevVisible = visibleIndices[visiblePos - 1]
            const showEllipsisBefore = isLong && visiblePos > 0 && idx - prevVisible > 1
            return (
              <React.Fragment key={`idx-${idx}`}>
                {showEllipsisBefore && <div className="w-8 shrink-0" />}
                <div className="w-8 shrink-0 text-center text-[9px] font-mono text-white/20">
                  {idx}
                </div>
              </React.Fragment>
            )
          })}
        </div>
      </div>
    )
  },
)

SingleArrayView.displayName = 'SingleArrayView'

// ─── PointerLabelRow ──────────────────────────────────────────────────────────

interface PointerLabelRowProps {
  pointers: { name: string; index: number; color: string }[]
  totalElements: number
  visibleIndices: number[]
}

const PointerLabelRow: React.FC<PointerLabelRowProps> = ({ pointers, visibleIndices }) => {
  // Map pointer index to position in the visible cells row
  const indexToVisiblePos = useMemo(() => {
    const map = new Map<number, number>()
    visibleIndices.forEach((idx, pos) => map.set(idx, pos))
    return map
  }, [visibleIndices])

  return (
    <div className="relative flex gap-0.5" style={{ height: '20px' }}>
      {pointers.map((ptr) => {
        const pos = indexToVisiblePos.get(ptr.index)
        if (pos === undefined) return null
        return (
          <motion.div
            key={ptr.name}
            layoutId={`ptr-${ptr.name}`}
            className="absolute flex flex-col items-center"
            style={{
              left: `calc(${pos} * (32px + 2px) + 16px)`,
              transform: 'translateX(-50%)',
            }}
            transition={{ type: 'spring', stiffness: 500, damping: 40 }}
          >
            <span
              className="text-[9px] font-mono font-bold px-1 rounded"
              style={{ color: ptr.color, backgroundColor: `${ptr.color}20` }}
            >
              {ptr.name}
            </span>
            <span style={{ color: ptr.color, fontSize: '8px', lineHeight: 1 }}>▼</span>
          </motion.div>
        )
      })}
    </div>
  )
}

// ─── ArrayCell ────────────────────────────────────────────────────────────────

interface ArrayCellProps {
  index: number
  value: unknown
  prevValue: unknown
  highlighted: boolean
  swapped: boolean
  comparing: boolean
  color?: string
  pointers?: { name: string; color: string }[]
  isNumeric: boolean
  maxVal: number
  step: number
}

const CELL_WIDTH = 32
const MAX_BAR_HEIGHT = 48
const MIN_BAR_HEIGHT = 4

const ArrayCell: React.FC<ArrayCellProps> = React.memo(
  ({
    index,
    value,
    prevValue,
    highlighted,
    swapped,
    comparing,
    color,
    pointers,
    isNumeric,
    maxVal,
    step,
  }) => {
    const hasChanged = value !== prevValue

    // Determine cell state color
    let cellBorderColor = 'border-white/10'
    let cellBgColor = 'bg-[#1A1A1D]'
    let textColor = 'text-white/70'

    if (swapped) {
      cellBorderColor = 'border-[#F59E0B]/70'
      cellBgColor = 'bg-[#F59E0B]/10'
      textColor = 'text-[#FCD34D]'
    } else if (comparing) {
      cellBorderColor = 'border-[#FBBF24]/60'
      cellBgColor = 'bg-[#FBBF24]/8'
      textColor = 'text-[#FDE68A]'
    } else if (highlighted || pointers?.length) {
      cellBorderColor = 'border-[#7C3AED]/60'
      cellBgColor = 'bg-[#7C3AED]/10'
      textColor = 'text-[#C4B5FD]'
    } else if (color) {
      cellBgColor = `bg-opacity-10`
    }

    // Bar height for numeric arrays
    const barHeight =
      isNumeric && typeof value === 'number'
        ? Math.max(
            MIN_BAR_HEIGHT,
            Math.round((value / maxVal) * MAX_BAR_HEIGHT),
          )
        : 0

    const barColor =
      swapped
        ? '#F59E0B'
        : comparing
        ? '#FBBF24'
        : highlighted || pointers?.length
        ? '#7C3AED'
        : color ?? '#4B5563'

    const displayValue = value === null ? 'null' : value === undefined ? '?' : String(value)
    const truncatedValue =
      displayValue.length > 4 ? displayValue.slice(0, 3) + '…' : displayValue

    return (
      <motion.div
        layout
        layoutId={`cell-${index}`}
        className="flex flex-col items-center shrink-0"
        style={{ width: `${CELL_WIDTH}px` }}
        transition={{ type: 'spring', stiffness: 600, damping: 40 }}
      >
        {/* Numeric bar */}
        {isNumeric && (
          <motion.div
            className="w-5 rounded-t"
            style={{ backgroundColor: `${barColor}60` }}
            animate={{ height: barHeight }}
            transition={{ type: 'spring', stiffness: 300, damping: 30 }}
          />
        )}

        {/* Cell box */}
        <motion.div
          className={[
            'w-8 h-8 flex items-center justify-center',
            'border rounded text-[10px] font-mono font-semibold',
            'relative overflow-hidden',
            cellBorderColor,
            cellBgColor,
            textColor,
          ].join(' ')}
          animate={
            swapped
              ? { scale: [1, 1.15, 1], borderColor: ['#F59E0B', '#F59E0B', '#F59E0B'] }
              : comparing
              ? {
                  boxShadow: [
                    '0 0 0px #FBBF2400',
                    '0 0 8px #FBBF24aa',
                    '0 0 0px #FBBF2400',
                  ],
                }
              : hasChanged
              ? { scale: [1, 1.08, 1] }
              : {}
          }
          transition={{ duration: 0.35 }}
          title={`[${index}] = ${String(value)}`}
        >
          {/* Changed flash overlay */}
          {hasChanged && (
            <motion.div
              className="absolute inset-0 bg-[#34D399]/20 rounded"
              initial={{ opacity: 1 }}
              animate={{ opacity: 0 }}
              transition={{ duration: 0.6 }}
            />
          )}
          {truncatedValue}
        </motion.div>
      </motion.div>
    )
  },
)

ArrayCell.displayName = 'ArrayCell'
