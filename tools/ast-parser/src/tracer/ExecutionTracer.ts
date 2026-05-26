import type {
  Language,
  TraceSession,
  ExecutionFrame,
  StackFrame,
  VariableState,
  ArrayState,
  HeapObject,
  ExecutionEvent,
  ExecutionEventType,
  ASTNode,
} from '../types'
import { ASTParserFactory } from '../parser/ASTParserFactory'

let _frameIdCounter = 0
function nextFrameId(): string {
  return `frame_${++_frameIdCounter}`
}

let _heapAddrCounter = 0
function nextAddress(): string {
  return `0x${((_heapAddrCounter += 4) * 256).toString(16).padStart(8, '0').toUpperCase()}`
}

/**
 * ExecutionTracer simulates code execution by walking the AST and emitting
 * ExecutionFrame snapshots at every meaningful step (assignment, loop
 * iteration, function call, etc.).
 *
 * This is a structural simulation, not a real interpreter. It produces
 * enough fidelity to drive the visualization engine while remaining
 * language-agnostic.
 */
export class ExecutionTracer {
  private frames: ExecutionFrame[] = []
  private callStack: StackFrame[] = []
  private globalVars = new Map<string, VariableState>()
  private globalArrays = new Map<string, ArrayState>()
  private heap = new Map<string, HeapObject>()
  private step = 0
  private functionCallCount = 0
  private totalIterations = 0
  private maxRecursionDepth = 0
  private maxStackDepth = 0

  static trace(code: string, language: Language): TraceSession {
    _frameIdCounter = 0
    _heapAddrCounter = 0
    const tracer = new ExecutionTracer()
    return tracer.run(code, language)
  }

  private run(code: string, language: Language): TraceSession {
    const sessionId = `session_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
    const parseResult = ASTParserFactory.parse(code, language)
    const { ast, patterns, timeComplexity, spaceComplexity } = parseResult

    // Push a global frame
    this.pushFrame('__global__', 0)
    this.walkAST(ast)
    this.popFrame()

    return {
      sessionId,
      language,
      code,
      totalSteps: this.step,
      frames: this.frames,
      summary: {
        patterns,
        timeComplexity,
        spaceComplexity,
        functionCalls: this.functionCallCount,
        totalIterations: this.totalIterations,
        maxRecursionDepth: this.maxRecursionDepth,
        maxStackDepth: this.maxStackDepth,
      },
    }
  }

  // ─── AST Walker ───────────────────────────────────────────────────────────

  private walkAST(node: ASTNode): void {
    switch (node.type) {
      case 'Program':
        for (const child of node.children) this.walkAST(child)
        break

      case 'FunctionDeclaration':
        this.handleFunctionDeclaration(node)
        break

      case 'VariableDeclaration':
        this.handleVariableDeclaration(node)
        break

      case 'AssignmentExpression':
      case 'AugmentedAssignment':
        this.handleAssignment(node)
        break

      case 'ForStatement':
        this.handleForLoop(node)
        break

      case 'WhileStatement':
      case 'DoWhileStatement':
        this.handleWhileLoop(node)
        break

      case 'IfStatement':
        this.handleIfStatement(node)
        break

      case 'CallExpression':
        this.handleCallExpression(node)
        break

      case 'ReturnStatement':
        this.handleReturn(node)
        break

      default:
        // Recurse into children for all other node types
        for (const child of node.children) this.walkAST(child)
        break
    }
  }

  // ─── Node Handlers ────────────────────────────────────────────────────────

  private handleFunctionDeclaration(node: ASTNode): void {
    const funcName = node.name ?? 'anonymous'
    this.emitEvent(
      'function-call',
      { functionName: funcName },
      node.position.line,
      `Entering function ${funcName}`,
    )
    this.functionCallCount++
    this.pushFrame(funcName, node.position.line)

    for (const child of node.children) this.walkAST(child)

    this.popFrame()
  }

  private handleVariableDeclaration(node: ASTNode): void {
    const varName = node.name ?? 'unknown'
    const rhs = node.value as string | undefined

    // Determine if this is an array
    const isArray = node.children.some((c) => c.type === 'ArrayExpression')

    if (isArray) {
      const arrState: ArrayState = {
        name: varName,
        elements: this.inferArrayElements(rhs ?? ''),
        pointers: [],
      }
      this.setArray(varName, arrState)

      // Emit heap allocation
      const addr = nextAddress()
      this.heap.set(addr, {
        address: addr,
        type: 'array',
        size: arrState.elements.length,
        fields: Object.fromEntries(arrState.elements.map((e, i) => [String(i), e.value])),
        referencedBy: [varName],
      })

      this.emitEvent(
        'array-create',
        { name: varName, size: arrState.elements.length, address: addr },
        node.position.line,
        `Array '${varName}' allocated with ${arrState.elements.length} elements`,
      )
    } else {
      const value = this.inferValue(rhs ?? '')
      const varState: VariableState = {
        name: varName,
        value,
        type: this.inferType(value),
        changed: true,
        isLoopCounter: false,
      }
      this.setVar(varName, varState)

      this.emitEvent(
        'variable-declare',
        { name: varName, value },
        node.position.line,
        `Variable '${varName}' = ${JSON.stringify(value)}`,
      )
    }
  }

  private handleAssignment(node: ASTNode): void {
    const nameNode = node.children.find((c) => c.type === 'Identifier')
    const varName = nameNode?.name ?? (node.name as string | undefined) ?? 'unknown'

    const prevState = this.getVar(varName)
    const newValue = prevState ? this.simulateTransition(prevState.value, node.type) : 0

    const varState: VariableState = {
      name: varName,
      value: newValue,
      type: this.inferType(newValue),
      changed: true,
      isLoopCounter: prevState?.isLoopCounter ?? false,
    }
    this.setVar(varName, varState)

    this.emitEvent(
      'variable-assign',
      { name: varName, oldValue: prevState?.value, newValue },
      node.position.line,
      `'${varName}' updated to ${JSON.stringify(newValue)}`,
    )
  }

  private handleForLoop(node: ASTNode): void {
    const loopVars = node.metadata.loopVariables ?? []
    const iterations = 5 // Simulate 5 iterations as a representative sample

    this.emitEvent('loop-enter', { variables: loopVars }, node.position.line, `Entering for loop`)

    for (let i = 0; i < iterations; i++) {
      this.totalIterations++

      // Update loop counter variables
      for (const loopVar of loopVars) {
        const varState: VariableState = {
          name: loopVar,
          value: i,
          type: 'number',
          changed: true,
          isLoopCounter: true,
        }
        this.setVar(loopVar, varState)
      }

      this.emitEvent(
        'loop-iteration',
        { iteration: i, variables: loopVars },
        node.position.line,
        `Loop iteration ${i}`,
      )

      // Walk body children
      for (const child of node.children) this.walkAST(child)

      // Mark all variables as not-changed after processing
      this.markVarsUnchanged()
    }

    this.emitEvent('loop-exit', { iterations }, node.position.line, `Exiting for loop after ${iterations} iterations`)
  }

  private handleWhileLoop(node: ASTNode): void {
    const iterations = 4

    this.emitEvent('loop-enter', {}, node.position.line, `Entering while loop`)

    for (let i = 0; i < iterations; i++) {
      this.totalIterations++

      this.emitEvent(
        'loop-iteration',
        { iteration: i },
        node.position.line,
        `While loop iteration ${i}`,
      )

      for (const child of node.children) this.walkAST(child)
      this.markVarsUnchanged()
    }

    this.emitEvent('loop-exit', { iterations }, node.position.line, `Exiting while loop`)
  }

  private handleIfStatement(node: ASTNode): void {
    this.emitEvent(
      'condition-eval',
      { condition: node.value },
      node.position.line,
      `Evaluating condition: ${node.value ?? 'if'}`,
    )

    for (const child of node.children) this.walkAST(child)
  }

  private handleCallExpression(node: ASTNode): void {
    const callName = node.name ?? 'unknown'
    this.functionCallCount++

    // Special handling for common operations
    if (callName === 'append' || callName === 'push') {
      this.emitEvent('array-write', { method: callName }, node.position.line, `Appending to array`)
    } else if (callName === 'pop' || callName === 'popleft' || callName === 'shift') {
      this.emitEvent('array-read', { method: callName }, node.position.line, `Reading from array`)
    } else if (callName === 'sort' || callName === 'sorted') {
      this.emitEvent(
        'array-write',
        { method: 'sort' },
        node.position.line,
        `Sorting array`,
      )
    } else {
      this.emitEvent(
        'function-call',
        { name: callName },
        node.position.line,
        `Calling ${callName}()`,
      )
    }
  }

  private handleReturn(node: ASTNode): void {
    const frame = this.getActiveFrame()
    this.emitEvent(
      'function-return',
      { functionName: frame?.functionName, value: node.value },
      node.position.line,
      `Returning from ${frame?.functionName ?? 'function'}`,
    )
  }

  // ─── Frame Management ─────────────────────────────────────────────────────

  private pushFrame(functionName: string, line: number): void {
    const frame: StackFrame = {
      frameId: nextFrameId(),
      functionName,
      line,
      variables: [],
      isActive: true,
      depth: this.callStack.length,
    }

    // Deactivate previous active frame
    if (this.callStack.length > 0) {
      this.callStack[this.callStack.length - 1].isActive = false
    }

    this.callStack.push(frame)

    if (this.callStack.length > this.maxStackDepth) {
      this.maxStackDepth = this.callStack.length
    }

    if (functionName !== '__global__') {
      const depth = this.callStack.filter((f) => f.functionName !== '__global__').length
      if (depth > this.maxRecursionDepth) this.maxRecursionDepth = depth
    }
  }

  private popFrame(): void {
    const frame = this.callStack.pop()
    if (frame) {
      this.emitEvent(
        'function-return',
        { functionName: frame.functionName },
        frame.line,
        `Popped frame: ${frame.functionName}`,
      )
    }
    // Re-activate top frame
    if (this.callStack.length > 0) {
      this.callStack[this.callStack.length - 1].isActive = true
    }
  }

  private getActiveFrame(): StackFrame | undefined {
    return this.callStack.length > 0 ? this.callStack[this.callStack.length - 1] : undefined
  }

  // ─── Variable Helpers ─────────────────────────────────────────────────────

  private setVar(name: string, state: VariableState): void {
    const frame = this.getActiveFrame()
    if (frame) {
      const existing = frame.variables.findIndex((v) => v.name === name)
      if (existing >= 0) {
        frame.variables[existing] = state
      } else {
        frame.variables.push(state)
      }
    }
    this.globalVars.set(name, state)
  }

  private getVar(name: string): VariableState | undefined {
    // Check active frame first, then global scope
    const frame = this.getActiveFrame()
    if (frame) {
      const local = frame.variables.find((v) => v.name === name)
      if (local) return local
    }
    return this.globalVars.get(name)
  }

  private setArray(name: string, state: ArrayState): void {
    this.globalArrays.set(name, state)
  }

  private markVarsUnchanged(): void {
    for (const [, v] of this.globalVars) {
      v.changed = false
    }
    const frame = this.getActiveFrame()
    if (frame) {
      for (const v of frame.variables) v.changed = false
    }
  }

  // ─── Event Emission ───────────────────────────────────────────────────────

  private emitEvent(
    type: ExecutionEventType,
    data: Record<string, unknown>,
    line: number,
    explanation?: string,
  ): void {
    this.step++

    const event: ExecutionEvent = {
      type,
      data,
      highlight: { line, column: 0, length: 80 },
      explanation,
    }

    // Build variable snapshot for this frame
    const variables = new Map<string, VariableState>()
    for (const [k, v] of this.globalVars) variables.set(k, { ...v })
    const frame = this.getActiveFrame()
    if (frame) {
      for (const v of frame.variables) variables.set(v.name, { ...v })
    }

    const arrays = new Map<string, ArrayState>()
    for (const [k, v] of this.globalArrays) arrays.set(k, { ...v, elements: v.elements.map((e) => ({ ...e })) })

    const heap = new Map<string, HeapObject>()
    for (const [k, v] of this.heap) heap.set(k, { ...v })

    const execFrame: ExecutionFrame = {
      frameId: nextFrameId(),
      step: this.step,
      timestamp: Date.now(),
      line,
      column: 0,
      callStack: this.callStack.map((f) => ({
        ...f,
        variables: f.variables.map((v) => ({ ...v })),
      })),
      variables,
      arrays,
      heap,
      event,
      explanation,
    }

    this.frames.push(execFrame)
  }

  // ─── Value Inference ──────────────────────────────────────────────────────

  private inferValue(rhs: string): unknown {
    if (rhs === 'None' || rhs === 'null') return null
    if (rhs === 'True' || rhs === 'true') return true
    if (rhs === 'False' || rhs === 'false') return false
    if (/^-?\d+(\.\d+)?$/.test(rhs)) return Number(rhs)
    if (rhs.startsWith('"') || rhs.startsWith("'")) return rhs.slice(1, -1)
    if (rhs === '[]' || rhs === 'list()') return []
    if (rhs === '{}' || rhs === 'dict()' || rhs === 'set()') return {}
    return 0 // default numeric
  }

  private inferType(value: unknown): VariableState['type'] {
    if (value === null) return 'null'
    if (value === undefined) return 'undefined'
    if (typeof value === 'boolean') return 'boolean'
    if (typeof value === 'number') return 'number'
    if (typeof value === 'string') return 'string'
    if (Array.isArray(value)) return 'array'
    return 'object'
  }

  private inferArrayElements(rhs: string): ArrayState['elements'] {
    // Try to parse simple arrays like [1, 2, 3] or [0] * 5
    const multiplyMatch = rhs.match(/\[(\d+)\]\s*\*\s*(\d+)/)
    if (multiplyMatch) {
      const val = Number(multiplyMatch[1])
      const count = Number(multiplyMatch[2])
      return Array.from({ length: count }, () => ({
        value: val,
        highlighted: false,
        swapped: false,
        comparing: false,
      }))
    }

    const literalMatch = rhs.match(/^\[([^\]]+)\]$/)
    if (literalMatch) {
      return literalMatch[1]
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean)
        .map((v) => ({
          value: /^\d+$/.test(v) ? Number(v) : v.replace(/['"]/g, ''),
          highlighted: false,
          swapped: false,
          comparing: false,
        }))
    }

    // Empty array
    return []
  }

  private simulateTransition(prevValue: unknown, nodeType: string): unknown {
    // Augmented assignment: simulate increment/decrement
    if (nodeType === 'AugmentedAssignment') {
      if (typeof prevValue === 'number') return prevValue + 1
      return prevValue
    }
    // Plain assignment: return prev value (structural simulation)
    return prevValue
  }
}
