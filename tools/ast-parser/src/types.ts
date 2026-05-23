export type Language = 'python' | 'java' | 'cpp' | 'javascript' | 'go' | 'rust'

export type ASTNodeType =
  | 'Program'
  | 'FunctionDeclaration'
  | 'ClassDeclaration'
  | 'ForStatement'
  | 'WhileStatement'
  | 'DoWhileStatement'
  | 'IfStatement'
  | 'ElseClause'
  | 'ReturnStatement'
  | 'BreakStatement'
  | 'ContinueStatement'
  | 'VariableDeclaration'
  | 'AssignmentExpression'
  | 'AugmentedAssignment'
  | 'BinaryExpression'
  | 'UnaryExpression'
  | 'TernaryExpression'
  | 'CallExpression'
  | 'SubscriptExpression'
  | 'AttributeAccess'
  | 'ArrayExpression'
  | 'ObjectExpression'
  | 'MemberExpression'
  | 'Identifier'
  | 'Literal'
  | 'Comment'
  | 'ImportStatement'
  | 'Block'

export type AlgorithmPattern =
  | 'sliding-window'
  | 'two-pointer'
  | 'fast-slow-pointer'
  | 'merge-intervals'
  | 'cyclic-sort'
  | 'in-place-reversal'
  | 'tree-bfs'
  | 'tree-dfs'
  | 'two-heaps'
  | 'subsets'
  | 'modified-binary-search'
  | 'bitwise-xor'
  | 'top-k-elements'
  | 'k-way-merge'
  | 'dp-0-1-knapsack'
  | 'dp-unbounded-knapsack'
  | 'dp-fibonacci'
  | 'dp-palindrome'
  | 'dp-lcs'
  | 'dp-lis'
  | 'graph-bfs'
  | 'graph-dfs'
  | 'topological-sort'
  | 'union-find'
  | 'monotonic-stack'
  | 'prefix-sum'
  | 'backtracking'
  | 'greedy'
  | 'divide-and-conquer'
  | 'unknown'

export type ComplexityClass =
  | 'O(1)'
  | 'O(log n)'
  | 'O(n)'
  | 'O(n log n)'
  | 'O(n²)'
  | 'O(n³)'
  | 'O(2^n)'
  | 'O(n!)'
  | 'O(√n)'

export interface Position {
  line: number
  column: number
  offset: number
}

export interface ASTNode {
  id: string
  type: ASTNodeType
  name?: string
  value?: string | number | boolean | null
  dataType?: string
  children: ASTNode[]
  parentId?: string
  position: Position
  endPosition: Position
  metadata: {
    complexityAnnotation?: ComplexityClass
    patternTag?: AlgorithmPattern
    isLoop: boolean
    isRecursive: boolean
    isConditional: boolean
    loopVariables?: string[]
    recursionBaseCase?: boolean
    nestedDepth: number
  }
}

export type VariableType =
  | 'number'
  | 'string'
  | 'boolean'
  | 'array'
  | 'object'
  | 'pointer'
  | 'null'
  | 'undefined'

export interface VariableState {
  name: string
  value: unknown
  type: VariableType
  address?: string
  pointsTo?: string
  changed: boolean
  isLoopCounter: boolean
}

export interface ArrayState {
  name: string
  elements: {
    value: unknown
    highlighted: boolean
    swapped: boolean
    comparing: boolean
    color?: string
  }[]
  pointers: { name: string; index: number; color: string }[]
}

export interface TreeNode {
  id: string
  value: unknown
  left?: TreeNode
  right?: TreeNode
  parent?: string
  highlighted: boolean
  visiting: boolean
  visited: boolean
  depth: number
  x?: number
  y?: number
}

export interface GraphState {
  nodes: {
    id: string
    label: string
    value?: unknown
    visited: boolean
    visiting: boolean
    inQueue: boolean
    x?: number
    y?: number
  }[]
  edges: {
    from: string
    to: string
    weight?: number
    directed: boolean
    highlighted: boolean
  }[]
}

export interface StackFrame {
  frameId: string
  functionName: string
  line: number
  variables: VariableState[]
  returnValue?: unknown
  isActive: boolean
  depth: number
}

export interface HeapObject {
  address: string
  type: string
  size: number
  fields: Record<string, unknown>
  referencedBy: string[]
}

export type ExecutionEventType =
  | 'variable-declare'
  | 'variable-assign'
  | 'variable-read'
  | 'array-create'
  | 'array-read'
  | 'array-write'
  | 'array-swap'
  | 'array-compare'
  | 'pointer-create'
  | 'pointer-move'
  | 'pointer-dereference'
  | 'function-call'
  | 'function-return'
  | 'condition-eval'
  | 'loop-enter'
  | 'loop-iteration'
  | 'loop-exit'
  | 'recursion-enter'
  | 'recursion-base'
  | 'recursion-return'
  | 'tree-visit'
  | 'tree-compare'
  | 'tree-insert'
  | 'tree-delete'
  | 'graph-visit'
  | 'graph-enqueue'
  | 'graph-dequeue'
  | 'heap-alloc'
  | 'heap-free'

export interface ExecutionEvent {
  type: ExecutionEventType
  data: Record<string, unknown>
  highlight?: { line: number; column: number; length: number }
  explanation?: string
}

export interface ExecutionFrame {
  frameId: string
  step: number
  timestamp: number
  line: number
  column: number
  callStack: StackFrame[]
  variables: Map<string, VariableState>
  arrays: Map<string, ArrayState>
  tree?: TreeNode
  graph?: GraphState
  heap: Map<string, HeapObject>
  event: ExecutionEvent
  explanation?: string
}

export interface TraceSession {
  sessionId: string
  language: Language
  code: string
  totalSteps: number
  frames: ExecutionFrame[]
  summary: {
    patterns: AlgorithmPattern[]
    timeComplexity: ComplexityClass
    spaceComplexity: ComplexityClass
    functionCalls: number
    totalIterations: number
    maxRecursionDepth: number
    maxStackDepth: number
  }
}

export interface ParseResult {
  ast: ASTNode
  patterns: AlgorithmPattern[]
  timeComplexity: ComplexityClass
  spaceComplexity: ComplexityClass
  diagnostics: {
    line: number
    message: string
    severity: 'error' | 'warning' | 'info'
  }[]
}
