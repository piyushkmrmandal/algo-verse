import { create } from 'zustand'
import { devtools, persist } from 'zustand/middleware'

export type SupportedLanguage = 'python' | 'java' | 'cpp' | 'javascript' | 'go' | 'rust'

export type SubmissionStatus =
  | 'IDLE'
  | 'RUNNING'
  | 'SUBMITTING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'

interface EditorState {
  // Per-problem code storage (key: `${problemSlug}:${language}`)
  codeMap: Record<string, string>

  // Current session state
  currentLanguage: SupportedLanguage
  isSubmitting: boolean
  isRunning: boolean
  submissionStatus: SubmissionStatus
  lastSubmissionId: string | null

  // Editor UI state
  fontSize: number
  showLineNumbers: boolean
  showMinimap: boolean
  wordWrap: boolean

  // Actions
  setCode: (problemSlug: string, language: SupportedLanguage, code: string) => void
  getCode: (problemSlug: string, language: SupportedLanguage) => string | null
  resetCode: (problemSlug: string, language: SupportedLanguage) => void
  setLanguage: (language: SupportedLanguage) => void
  setSubmitting: (value: boolean) => void
  setRunning: (value: boolean) => void
  setSubmissionStatus: (status: SubmissionStatus) => void
  setLastSubmissionId: (id: string | null) => void
  updateEditorSettings: (
    settings: Partial<
      Pick<EditorState, 'fontSize' | 'showLineNumbers' | 'showMinimap' | 'wordWrap'>
    >,
  ) => void
}

const codeKey = (slug: string, lang: SupportedLanguage): string => `${slug}:${lang}`

export const useEditorStore = create<EditorState>()(
  devtools(
    persist(
      (set, get) => ({
        // ── Persisted state ────────────────────────────────────────────────
        codeMap: {},
        currentLanguage: 'python',
        fontSize: 14,
        showLineNumbers: true,
        showMinimap: false,
        wordWrap: false,

        // ── Session-only state (not persisted — reset handled by middleware) ─
        isSubmitting: false,
        isRunning: false,
        submissionStatus: 'IDLE',
        lastSubmissionId: null,

        // ── Actions ────────────────────────────────────────────────────────
        setCode: (problemSlug, language, code) =>
          set(
            (state) => ({
              codeMap: { ...state.codeMap, [codeKey(problemSlug, language)]: code },
            }),
            false,
            'editor/setCode',
          ),

        getCode: (problemSlug, language) => {
          const state = get()
          return state.codeMap[codeKey(problemSlug, language)] ?? null
        },

        resetCode: (problemSlug, language) =>
          set(
            (state) => {
              const next = { ...state.codeMap }
              delete next[codeKey(problemSlug, language)]
              return { codeMap: next }
            },
            false,
            'editor/resetCode',
          ),

        setLanguage: (language) =>
          set({ currentLanguage: language }, false, 'editor/setLanguage'),

        setSubmitting: (value) =>
          set({ isSubmitting: value }, false, 'editor/setSubmitting'),

        setRunning: (value) => set({ isRunning: value }, false, 'editor/setRunning'),

        setSubmissionStatus: (status) =>
          set({ submissionStatus: status }, false, 'editor/setSubmissionStatus'),

        setLastSubmissionId: (id) =>
          set({ lastSubmissionId: id }, false, 'editor/setLastSubmissionId'),

        updateEditorSettings: (settings) =>
          set(settings, false, 'editor/updateEditorSettings'),
      }),
      {
        name: 'algoverse-editor',
        // Only persist code + UI prefs, not ephemeral session flags
        partialize: (state) => ({
          codeMap: state.codeMap,
          currentLanguage: state.currentLanguage,
          fontSize: state.fontSize,
          showLineNumbers: state.showLineNumbers,
          showMinimap: state.showMinimap,
          wordWrap: state.wordWrap,
        }),
      },
    ),
    { name: 'EditorStore' },
  ),
)
