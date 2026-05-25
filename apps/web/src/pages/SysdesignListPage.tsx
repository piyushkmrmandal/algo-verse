import { useState } from 'react'
import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import AppNav from '../components/ui/AppNav'

interface SysdesignProblem {
  id: string
  slug: string
  title: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  category: string
  descriptionMd: string
  published: boolean
}

const CATEGORIES = ['All', 'Storage', 'Compute', 'Messaging', 'Search', 'Social', 'Financial']
const DIFFICULTIES = ['All', 'Easy', 'Medium', 'Hard']

function difficultyBadge(d: SysdesignProblem['difficulty']) {
  const cls = { EASY: 'badge-easy', MEDIUM: 'badge-medium', HARD: 'badge-hard' }
  return <span className={cls[d]}>{d[0] + d.slice(1).toLowerCase()}</span>
}

const categoryIcon: Record<string, string> = {
  Storage: '🗄️', Compute: '⚡', Messaging: '📨', Search: '🔍',
  Social: '👥', Financial: '💳', All: '🌐',
}

export default function SysdesignListPage() {
  const [category, setCategory] = useState('All')
  const [difficulty, setDifficulty] = useState('All')

  const { data: problems = [], isLoading } = useQuery<SysdesignProblem[]>({
    queryKey: ['sysdesign', 'problems', { category, difficulty }],
    queryFn: () =>
      api.get<SysdesignProblem[]>('/sysdesign/problems', {
        params: {
          category: category !== 'All' ? category : undefined,
          difficulty: difficulty !== 'All' ? difficulty.toUpperCase() : undefined,
        },
      }).then((r) => r.data),
    staleTime: 5 * 60_000,
  })

  return (
    <div className="min-h-screen bg-bg-base">
      <AppNav />

      <div className="max-w-5xl mx-auto px-4 py-12">
        {/* Header */}
        <motion.div initial={{ opacity: 0, y: -16 }} animate={{ opacity: 1, y: 0 }} className="mb-10">
          <h1 className="text-3xl font-bold text-gradient-brand mb-2">System Design</h1>
          <p className="text-text-secondary">
            Design scalable systems with AI-powered feedback on your diagrams.
          </p>
        </motion.div>

        {/* Filters */}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.05 }}
          className="flex flex-wrap gap-4 mb-8"
        >
          {/* Category pills */}
          <div className="flex flex-wrap gap-2">
            {CATEGORIES.map((c) => (
              <button
                key={c}
                onClick={() => setCategory(c)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium border transition-all duration-150 ${
                  category === c
                    ? 'bg-brand-primary text-white border-brand-primary'
                    : 'border-border-subtle text-text-secondary hover:border-border-default hover:text-text-primary bg-bg-elevated'
                }`}
              >
                {categoryIcon[c]} {c}
              </button>
            ))}
          </div>

          {/* Difficulty */}
          <div className="flex gap-1 bg-bg-elevated rounded-lg p-1 border border-border-subtle ml-auto">
            {DIFFICULTIES.map((d) => (
              <button
                key={d}
                onClick={() => setDifficulty(d)}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-all duration-150 ${
                  difficulty === d
                    ? 'bg-brand-primary text-white'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {d}
              </button>
            ))}
          </div>
        </motion.div>

        {/* Problem grid */}
        {isLoading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-44 bg-bg-elevated rounded-2xl animate-pulse" />
            ))}
          </div>
        ) : problems.length === 0 ? (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="flex flex-col items-center justify-center py-20 gap-3 text-center"
          >
            <span className="text-5xl">🏗️</span>
            <p className="text-text-primary font-semibold">No problems found</p>
            <p className="text-text-muted text-sm">Try changing the filters above</p>
          </motion.div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {problems.map((problem, i) => (
              <motion.div
                key={problem.id}
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.04 }}
              >
                <Link
                  to={`/sysdesign/${problem.slug}`}
                  className="group block h-full glass rounded-2xl p-5 hover:border-brand-primary/40 transition-all duration-200 hover:-translate-y-0.5"
                >
                  <div className="flex items-start justify-between mb-3">
                    <span className="text-2xl">{categoryIcon[problem.category] ?? '🔧'}</span>
                    {difficultyBadge(problem.difficulty)}
                  </div>

                  <h3 className="font-semibold text-text-primary group-hover:text-brand-primary transition-colors mb-2 leading-snug">
                    {problem.title}
                  </h3>

                  <p className="text-xs text-text-muted line-clamp-2 leading-relaxed">
                    {problem.descriptionMd.replace(/[#*`]/g, '').slice(0, 120)}…
                  </p>

                  <div className="flex items-center justify-between mt-4">
                    <span className="text-xs text-text-muted bg-bg-elevated px-2 py-0.5 rounded-md border border-border-subtle">
                      {problem.category}
                    </span>
                    <span className="text-xs text-brand-primary font-medium opacity-0 group-hover:opacity-100 transition-opacity">
                      Design it →
                    </span>
                  </div>
                </Link>
              </motion.div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
