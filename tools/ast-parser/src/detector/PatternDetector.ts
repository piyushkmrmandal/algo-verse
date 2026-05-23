import type { ASTNode, AlgorithmPattern } from '../types'

interface PatternRule {
  pattern: AlgorithmPattern
  detect: (ast: ASTNode) => boolean
  confidence: number
}

export class PatternDetector {
  private static rules: PatternRule[] = [
    {
      pattern: 'two-pointer',
      detect: (ast) => PatternDetector.hasTwoPointerPattern(ast),
      confidence: 0.85,
    },
    {
      pattern: 'sliding-window',
      detect: (ast) => PatternDetector.hasSlidingWindowPattern(ast),
      confidence: 0.82,
    },
    {
      pattern: 'fast-slow-pointer',
      detect: (ast) => PatternDetector.hasFastSlowPointer(ast),
      confidence: 0.88,
    },
    {
      pattern: 'tree-bfs',
      detect: (ast) => PatternDetector.hasQueueUsage(ast) && PatternDetector.hasTreeNodeAccess(ast),
      confidence: 0.87,
    },
    {
      pattern: 'graph-bfs',
      detect: (ast) => PatternDetector.hasQueueUsage(ast) && PatternDetector.hasVisitedSet(ast),
      confidence: 0.84,
    },
    {
      pattern: 'tree-dfs',
      detect: (ast) => PatternDetector.hasStackOrRecursion(ast) && PatternDetector.hasTreeNodeAccess(ast),
      confidence: 0.83,
    },
    {
      pattern: 'graph-dfs',
      detect: (ast) => PatternDetector.hasStackOrRecursion(ast) && PatternDetector.hasVisitedSet(ast),
      confidence: 0.81,
    },
    {
      pattern: 'dp-0-1-knapsack',
      detect: (ast) => PatternDetector.hasNestedLoopsWithDP(ast) && PatternDetector.hasKnapsackPattern(ast),
      confidence: 0.80,
    },
    {
      pattern: 'dp-fibonacci',
      detect: (ast) => PatternDetector.hasFibonacciDPPattern(ast),
      confidence: 0.83,
    },
    {
      pattern: 'dp-lcs',
      detect: (ast) => PatternDetector.hasLCSPattern(ast),
      confidence: 0.80,
    },
    {
      pattern: 'backtracking',
      detect: (ast) => PatternDetector.hasRecursiveBacktracking(ast),
      confidence: 0.85,
    },
    {
      pattern: 'modified-binary-search',
      detect: (ast) => PatternDetector.hasBinarySearchPattern(ast),
      confidence: 0.90,
    },
    {
      pattern: 'monotonic-stack',
      detect: (ast) => PatternDetector.hasMonotonicStack(ast),
      confidence: 0.88,
    },
    {
      pattern: 'union-find',
      detect: (ast) => PatternDetector.hasUnionFindPattern(ast),
      confidence: 0.92,
    },
    {
      pattern: 'prefix-sum',
      detect: (ast) => PatternDetector.hasPrefixSumPattern(ast),
      confidence: 0.86,
    },
    {
      pattern: 'merge-intervals',
      detect: (ast) => PatternDetector.hasMergeIntervalsPattern(ast),
      confidence: 0.84,
    },
    {
      pattern: 'topological-sort',
      detect: (ast) => PatternDetector.hasTopologicalSortPattern(ast),
      confidence: 0.87,
    },
    {
      pattern: 'divide-and-conquer',
      detect: (ast) => PatternDetector.hasDivideAndConquer(ast),
      confidence: 0.78,
    },
  ]

  static detect(ast: ASTNode): AlgorithmPattern[] {
    const results: { pattern: AlgorithmPattern; confidence: number }[] = []

    for (const rule of this.rules) {
      try {
        if (rule.detect(ast)) {
          results.push({ pattern: rule.pattern, confidence: rule.confidence })
        }
      } catch {
        // Ignore detection errors for individual rules
      }
    }

    const filtered = results
      .filter((r) => r.confidence > 0.6)
      .sort((a, b) => b.confidence - a.confidence)
      .map((r) => r.pattern)

    return filtered.length > 0 ? filtered : ['unknown']
  }

  // ─── Detection helpers ────────────────────────────────────────────────────

  private static hasQueueUsage(ast: ASTNode): boolean {
    const nodes = this.flatten(ast)

    // Detect deque, Queue, collections.deque imports
    const hasDequeImport = nodes
      .filter((n) => n.type === 'ImportStatement')
      .some((n) => {
        const text = (n.value as string) ?? ''
        return /deque|Queue|queue/.test(text)
      })

    if (hasDequeImport) return true

    // Detect .append and .popleft / .pop(0) / dequeue calls co-existing
    const callNodes = this.findByType(ast, 'CallExpression')
    const callNames = callNodes.map((n) => n.name ?? '').filter(Boolean)
    const hasAppend = callNames.some((n) => n === 'append' || n === 'enqueue' || n === 'push')
    const hasPopleft = callNames.some(
      (n) =>
        n === 'popleft' ||
        n === 'dequeue' ||
        n === 'shift' ||
        callNodes.some(
          (c) =>
            c.name === 'pop' &&
            c.children.some((ch) => ch.type === 'Literal' && ch.value === 0),
        ),
    )

    // Detect identifier named 'queue' or 'q' with deque/list assignment
    const hasQueueVar = this.hasIdentifierMatching(ast, /^(queue|q|bfs_queue|level_queue)$/)

    return (hasAppend && hasPopleft) || hasQueueVar
  }

  private static hasTwoPointerPattern(ast: ASTNode): boolean {
    const identifiers = this.findByType(ast, 'Identifier').map((n) => n.name ?? '')

    // left/right or start/end or lo/hi pointer names
    const hasLeftRight =
      identifiers.some((n) => /^(left|l|lo|start|begin|i)$/.test(n)) &&
      identifiers.some((n) => /^(right|r|hi|end|j|last)$/.test(n))

    if (!hasLeftRight) return false

    // They should converge inside a while loop
    const whileLoops = this.findByType(ast, 'WhileStatement')
    return whileLoops.length > 0
  }

  private static hasSlidingWindowPattern(ast: ASTNode): boolean {
    const identifiers = this.findByType(ast, 'Identifier').map((n) => n.name ?? '')

    // window_start/end or left/right with max/min window size tracking
    const hasWindowVars =
      identifiers.some((n) => /^(window_start|window_left|start|left|l)$/.test(n)) &&
      identifiers.some((n) => /^(window_end|window_right|end|right|r)$/.test(n)) &&
      identifiers.some((n) => /^(max_len|min_len|max_size|window_size|result|ans|res)$/.test(n))

    // OR: for loop iterating an array with an inner while loop shrinking the window
    const forLoops = this.findByType(ast, 'ForStatement')
    const whileLoops = this.findByType(ast, 'WhileStatement')
    const hasNestedForWhile = forLoops.some((f) =>
      this.findByType(f, 'WhileStatement').length > 0,
    )

    return hasWindowVars || (hasNestedForWhile && whileLoops.length > 0)
  }

  private static hasFastSlowPointer(ast: ASTNode): boolean {
    // Look for fast/slow named identifiers
    const hasFastSlow = this.hasIdentifierMatching(ast, /^(fast|slow|hare|tortoise)$/)
    if (hasFastSlow) return true

    // Look for patterns like fast = fast.next.next or fast += 2
    const augAssignments = this.findByType(ast, 'AugmentedAssignment')
    const hasFastIncrement = augAssignments.some((n) => {
      const nameNode = n.children.find((c) => c.type === 'Identifier')
      const valueNode = n.children.find((c) => c.type === 'Literal' && c.value === 2)
      return nameNode && /fast/.test(nameNode.name ?? '') && valueNode
    })

    return hasFastIncrement
  }

  private static hasTreeNodeAccess(ast: ASTNode): boolean {
    const identifiers = this.findByType(ast, 'Identifier').map((n) => n.name ?? '')
    return (
      identifiers.some((n) => /^(root|node|TreeNode|tree)$/.test(n)) &&
      identifiers.some((n) => /^(left|right|val|value)$/.test(n))
    )
  }

  private static hasVisitedSet(ast: ASTNode): boolean {
    return this.hasIdentifierMatching(ast, /^(visited|seen|explored)$/)
  }

  private static hasStackOrRecursion(ast: ASTNode): boolean {
    const hasFunctionWithSelfCall = this.hasRecursiveFunctionCall(ast)
    const hasStackVar = this.hasIdentifierMatching(ast, /^(stack|stk|dfs_stack)$/)
    return hasFunctionWithSelfCall || hasStackVar
  }

  private static hasNestedLoopsWithDP(ast: ASTNode): boolean {
    // Check for 2D list/array named dp + nested for loops
    const hasDpVar = this.hasIdentifierMatching(ast, /^(dp|memo|cache|table|f)$/)
    if (!hasDpVar) return false

    const forLoops = this.findByType(ast, 'ForStatement')
    if (forLoops.length < 2) return false

    // Check nesting: any for loop contains another for loop
    return forLoops.some((f) => this.findByType(f, 'ForStatement').length > 0)
  }

  private static hasKnapsackPattern(ast: ASTNode): boolean {
    // capacity/weight/value identifiers suggest 0-1 knapsack
    const identifiers = this.findByType(ast, 'Identifier').map((n) => n.name ?? '')
    return (
      identifiers.some((n) => /^(capacity|cap|W|maxWeight)$/.test(n)) &&
      identifiers.some((n) => /^(weight|weights|wt|w)$/.test(n)) &&
      identifiers.some((n) => /^(value|values|val|profit|profits)$/.test(n))
    )
  }

  private static hasFibonacciDPPattern(ast: ASTNode): boolean {
    const hasDpVar = this.hasIdentifierMatching(ast, /^(dp|memo|fib|f)$/)
    if (!hasDpVar) return false

    // dp[i] = dp[i-1] + dp[i-2] pattern: subscripts with i-1, i-2
    const subscripts = this.findByType(ast, 'SubscriptExpression')
    const hasI1 = subscripts.some((s) =>
      s.children.some(
        (c) => c.type === 'BinaryExpression' && String(c.value ?? '').includes('-'),
      ),
    )

    return hasI1 || this.hasIdentifierMatching(ast, /^(fibonacci|fib_)/)
  }

  private static hasLCSPattern(ast: ASTNode): boolean {
    // LCS: 2D dp + comparing characters from two strings
    const has2DDP = this.hasNestedLoopsWithDP(ast)
    const identifiers = this.findByType(ast, 'Identifier').map((n) => n.name ?? '')
    const hasTwoStringVars =
      identifiers.some((n) => /^(s1|text1|word1|str1|a)$/.test(n)) &&
      identifiers.some((n) => /^(s2|text2|word2|str2|b)$/.test(n))
    return has2DDP && hasTwoStringVars
  }

  private static hasRecursiveBacktracking(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')

    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue

      // Recursive call within the function
      const calls = this.findByType(func, 'CallExpression')
      const hasSelfCall = calls.some((c) => c.name === funcName)
      if (!hasSelfCall) continue

      // For loop inside the recursive function
      const hasForLoop =
        this.findByType(func, 'ForStatement').length > 0 ||
        this.findByType(func, 'WhileStatement').length > 0

      // Undo operation: identifiers like "path.pop()", "result.pop()", "remove", "undo"
      const callNames = calls.map((c) => c.name ?? '')
      const hasUndo =
        callNames.some((n) => /^(pop|remove|undo|delete)$/.test(n)) ||
        this.hasIdentifierMatching(func, /^(path|choices|current|used)$/)

      if (hasSelfCall && hasForLoop && hasUndo) return true
    }

    return false
  }

  private static hasBinarySearchPattern(ast: ASTNode): boolean {
    const whileLoops = this.findByType(ast, 'WhileStatement')
    for (const loop of whileLoops) {
      // Condition contains left <= right (or lo <= hi)
      const hasBoundaryCondition = this.hasIdentifierMatching(loop, /^(left|lo|low|l|start)$/) &&
        this.hasIdentifierMatching(loop, /^(right|hi|high|r|end)$/)

      if (!hasBoundaryCondition) continue

      // mid = (left + right) // 2 or (left + right) >> 1
      const hasMidCalc = this.hasIdentifierMatching(loop, /^(mid|middle|m)$/)
      if (hasMidCalc) return true
    }

    // Also check for loop variant: for _ in range(...)  with mid
    const hasMid = this.hasIdentifierMatching(ast, /^(mid|middle)$/)
    const hasLeftRight =
      this.hasIdentifierMatching(ast, /^(left|lo|low)$/) &&
      this.hasIdentifierMatching(ast, /^(right|hi|high)$/)
    return hasMid && hasLeftRight
  }

  private static hasMonotonicStack(ast: ASTNode): boolean {
    // Stack variable + while loop checking stack[-1] / stack.peek() comparison
    const hasStackVar = this.hasIdentifierMatching(ast, /^(stack|stk|mono_stack)$/)
    if (!hasStackVar) return false

    // While loop inside a for loop with stack comparison
    const forLoops = this.findByType(ast, 'ForStatement')
    const hasInnerWhile = forLoops.some(
      (f) => this.findByType(f, 'WhileStatement').length > 0,
    )

    if (!hasInnerWhile) return false

    // Comparison in while: stack[-1] > arr[i] or similar
    const calls = this.findByType(ast, 'CallExpression')
    const hasPopOnStack = calls.some((c) => c.name === 'pop' || c.name === 'append')

    return hasPopOnStack
  }

  private static hasUnionFindPattern(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    const funcNames = funcDecls.map((f) => f.name ?? '')

    // Needs both find and union functions
    const hasFind = funcNames.some((n) => /^(find|Find)$/.test(n))
    const hasUnion = funcNames.some((n) => /^(union|Union|merge)$/.test(n))
    if (hasFind && hasUnion) return true

    // Alternatively: parent array + path compression pattern
    const hasParentArray = this.hasIdentifierMatching(ast, /^(parent|rank|uf|root)$/)
    return hasParentArray && (hasFind || funcNames.length > 0)
  }

  private static hasPrefixSumPattern(ast: ASTNode): boolean {
    // prefix/cumulative sum array declaration + range query
    const hasPrefix = this.hasIdentifierMatching(
      ast,
      /^(prefix|prefix_sum|cum_sum|running_sum|cumulative)$/,
    )
    if (hasPrefix) return true

    // Construction: for loop with sum accumulation into array
    const forLoops = this.findByType(ast, 'ForStatement')
    const hasAccumulationLoop = forLoops.some((f) => {
      const augAssigns = this.findByType(f, 'AugmentedAssignment')
      const hasSum = augAssigns.some((a) => {
        const name = a.children.find((c) => c.type === 'Identifier')?.name ?? ''
        return /sum|total|acc/.test(name)
      })
      const hasArrayWrite = this.findByType(f, 'SubscriptExpression').length > 0
      return hasSum || hasArrayWrite
    })

    return hasAccumulationLoop
  }

  private static hasMergeIntervalsPattern(ast: ASTNode): boolean {
    // Sort by start + overlap check (start <= prev_end)
    const hasSortCall = this.findByType(ast, 'CallExpression').some(
      (c) => c.name === 'sort' || c.name === 'sorted',
    )
    if (!hasSortCall) return false

    const hasIntervalVars = this.hasIdentifierMatching(
      ast,
      /^(intervals|interval|start|end|merged|result)$/,
    )
    return hasIntervalVars
  }

  private static hasTopologicalSortPattern(ast: ASTNode): boolean {
    // In-degree array + BFS-style processing
    const hasInDegree = this.hasIdentifierMatching(ast, /^(in_degree|indegree|in_deg|degree)$/)
    const hasGraph = this.hasIdentifierMatching(
      ast,
      /^(graph|adj|adjacency|neighbors|edges)$/,
    )
    const hasQueueOrStack =
      this.hasQueueUsage(ast) ||
      this.hasIdentifierMatching(ast, /^(stack|order|result|topo)$/)
    return hasInDegree && hasGraph && hasQueueOrStack
  }

  private static hasDivideAndConquer(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue

      const calls = this.findByType(func, 'CallExpression')
      const selfCalls = calls.filter((c) => c.name === funcName)

      // Two or more recursive calls with halved input (divide-and-conquer)
      if (selfCalls.length >= 2) {
        // Check for mid-point calculation
        const hasMid = this.hasIdentifierMatching(func, /^(mid|middle|m|pivot)$/)
        if (hasMid) return true
      }
    }
    return false
  }

  private static hasRecursiveFunctionCall(ast: ASTNode): boolean {
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')
    for (const func of funcDecls) {
      const funcName = func.name
      if (!funcName) continue
      const calls = this.findByType(func, 'CallExpression')
      if (calls.some((c) => c.name === funcName)) return true
    }
    return false
  }

  // ─── Public Utility Methods ───────────────────────────────────────────────

  static flatten(node: ASTNode): ASTNode[] {
    const result: ASTNode[] = [node]
    for (const child of node.children) {
      result.push(...this.flatten(child))
    }
    return result
  }

  static findByType(node: ASTNode, type: ASTNode['type']): ASTNode[] {
    return this.flatten(node).filter((n) => n.type === type)
  }

  static hasIdentifierMatching(node: ASTNode, pattern: RegExp): boolean {
    return this.findByType(node, 'Identifier').some(
      (n) => n.name !== undefined && pattern.test(n.name),
    )
  }
}
