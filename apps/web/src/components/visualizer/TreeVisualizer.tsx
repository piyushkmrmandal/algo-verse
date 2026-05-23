import React, { useMemo } from 'react'
import { motion } from 'framer-motion'
import type { TreeNode } from '@algoverse/ast-parser'

interface TreeVisualizerProps {
  root: TreeNode
}

const NODE_RADIUS = 18
const HORIZONTAL_GAP = 48
const VERTICAL_GAP = 56

interface LayoutNode extends TreeNode {
  x: number
  y: number
  left?: LayoutNode
  right?: LayoutNode
}

/** Assign x/y coordinates to every node using in-order traversal. */
function layoutTree(node: TreeNode, depth = 0, counter = { value: 0 }): LayoutNode {
  const laid: LayoutNode = { ...node, x: 0, y: depth * VERTICAL_GAP }

  if (node.left) {
    laid.left = layoutTree(node.left, depth + 1, counter)
  }

  laid.x = counter.value * HORIZONTAL_GAP
  counter.value++

  if (node.right) {
    laid.right = layoutTree(node.right, depth + 1, counter)
  }

  return laid
}

function collectNodes(node: LayoutNode): LayoutNode[] {
  const result: LayoutNode[] = [node]
  if (node.left) result.push(...collectNodes(node.left))
  if (node.right) result.push(...collectNodes(node.right))
  return result
}

interface EdgeDef {
  x1: number
  y1: number
  x2: number
  y2: number
}

function collectEdges(node: LayoutNode): EdgeDef[] {
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

export const TreeVisualizer: React.FC<TreeVisualizerProps> = React.memo(({ root }) => {
  const layoutRoot = useMemo(() => layoutTree(root), [root])
  const nodes = useMemo(() => collectNodes(layoutRoot), [layoutRoot])
  const edges = useMemo(() => collectEdges(layoutRoot), [layoutRoot])

  const minX = Math.min(...nodes.map((n) => n.x))
  const maxX = Math.max(...nodes.map((n) => n.x))
  const maxY = Math.max(...nodes.map((n) => n.y))

  const svgWidth = maxX - minX + NODE_RADIUS * 4 + 16
  const svgHeight = maxY + NODE_RADIUS * 4 + 16
  const offsetX = -minX + NODE_RADIUS * 2 + 8
  const offsetY = NODE_RADIUS * 2 + 8

  return (
    <div className="overflow-auto w-full">
      <svg
        width={svgWidth}
        height={svgHeight}
        className="font-mono"
        aria-label="Binary tree visualization"
      >
        {/* Edges */}
        {edges.map((edge, i) => (
          <line
            key={i}
            x1={edge.x1 + offsetX}
            y1={edge.y1 + offsetY}
            x2={edge.x2 + offsetX}
            y2={edge.y2 + offsetY}
            stroke="#374151"
            strokeWidth={1.5}
          />
        ))}

        {/* Nodes */}
        {nodes.map((node) => {
          let fill = '#1F2937'
          let stroke = '#374151'
          let textColor = '#9CA3AF'

          if (node.visiting) {
            fill = '#7C3AED'
            stroke = '#A78BFA'
            textColor = '#EDE9FE'
          } else if (node.highlighted) {
            fill = '#1E3A5F'
            stroke = '#3B82F6'
            textColor = '#93C5FD'
          } else if (node.visited) {
            fill = '#064E3B'
            stroke = '#059669'
            textColor = '#6EE7B7'
          }

          const label =
            node.value === null ? '∅' : String(node.value).slice(0, 4)

          return (
            <motion.g
              key={node.id}
              animate={{ opacity: 1 }}
              initial={{ opacity: 0 }}
              transition={{ duration: 0.3 }}
            >
              <motion.circle
                cx={node.x + offsetX}
                cy={node.y + offsetY}
                r={NODE_RADIUS}
                fill={fill}
                stroke={stroke}
                strokeWidth={node.visiting ? 2.5 : 1.5}
                animate={
                  node.visiting
                    ? {
                        filter: [
                          'drop-shadow(0 0 0px #7C3AED)',
                          'drop-shadow(0 0 8px #7C3AED)',
                          'drop-shadow(0 0 0px #7C3AED)',
                        ],
                      }
                    : {}
                }
                transition={{ duration: 1.2, repeat: node.visiting ? Infinity : 0 }}
              />
              <text
                x={node.x + offsetX}
                y={node.y + offsetY + 4}
                textAnchor="middle"
                fontSize={11}
                fill={textColor}
                fontWeight={node.visiting ? 700 : 400}
              >
                {label}
              </text>
            </motion.g>
          )
        })}
      </svg>
    </div>
  )
})

TreeVisualizer.displayName = 'TreeVisualizer'
