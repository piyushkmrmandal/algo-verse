import type { Language, ASTNode, ParseResult, ASTNodeType, AlgorithmPattern, ComplexityClass } from '../types'
import { PatternDetector } from '../detector/PatternDetector'
import { ComplexityAnalyzer } from '../analyzer/ComplexityAnalyzer'

let _idCounter = 0
function nextId(): string {
  return `node_${++_idCounter}`
}

function makeNode(
  type: ASTNodeType,
  position = { line: 1, column: 0, offset: 0 },
  endPosition = { line: 1, column: 0, offset: 0 },
  overrides: Partial<ASTNode> = {},
): ASTNode {
  return {
    id: nextId(),
    type,
    children: [],
    position,
    endPosition,
    metadata: {
      isLoop: false,
      isRecursive: false,
      isConditional: false,
      nestedDepth: 0,
    },
    ...overrides,
  }
}

/**
 * ASTParserFactory produces ParseResult objects from source code strings.
 *
 * The implementation performs lightweight lexical analysis to build a
 * structural AST that is rich enough for PatternDetector and
 * ComplexityAnalyzer without requiring a full language grammar.
 */
export class ASTParserFactory {
  static parse(code: string, language: Language): ParseResult {
    _idCounter = 0
    const ast = this.buildAST(code, language)

    const patterns = PatternDetector.detect(ast)
    const { time: timeComplexity, space: spaceComplexity } = ComplexityAnalyzer.analyze(ast)

    const diagnostics = this.lint(ast, code)

    return { ast, patterns, timeComplexity, spaceComplexity, diagnostics }
  }

  // ─── AST Builder ─────────────────────────────────────────────────────────

  private static buildAST(code: string, language: Language): ASTNode {
    const lines = code.split('\n')
    const root = makeNode('Program', { line: 1, column: 0, offset: 0 }, {
      line: lines.length,
      column: lines[lines.length - 1]?.length ?? 0,
      offset: code.length,
    })

    let offset = 0
    for (let lineIdx = 0; lineIdx < lines.length; lineIdx++) {
      const line = lines[lineIdx]
      const trimmed = line.trimStart()
      const lineNum = lineIdx + 1
      const col = line.length - trimmed.length
      const pos = { line: lineNum, column: col, offset }
      const endPos = { line: lineNum, column: line.length, offset: offset + line.length }

      const node = this.classifyLine(trimmed, lineNum, col, pos, endPos, language)
      if (node) root.children.push(node)

      offset += line.length + 1 // +1 for newline
    }

    this.annotateNestedDepth(root, 0)
    return root
  }

  private static classifyLine(
    trimmed: string,
    lineNum: number,
    col: number,
    pos: { line: number; column: number; offset: number },
    endPos: { line: number; column: number; offset: number },
    language: Language,
  ): ASTNode | null {
    if (!trimmed) return null

    // Comments
    if (
      trimmed.startsWith('#') ||
      trimmed.startsWith('//') ||
      trimmed.startsWith('/*') ||
      trimmed.startsWith('*')
    ) {
      return makeNode('Comment', pos, endPos, { value: trimmed })
    }

    // Import statements
    if (/^(import |from |#include |using )/.test(trimmed)) {
      return makeNode('ImportStatement', pos, endPos, { value: trimmed })
    }

    // Function/method declarations
    const fnMatch = trimmed.match(
      /^(?:def |function |func |public |private |protected |static |void |int |string |bool |auto )*(\w+)\s*\(([^)]*)\)/,
    )
    if (fnMatch && !trimmed.startsWith('return') && !trimmed.startsWith('if')) {
      return makeNode('FunctionDeclaration', pos, endPos, {
        name: fnMatch[1],
        value: trimmed,
        metadata: {
          isLoop: false,
          isRecursive: false,
          isConditional: false,
          nestedDepth: Math.floor(col / 4),
        },
      })
    }

    // Class declarations
    if (/^class\s+(\w+)/.test(trimmed)) {
      const m = trimmed.match(/^class\s+(\w+)/)!
      return makeNode('ClassDeclaration', pos, endPos, { name: m[1] })
    }

    // For loops
    if (/^for\s+/.test(trimmed) || /^for\s*\(/.test(trimmed)) {
      const loopVars = this.extractLoopVariables(trimmed, language)
      return makeNode('ForStatement', pos, endPos, {
        value: trimmed,
        metadata: {
          isLoop: true,
          isRecursive: false,
          isConditional: false,
          loopVariables: loopVars,
          nestedDepth: Math.floor(col / 4),
        },
      })
    }

    // While loops
    if (/^while\s+/.test(trimmed) || /^while\s*\(/.test(trimmed)) {
      return makeNode('WhileStatement', pos, endPos, {
        value: trimmed,
        metadata: {
          isLoop: true,
          isRecursive: false,
          isConditional: false,
          nestedDepth: Math.floor(col / 4),
        },
      })
    }

    // Do-while
    if (/^do\s*\{/.test(trimmed)) {
      return makeNode('DoWhileStatement', pos, endPos, {
        value: trimmed,
        metadata: {
          isLoop: true,
          isRecursive: false,
          isConditional: false,
          nestedDepth: Math.floor(col / 4),
        },
      })
    }

    // If statements
    if (/^if\s+/.test(trimmed) || /^if\s*\(/.test(trimmed)) {
      return makeNode('IfStatement', pos, endPos, {
        value: trimmed,
        metadata: {
          isLoop: false,
          isRecursive: false,
          isConditional: true,
          nestedDepth: Math.floor(col / 4),
        },
      })
    }

    // Else / elif
    if (/^(else|elif)\b/.test(trimmed)) {
      return makeNode('ElseClause', pos, endPos, { value: trimmed })
    }

    // Return statements
    if (/^return\b/.test(trimmed)) {
      return makeNode('ReturnStatement', pos, endPos, { value: trimmed })
    }

    // Break / continue
    if (/^break\b/.test(trimmed)) return makeNode('BreakStatement', pos, endPos)
    if (/^continue\b/.test(trimmed)) return makeNode('ContinueStatement', pos, endPos)

    // Function calls (lines that are standalone calls)
    const callMatch = trimmed.match(/^(\w+(?:\.\w+)*)\s*\(/)
    if (callMatch && !trimmed.includes('=')) {
      const name = callMatch[1]
      const node = makeNode('CallExpression', pos, endPos, { name, value: trimmed })
      // Extract argument identifiers as children
      const argStr = trimmed.slice(trimmed.indexOf('(') + 1, trimmed.lastIndexOf(')'))
      for (const arg of argStr.split(',')) {
        const argTrimmed = arg.trim()
        if (/^\w+$/.test(argTrimmed)) {
          node.children.push(makeNode('Identifier', pos, endPos, { name: argTrimmed }))
        }
      }
      return node
    }

    // Augmented assignments (+=, -=, etc.)
    const augMatch = trimmed.match(/^(\w+(?:\[.*?\])?)\s*(\+=|-=|\*=|\/=|%=|&=|\|=|\^=|<<=|>>=)\s*(.+)/)
    if (augMatch) {
      const node = makeNode('AugmentedAssignment', pos, endPos, { value: trimmed })
      node.children.push(makeNode('Identifier', pos, endPos, { name: augMatch[1].split('[')[0] }))
      const rhsLiteralMatch = augMatch[3].match(/^\d+$/)
      if (rhsLiteralMatch) {
        node.children.push(
          makeNode('Literal', pos, endPos, { value: Number(augMatch[3]) }),
        )
      } else {
        node.children.push(makeNode('Identifier', pos, endPos, { name: augMatch[3].trim() }))
      }
      return node
    }

    // Variable assignments / declarations
    const assignMatch = trimmed.match(/^(?:(?:let|const|var|int|long|double|float|bool|string|auto)\s+)?(\w+)\s*(?::\s*\w+)?\s*=\s*(.+)/)
    if (assignMatch) {
      const name = assignMatch[1]
      const rhs = assignMatch[2].trim()
      const node = makeNode('VariableDeclaration', pos, endPos, { name, value: rhs })

      // Parse RHS for arrays / calls / literals
      if (rhs.startsWith('[') || rhs.includes('[]') || /\[\s*\]/.test(rhs)) {
        const arrNode = makeNode('ArrayExpression', pos, endPos, { value: rhs })
        // Try to find dynamic size identifier: [0] * n → children have Identifier 'n'
        const sizeMatch = rhs.match(/\*\s*(\w+)$/) || rhs.match(/\((\w+)\)$/)
        if (sizeMatch) {
          arrNode.children.push(
            makeNode('Identifier', pos, endPos, { name: sizeMatch[1] }),
          )
        }
        node.children.push(arrNode)
      } else if (/^[\d.]+$/.test(rhs) || rhs === 'true' || rhs === 'false' || rhs === 'null' || rhs === 'None') {
        node.children.push(makeNode('Literal', pos, endPos, { value: rhs }))
      } else if (/^\w+\s*\(/.test(rhs)) {
        // RHS is a call expression
        const callName = rhs.match(/^(\w+(?:\.\w+)*)/)![1]
        const callNode = makeNode('CallExpression', pos, endPos, { name: callName, value: rhs })
        const argStr = rhs.slice(rhs.indexOf('(') + 1, rhs.lastIndexOf(')'))
        for (const arg of argStr.split(',')) {
          const a = arg.trim()
          if (/^\w+$/.test(a)) {
            callNode.children.push(makeNode('Identifier', pos, endPos, { name: a }))
          }
        }
        node.children.push(callNode)
      } else if (/^\w+$/.test(rhs)) {
        node.children.push(makeNode('Identifier', pos, endPos, { name: rhs }))
      }

      return node
    }

    // Bare identifiers or expressions
    if (/^\w+$/.test(trimmed)) {
      return makeNode('Identifier', pos, endPos, { name: trimmed })
    }

    return null
  }

  private static extractLoopVariables(line: string, _language: Language): string[] {
    // Python: for i in range(...)
    const pyMatch = line.match(/^for\s+(\w+(?:,\s*\w+)*)\s+in\s+/)
    if (pyMatch) return pyMatch[1].split(',').map((s) => s.trim())

    // C/Java/JS: for (int i = 0; ...)
    const cMatch = line.match(/for\s*\(\s*(?:\w+\s+)?(\w+)\s*=/)
    if (cMatch) return [cMatch[1]]

    return []
  }

  private static annotateNestedDepth(node: ASTNode, depth: number): void {
    node.metadata.nestedDepth = depth
    const childDepth =
      node.type === 'ForStatement' ||
      node.type === 'WhileStatement' ||
      node.type === 'DoWhileStatement'
        ? depth + 1
        : depth
    for (const child of node.children) {
      this.annotateNestedDepth(child, childDepth)
    }
  }

  // ─── Linter ───────────────────────────────────────────────────────────────

  private static lint(
    ast: ASTNode,
    _code: string,
  ): ParseResult['diagnostics'] {
    const diagnostics: ParseResult['diagnostics'] = []
    const funcDecls = this.findByType(ast, 'FunctionDeclaration')

    for (const func of funcDecls) {
      const calls = this.findByType(func, 'CallExpression')
      const selfCalls = calls.filter((c) => c.name === func.name)
      if (selfCalls.length > 0 && !this.hasReturnStatement(func)) {
        diagnostics.push({
          line: func.position.line,
          message: `Recursive function '${func.name}' may be missing a base case (no return statement found).`,
          severity: 'warning',
        })
      }
    }

    // Deep nesting warning
    const maxDepth = this.findByType(ast, 'ForStatement')
      .concat(this.findByType(ast, 'WhileStatement'))
      .reduce((max, n) => Math.max(max, n.metadata.nestedDepth), 0)

    if (maxDepth >= 4) {
      diagnostics.push({
        line: 1,
        message: `Deeply nested loops (depth ${maxDepth}) detected. Time complexity may be O(n^${maxDepth}).`,
        severity: 'info',
      })
    }

    return diagnostics
  }

  private static hasReturnStatement(node: ASTNode): boolean {
    return this.findByType(node, 'ReturnStatement').length > 0
  }

  private static findByType(node: ASTNode, type: ASTNodeType): ASTNode[] {
    const result: ASTNode[] = []
    const stack = [node]
    while (stack.length > 0) {
      const current = stack.pop()!
      if (current.type === type) result.push(current)
      stack.push(...current.children)
    }
    return result
  }
}
