import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react'
import Editor, { type Monaco, type OnMount } from '@monaco-editor/react'
import { motion, AnimatePresence } from 'framer-motion'
import * as monaco from 'monaco-editor'
import { useEditorStore, type SupportedLanguage } from '../../stores/editor-store'

// ── Types ─────────────────────────────────────────────────────────────────────

interface CodeEditorProps {
  problemSlug: string
  initialCode?: string
  language: string
  readOnly?: boolean
  highlightedLine?: number
  onCodeChange?: (code: string) => void
  onSubmit?: (code: string) => void
  className?: string
}

// ── Language default templates ─────────────────────────────────────────────────

const DEFAULT_TEMPLATES: Record<SupportedLanguage, string> = {
  python: `# Write your solution here
from typing import List, Optional

class Solution:
    def solve(self) -> None:
        pass
`,
  java: `import java.util.*;

class Solution {
    public void solve() {
        // Write your solution here
    }
}
`,
  cpp: `#include <bits/stdc++.h>
using namespace std;

class Solution {
public:
    void solve() {
        // Write your solution here
    }
};
`,
  javascript: `/**
 * @return {void}
 */
var solve = function() {
    // Write your solution here
};
`,
  go: `package main

import "fmt"

func solve() {
    // Write your solution here
    fmt.Println("Hello, AlgoVerse!")
}
`,
  rust: `impl Solution {
    pub fn solve() {
        // Write your solution here
    }
}
`,
}

// ── AlgoVerse Monaco Theme ─────────────────────────────────────────────────────

const defineAlgoVerseTheme = (monacoInstance: Monaco): void => {
  monacoInstance.editor.defineTheme('algoverse-dark', {
    base: 'vs-dark',
    inherit: true,
    rules: [
      { token: '', foreground: 'F8F8F2', background: '0A0A0B' },
      { token: 'comment', foreground: '6272A4', fontStyle: 'italic' },
      { token: 'comment.line', foreground: '6272A4', fontStyle: 'italic' },
      { token: 'comment.block', foreground: '6272A4', fontStyle: 'italic' },
      { token: 'keyword', foreground: 'BD93F9', fontStyle: 'bold' },
      { token: 'keyword.control', foreground: 'BD93F9', fontStyle: 'bold' },
      { token: 'keyword.operator', foreground: 'FF79C6' },
      { token: 'string', foreground: 'F1FA8C' },
      { token: 'string.escape', foreground: 'FF79C6' },
      { token: 'number', foreground: 'FF79C6' },
      { token: 'number.float', foreground: 'FF79C6' },
      { token: 'delimiter', foreground: 'F8F8F2' },
      { token: 'delimiter.bracket', foreground: 'F8F8F2' },
      { token: 'type', foreground: '8BE9FD' },
      { token: 'type.identifier', foreground: '8BE9FD' },
      { token: 'entity.name.function', foreground: '50FA7B' },
      { token: 'variable', foreground: 'FFB86C' },
      { token: 'variable.parameter', foreground: 'FFB86C' },
      { token: 'constant', foreground: 'BD93F9' },
      { token: 'support.function', foreground: '50FA7B' },
      { token: 'operator', foreground: 'FF79C6' },
      { token: 'identifier', foreground: 'F8F8F2' },
      { token: 'tag', foreground: 'FF79C6' },
      { token: 'attribute.name', foreground: '50FA7B' },
      { token: 'attribute.value', foreground: 'F1FA8C' },
      { token: 'metatag', foreground: 'BD93F9' },
      { token: 'regexp', foreground: 'F1FA8C' },
    ],
    colors: {
      'editor.background': '#0A0A0B',
      'editor.foreground': '#F8F8F2',
      'editorCursor.foreground': '#6366F1',
      'editor.lineHighlightBackground': '#18181C',
      'editor.lineHighlightBorder': '#6366F130',
      'editorLineNumber.foreground': '#475569',
      'editorLineNumber.activeForeground': '#94A3B8',
      'editor.selectionBackground': '#6366F133',
      'editor.inactiveSelectionBackground': '#6366F120',
      'editorIndentGuide.background': '#1E1E2E',
      'editorIndentGuide.activeBackground': '#6366F150',
      'editor.wordHighlightBackground': '#6366F120',
      'editorBracketMatch.background': '#6366F130',
      'editorBracketMatch.border': '#6366F1',
      'editorGutter.background': '#0A0A0B',
      'scrollbar.shadow': '#00000040',
      'scrollbarSlider.background': '#47556930',
      'scrollbarSlider.hoverBackground': '#47556950',
      'scrollbarSlider.activeBackground': '#47556970',
      'editorWidget.background': '#111113',
      'editorWidget.border': '#18181C',
      'editorSuggestWidget.background': '#111113',
      'editorSuggestWidget.border': '#18181C',
      'editorSuggestWidget.selectedBackground': '#18181C',
      'input.background': '#18181C',
      'input.border': '#475569',
      'focusBorder': '#6366F1',
    },
  })
}

// ── Monaco language mapping ────────────────────────────────────────────────────

const MONACO_LANGUAGE_MAP: Record<SupportedLanguage, string> = {
  python: 'python',
  java: 'java',
  cpp: 'cpp',
  javascript: 'javascript',
  go: 'go',
  rust: 'rust',
}

// ── Component ─────────────────────────────────────────────────────────────────

const CodeEditor: React.FC<CodeEditorProps> = ({
  problemSlug,
  initialCode,
  language,
  readOnly = false,
  highlightedLine,
  onCodeChange,
  onSubmit,
  className = '',
}) => {
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null)
  const monacoRef = useRef<Monaco | null>(null)
  const decorationsRef = useRef<string[]>([])
  const [copied, setCopied] = useState(false)
  const [lineCount, setLineCount] = useState(0)
  const [charCount, setCharCount] = useState(0)

  const {
    fontSize,
    showLineNumbers,
    showMinimap,
    wordWrap,
    getCode,
    setCode,
  } = useEditorStore()

  const lang = language as SupportedLanguage

  // Resolve code: persisted > initialCode > template
  const resolveCode = useCallback((): string => {
    const persisted = getCode(problemSlug, lang)
    if (persisted !== null) return persisted
    if (initialCode) return initialCode
    return DEFAULT_TEMPLATES[lang] ?? ''
  }, [getCode, problemSlug, lang, initialCode])

  // ── Editor mount ────────────────────────────────────────────────────────────

  const handleEditorMount: OnMount = useCallback(
    (editor, monacoInstance) => {
      editorRef.current = editor
      monacoRef.current = monacoInstance

      defineAlgoVerseTheme(monacoInstance)
      monacoInstance.editor.setTheme('algoverse-dark')

      // Ctrl+Enter / Cmd+Enter → Submit
      editor.addAction({
        id: 'algoverse-submit',
        label: 'Submit Solution',
        keybindings: [
          monacoInstance.KeyMod.CtrlCmd | monacoInstance.KeyCode.Enter,
        ],
        run: () => {
          const code = editor.getValue()
          onSubmit?.(code)
        },
      })

      // Ctrl+Shift+F → Format
      editor.addAction({
        id: 'algoverse-format',
        label: 'Format Code',
        keybindings: [
          monacoInstance.KeyMod.CtrlCmd |
            monacoInstance.KeyMod.Shift |
            monacoInstance.KeyCode.KeyF,
        ],
        run: () => {
          editor.getAction('editor.action.formatDocument')?.run()
        },
      })

      const model = editor.getModel()
      if (model) {
        setLineCount(model.getLineCount())
        setCharCount(model.getValue().length)
      }
    },
    [onSubmit],
  )

  // ── Code change handler ────────────────────────────────────────────────────

  const handleCodeChange = useCallback(
    (value: string | undefined) => {
      const code = value ?? ''
      setCode(problemSlug, lang, code)
      onCodeChange?.(code)
      const model = editorRef.current?.getModel()
      if (model) {
        setLineCount(model.getLineCount())
        setCharCount(code.length)
      }
    },
    [setCode, problemSlug, lang, onCodeChange],
  )

  // ── Highlighted line decoration ────────────────────────────────────────────

  useEffect(() => {
    const editor = editorRef.current
    const monacoInstance = monacoRef.current
    if (!editor || !monacoInstance) return

    if (highlightedLine === undefined || highlightedLine < 1) {
      decorationsRef.current = editor.deltaDecorations(decorationsRef.current, [])
      return
    }

    decorationsRef.current = editor.deltaDecorations(decorationsRef.current, [
      {
        range: new monacoInstance.Range(highlightedLine, 1, highlightedLine, 1),
        options: {
          isWholeLine: true,
          className: 'algoverse-exec-line',
          glyphMarginClassName: 'algoverse-exec-glyph',
          overviewRuler: {
            color: '#F59E0B',
            position: monacoInstance.editor.OverviewRulerLane.Full,
          },
        },
      },
    ])

    // Scroll to revealed line
    editor.revealLineInCenterIfOutsideViewport(highlightedLine)
  }, [highlightedLine])

  // ── Language change: load persisted or template ───────────────────────────

  useEffect(() => {
    const editor = editorRef.current
    if (!editor) return
    const newCode = resolveCode()
    const currentCode = editor.getValue()
    if (currentCode !== newCode) {
      editor.setValue(newCode)
    }
  }, [lang, resolveCode])

  // ── Copy handler ───────────────────────────────────────────────────────────

  const handleCopy = useCallback(async () => {
    const code = editorRef.current?.getValue() ?? ''
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Fallback for older browsers
      const el = document.createElement('textarea')
      el.value = code
      document.body.appendChild(el)
      el.select()
      document.execCommand('copy')
      document.body.removeChild(el)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }, [])

  // ── Inject CSS for execution-line decoration ───────────────────────────────

  useEffect(() => {
    const id = 'algoverse-exec-line-style'
    if (document.getElementById(id)) return
    const style = document.createElement('style')
    style.id = id
    style.textContent = `
      .algoverse-exec-line {
        background: rgba(245, 158, 11, 0.12) !important;
        border-left: 3px solid #F59E0B !important;
        box-shadow: inset 0 0 12px rgba(245, 158, 11, 0.08);
      }
      .algoverse-exec-glyph::before {
        content: '▶';
        color: #F59E0B;
        font-size: 10px;
        line-height: 19px;
        margin-left: 4px;
      }
    `
    document.head.appendChild(style)
  }, [])

  const currentCode = resolveCode()

  return (
    <div className={`flex flex-col h-full bg-[#0A0A0B] ${className}`}>
      {/* Editor Area */}
      <div className="flex-1 relative overflow-hidden">
        <Editor
          defaultValue={currentCode}
          language={MONACO_LANGUAGE_MAP[lang] ?? 'python'}
          theme="algoverse-dark"
          onMount={handleEditorMount}
          onChange={handleCodeChange}
          options={{
            fontSize,
            fontFamily: '"JetBrains Mono", "Fira Code", monospace',
            fontLigatures: true,
            lineHeight: 22,
            minimap: { enabled: showMinimap },
            lineNumbers: showLineNumbers ? 'on' : 'off',
            renderLineHighlight: 'line',
            smoothScrolling: true,
            cursorSmoothCaretAnimation: 'on',
            formatOnPaste: true,
            formatOnType: false,
            wordWrap: wordWrap ? 'on' : 'off',
            readOnly,
            scrollBeyondLastLine: false,
            padding: { top: 12, bottom: 12 },
            overviewRulerBorder: false,
            hideCursorInOverviewRuler: true,
            scrollbar: {
              vertical: 'auto',
              horizontal: 'auto',
              useShadows: false,
              verticalScrollbarSize: 6,
              horizontalScrollbarSize: 6,
            },
            bracketPairColorization: { enabled: true },
            guides: { bracketPairs: true, indentation: true },
            suggest: { showKeywords: true },
            quickSuggestions: { strings: false },
            tabSize: lang === 'python' ? 4 : 4,
            insertSpaces: true,
            automaticLayout: true,
          }}
        />
      </div>

      {/* Status Bar */}
      <div className="flex items-center justify-between px-4 py-1.5 bg-[#111113] border-t border-[#18181C] text-xs text-[#475569] select-none">
        <div className="flex items-center gap-4">
          <span>
            Ln {lineCount}, Col{' '}
            {editorRef.current?.getPosition()?.column ?? 1}
          </span>
          <span>{lineCount} lines</span>
          <span>{charCount.toLocaleString()} chars</span>
        </div>

        <div className="flex items-center gap-3">
          {/* Copy button */}
          <motion.button
            whileTap={{ scale: 0.92 }}
            onClick={handleCopy}
            className="flex items-center gap-1.5 px-2 py-0.5 rounded text-[#94A3B8] hover:text-[#F8F8F2] hover:bg-[#18181C] transition-colors"
            title="Copy code"
          >
            <AnimatePresence mode="wait">
              {copied ? (
                <motion.span
                  key="copied"
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 4 }}
                  className="text-[#10B981]"
                >
                  ✓ Copied!
                </motion.span>
              ) : (
                <motion.span
                  key="copy"
                  initial={{ opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 4 }}
                >
                  Copy
                </motion.span>
              )}
            </AnimatePresence>
          </motion.button>

          <span className="text-[#18181C]">|</span>
          <span className="capitalize">{lang}</span>
          <span className="text-[#18181C]">|</span>
          <span>UTF-8</span>
        </div>
      </div>
    </div>
  )
}

export default CodeEditor
