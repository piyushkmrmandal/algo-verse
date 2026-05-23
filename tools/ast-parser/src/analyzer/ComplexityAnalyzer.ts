import type { ASTNode, ComplexityClass } from '../types'

export class ComplexityAnalyzer {
  static analyze(ast: ASTNode): { time: ComplexityClass; space: ComplexityClass } {
    const loopDepth = this.getMaxLoopNestingDepth(ast)
    const hasRecursion = this.hasRecursion(ast)
    const recursionPattern = hasRecursion ? this.analyzeRecursionPattern(ast) : null
    const hasSorting = this.callsSortingAlgorithm(ast)
    const hasBinarySearch = this.hasBinarySearchPattern(ast)
    const hasExponentialBranching = this.hasExponentialBranching(ast)

    const time = this.inferTimeComplexity(
      loopDepth,
      hasRecursion,
      recursionPattern,
      hasSorting,
      hasBinarySearch,
      hasExponentialBranching,
    )
    const space = this.inferSpaceComplexity(ast, hasRecursion, this.getMaxRecursionDepth(ast))

    return { time, space }
  }

  // ─── Loop Depth ───────────────────────────────────────────────────────────

  private static getMaxLoopNestingDepth(ast: ASTNode): number {
    return this.computeLoopDepth(ast, 0)
  }

  private static computeLoopDepth(node: ASTNode, currentDepth: number): number {
    const isLoop =
      node.type === 'ForStatement' ||
      node.type === 'WhileStatement' ||
      node.type === 'DoWhileStatement'

    const depth = isLoop ? currentDepth + 1 : currentDepth
    let maxDepth = depth

    for (const child of node.children) {
      const childDepth = this.computeLoopDepth(child, depth)
      if (childDepth > maxDepth) maxDepth = childDepth
    }

    return maxDepth
  }

  // ─── Recursion Detection ──────────────────────────────────────────────────

  private static hasRecursion(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue
      const calls = this.findByType(func, 'CallExpression')
      if (calls.some((c) => c.name === funcName)) return true
    }
    return false
  }

  private static analyzeRecursionPattern(
    ast: ASTNode,
  ): 'linear' | 'divide-conquer' | 'exponential' | 'unknown' {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')

    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue

      const calls = this.findByType(func, 'CallExpression')
      const selfCalls = calls.filter((c) => c.name === funcName)
      if (selfCalls.length === 0) continue

      if (selfCalls.length >= 2) {
        // Two recursive calls → exponential unless there is a halving of input
        const hasMidComputation = this.hasIdentifierMatching(
          func,
          /^(mid|middle|m|pivot|half)$/,
        )
        if (hasMidComputation) return 'divide-conquer'
        return 'exponential'
      }

      if (selfCalls.length === 1) {
        // Single recursive call — check if input is halved (binary recursion) or decremented
        const hasMidComputation = this.hasIdentifierMatching(
          func,
          /^(mid|middle|half)$/,
        )
        if (hasMidComputation) return 'divide-conquer'
        return 'linear'
      }
    }

    return 'unknown'
  }

  // ─── Sorting ──────────────────────────────────────────────────────────────

  private static callsSortingAlgorithm(ast: ASTNode): boolean {
    const calls = this.findByType(ast, 'CallExpression')
    const sortCallNames = ['sort', 'sorted', 'Arrays.sort', 'Collections.sort', 'std::sort']
    return calls.some((c) => c.name !== undefined && sortCallNames.includes(c.name))
  }

  // ─── Binary Search ────────────────────────────────────────────────────────

  private static hasBinarySearchPattern(ast: ASTNode): boolean {
    const whileLoops = this.findByType(ast, 'WhileStatement')
    for (const loop of whileLoops) {
      const hasLeft = this.hasIdentifierMatching(loop, /^(left|lo|low|l|start)$/)
      const hasRight = this.hasIdentifierMatching(loop, /^(right|hi|high|r|end)$/)
      const hasMid = this.hasIdentifierMatching(loop, /^(mid|middle|m)$/)
      if (hasLeft && hasRight && hasMid) return true
    }
    return false
  }

  // ─── Exponential Branching ────────────────────────────────────────────────

  private static hasExponentialBranching(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue

      const calls = this.findByType(func, 'CallExpression')
      const selfCalls = calls.filter((c) => c.name === funcName)

      // 2+ recursive calls without divide-and-conquer halving = exponential
      if (selfCalls.length >= 2) {
        const hasMid = this.hasIdentifierMatching(func, /^(mid|middle|pivot|half)$/)
        if (!hasMid) return true
      }
    }
    return false
  }

  // ─── Recursion Depth ─────────────────────────────────────────────────────

  private static getMaxRecursionDepth(
    ast: ASTNode,
  ): 'constant' | 'linear' | 'logarithmic' {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue

      const calls = this.findByType(func, 'CallExpression')
      const selfCalls = calls.filter((c) => c.name === funcName)
      if (selfCalls.length === 0) continue

      // Check for logarithmic recursion: input is halved on each call
      const hasMid = this.hasIdentifierMatching(func, /^(mid|middle|half|pivot)$/)
      if (hasMid) return 'logarithmic'

      return 'linear'
    }
    return 'constant'
  }

  // ─── Time Complexity Inference ────────────────────────────────────────────

  private static inferTimeComplexity(
    loopDepth: number,
    hasRecursion: boolean,
    recursionPattern: string | null,
    hasSorting: boolean,
    hasBinarySearch: boolean,
    hasExponentialBranching: boolean,
  ): ComplexityClass {
    if (hasExponentialBranching) return 'O(2^n)'
    if (loopDepth === 0 && !hasRecursion && !hasSorting) return 'O(1)'
    if (hasBinarySearch && loopDepth <= 1 && !hasRecursion) return 'O(log n)'
    if (recursionPattern === 'divide-conquer' || hasSorting) return 'O(n log n)'
    if (recursionPattern === 'exponential') return 'O(2^n)'
    if (loopDepth === 1 || (hasRecursion && recursionPattern === 'linear')) return 'O(n)'
    if (loopDepth === 2) return 'O(n²)'
    if (loopDepth === 3) return 'O(n³)'
    if (loopDepth > 3) return 'O(n³)' // conservatively report cubic for very deep nesting
    return 'O(n)'
  }

  // ─── Space Complexity Inference ───────────────────────────────────────────

  private static inferSpaceComplexity(
    ast: ASTNode,
    hasRecursion: boolean,
    recursionDepth: 'constant' | 'linear' | 'logarithmic',
  ): ComplexityClass {
    const allocatesN2Array = this.allocatesN2SizedArray(ast)
    const allocatesNArray = this.allocatesNSizedArray(ast)

    if (allocatesN2Array) return 'O(n²)'
    if (allocatesNArray) return 'O(n)'
    if (hasRecursion) {
      if (recursionDepth === 'logarithmic') return 'O(log n)'
      return 'O(n)'
    }
    return 'O(1)'
  }

  private static allocatesNSizedArray(ast: ASTNode): boolean {
    // Look for array/list declarations with variable size: [0] * n, new int[n], vector<int>(n), etc.
    const variableDecls = this.findByType(ast, 'VariableDeclaration')
    for (const decl of variableDecls) {
      // Check for array-like children whose size depends on a variable
      const arrayChildren = decl.children.filter(
        (c) => c.type === 'ArrayExpression' || c.type === 'CallExpression',
      )
      for (const arr of arrayChildren) {
        // Look for an identifier (n, size, length) as array dimension
        const hasDynamicSize = arr.children.some(
          (c) =>
            c.type === 'Identifier' &&
            /^(n|size|length|len|m|k|capacity)$/.test(c.name ?? ''),
        )
        if (hasDynamicSize) return true
      }

      // Detect list comprehension or fill with variable size
      const hasDPVar = /^(dp|memo|cache|prefix|result|ans|visited|parent)$/.test(
        decl.name ?? '',
      )
      if (hasDPVar) return true
    }

    // Check for set/map usage (implicitly O(n) space)
    const calls = this.findByType(ast, 'CallExpression')
    const hasSetOrMap = calls.some((c) =>
      /^(set|dict|map|Set|Map|HashMap|HashSet|unordered_map|unordered_set)$/.test(c.name ?? ''),
    )
    return hasSetOrMap
  }

  private static allocatesN2SizedArray(ast: ASTNode): boolean {
    // 2D dp array: dp = [[0] * n for _ in range(n)] or new int[n][n]
    const variableDecls = this.findByType(ast, 'VariableDeclaration')
    for (const decl of variableDecls) {
      const hasDPName = /^(dp|table|grid|matrix|dist|cost|memo2d)$/.test(decl.name ?? '')
      if (!hasDPName) continue

      // Look for nested array expressions or double dimension
      const nestedArrays = this.findByType(decl, 'ArrayExpression')
      if (nestedArrays.length >= 2) return true

      // Python: [[...] for _ in range(...)] — array inside list comprehension
      const forInDecl = this.findByType(decl, 'ForStatement')
      const hasNestedFor = forInDecl.some(
        (f) => this.findByType(f, 'ArrayExpression').length > 0,
      )
      if (hasNestedFor) return true
    }
    return false
  }

  // ─── Utility ──────────────────────────────────────────────────────────────

  private static flatten(node: ASTNode): ASTNode[] {
    const result: ASTNode[] = [node]
    for (const child of node.children) {
      result.push(...this.flatten(child))
    }
    return result
  }

  private static findByType(node: ASTNode, type: ASTNode['type']): ASTNode[] {
    return this.flatten(node).filter((n) => n.type === type)
  }

  private static hasIdentifierMatching(node: ASTNode, pattern: RegExp): boolean {
    return this.findByType(node, 'Identifier').some(
      (n) => n.name !== undefined && pattern.test(n.name),
    )
  }
}
