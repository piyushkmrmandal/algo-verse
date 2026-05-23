import React, { useMemo } from 'react'
import { motion } from 'framer-motion'
import type { GraphState } from '@algoverse/ast-parser'

interface GraphVisualizerProps {
  graph: GraphState
}

const NODE_RADIUS = 20
const CANVAS_SIZE = 320

/** Arrange nodes in a circle if they don't have preset coordinates. */
function computeLayout(
  nodes: GraphState['nodes'],
): Map<string, { x: number; y: number }> {
  const layout = new Map<string, { x: number; y: number }>()
  const cx = CANVAS_SIZE / 2
  const cy = CANVAS_SIZE / 2
  const r = (CANVAS_SIZE / 2) * 0.72

  nodes.forEach((node, idx) => {
    if (node.x !== undefined && node.y !== undefined) {
      layout.set(node.id, { x: node.x, y: node.y })
    } else {
      const angle = (2 * Math.PI * idx) / nodes.length - Math.PI / 2
      layout.set(node.id, {
        x: cx + r * Math.cos(angle),
        y: cy + r * Math.sin(angle),
      })
    }
  })

  return layout
}

export const GraphVisualizer: React.FC<GraphVisualizerProps> = React.memo(({ graph }) => {
  const layout = useMemo(() => computeLayout(graph.nodes), [graph.nodes])

  return (
    <div className="flex items-center justify-center w-full">
      <svg
        width={CANVAS_SIZE}
        height={CANVAS_SIZE}
        className="font-mono"
        aria-label="Graph visualization"
      >
        {/* Edges */}
        {graph.edges.map((edge, i) => {
          const from = layout.get(edge.from)
          const to = layout.get(edge.to)
          if (!from || !to) return null

          const dx = to.x - from.x
          const dy = to.y - from.y
          const len = Math.sqrt(dx * dx + dy * dy)
          const ux = dx / len
          const uy = dy / len

          // Start/end points offset from node center
          const x1 = from.x + ux * NODE_RADIUS
          const y1 = from.y + uy * NODE_RADIUS
          const x2 = to.x - ux * NODE_RADIUS
          const y2 = to.y - uy * NODE_RADIUS

          return (
            <g key={i}>
              <line
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke={edge.highlighted ? '#7C3AED' : '#374151'}
                strokeWidth={edge.highlighted ? 2 : 1.5}
                markerEnd={edge.directed ? 'url(#arrowhead)' : undefined}
              />
              {edge.weight !== undefined && (
                <text
                  x={(x1 + x2) / 2}
                  y={(y1 + y2) / 2 - 4}
                  textAnchor="middle"
                  fontSize={9}
                  fill="#6B7280"
                >
                  {edge.weight}
                </text>
              )}
            </g>
          )
        })}

        {/* Arrowhead marker */}
        <defs>
          <marker
            id="arrowhead"
            markerWidth={8}
            markerHeight={6}
            refX={8}
            refY={3}
            orient="auto"
          >
            <polygon points="0 0, 8 3, 0 6" fill="#4B5563" />
          </marker>
        </defs>

        {/* Nodes */}
        {graph.nodes.map((node) => {
          const pos = layout.get(node.id)
          if (!pos) return null

          let fill = '#1F2937'
          let stroke = '#374151'
          let textColor = '#9CA3AF'

          if (node.visiting) {
            fill = '#5B21B6'
            stroke = '#7C3AED'
            textColor = '#DDD6FE'
          } else if (node.inQueue) {
            fill = '#1E3A5F'
            stroke = '#2563EB'
            textColor = '#93C5FD'
          } else if (node.visited) {
            fill = '#064E3B'
            stroke = '#059669'
            textColor = '#6EE7B7'
          }

          return (
            <motion.g
              key={node.id}
              initial={{ opacity: 0, scale: 0.6 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ type: 'spring', stiffness: 350, damping: 25 }}
            >
              <motion.circle
                cx={pos.x}
                cy={pos.y}
                r={NODE_RADIUS}
                fill={fill}
                stroke={stroke}
                strokeWidth={node.visiting ? 2.5 : 1.5}
                animate={
                  node.visiting
                    ? {
                        filter: [
                          'drop-shadow(0 0 0px #7C3AED)',
                          'drop-shadow(0 0 10px #7C3AED)',
                          'drop-shadow(0 0 0px #7C3AED)',
                        ],
                      }
                    : {}
                }
                transition={{ duration: 1.2, repeat: node.visiting ? Infinity : 0 }}
              />
              <text
                x={pos.x}
                y={pos.y + 4}
                textAnchor="middle"
                fontSize={11}
                fill={textColor}
                fontWeight={node.visiting ? 700 : 400}
              >
                {node.label}
              </text>
            </motion.g>
          )
        })}
      </svg>
    </div>
  )
})

GraphVisualizer.displayName = 'GraphVisualizer'
