import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { api } from '../lib/api';
import { queryKeys } from '../lib/queryKeys';
import type { Page, ProblemSummary, Difficulty } from '@algoverse/shared-types';
import ThemeToggle from '../components/ui/ThemeToggle';

const DIFFICULTIES: Array<{ value: string; label: string }> = [
  { value: '', label: 'All' },
  { value: 'EASY', label: 'Easy' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HARD', label: 'Hard' },
];

function difficultyBadge(d: Difficulty) {
  const cls: Record<Difficulty, string> = {
    EASY: 'badge-easy',
    MEDIUM: 'badge-medium',
    HARD: 'badge-hard',
  };
  return <span className={cls[d]}>{d[0] + d.slice(1).toLowerCase()}</span>;
}

export default function ProblemsListPage() {
  const [page, setPage] = useState(0);
  const [difficulty, setDifficulty] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');

  const { data, isLoading, isFetching } = useQuery({
    queryKey: queryKeys.problems.list({ page, size: 20, difficulty: difficulty || undefined, search: search || undefined }),
    queryFn: () =>
      api
        .get<Page<ProblemSummary>>('/problems', {
          params: { page, size: 20, difficulty: difficulty || undefined, search: search || undefined },
        })
        .then((r) => r.data),
    placeholderData: (prev) => prev,
  });

  return (
    <div className="min-h-screen bg-bg-base">
      {/* Top bar */}
      <nav className="border-b border-border-subtle bg-bg-surface/80 backdrop-blur-md sticky top-0 z-40">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between">
          <Link to="/dashboard" className="font-bold text-gradient-brand text-lg">AlgoVerse</Link>
          <ThemeToggle />
        </div>
      </nav>

      <div className="max-w-5xl mx-auto px-4 py-12">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: -16 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <h1 className="text-3xl font-bold text-gradient-brand mb-1">Problems</h1>
          <p className="text-text-secondary text-sm">
            {data?.totalElements ?? '—'} problems · sharpen your skills
          </p>
        </motion.div>

        {/* Filters */}
        <div className="flex flex-wrap gap-3 mb-6">
          <form
            onSubmit={(e) => { e.preventDefault(); setSearch(searchInput); setPage(0); }}
            className="flex gap-2 flex-1 min-w-[200px]"
          >
            <input
              className="input-base flex-1"
              placeholder="Search problems…"
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
            />
            <button type="submit" className="btn-primary px-3">Search</button>
          </form>

          <div className="flex gap-1 bg-bg-elevated rounded-lg p-1 border border-border-subtle">
            {DIFFICULTIES.map((d) => (
              <button
                key={d.value}
                onClick={() => { setDifficulty(d.value); setPage(0); }}
                className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all duration-150 ${
                  difficulty === d.value
                    ? 'bg-brand-primary text-white'
                    : 'text-text-secondary hover:text-text-primary'
                }`}
              >
                {d.label}
              </button>
            ))}
          </div>
        </div>

        {/* Table */}
        <div className={`transition-opacity duration-200 ${isFetching ? 'opacity-60' : 'opacity-100'}`}>
          {isLoading ? (
            <div className="space-y-2">
              {Array.from({ length: 10 }).map((_, i) => (
                <div key={i} className="h-14 bg-bg-elevated rounded-lg animate-pulse" />
              ))}
            </div>
          ) : (
            <AnimatePresence mode="popLayout">
              <div className="divide-y divide-border-subtle rounded-xl border border-border-subtle overflow-hidden">
                {data?.items.map((problem, i) => (
                  <motion.div
                    key={problem.id}
                    initial={{ opacity: 0, x: -8 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.02 }}
                  >
                    <Link
                      to={`/problems/${problem.slug}`}
                      className="flex items-center gap-4 px-5 py-4 bg-bg-surface hover:bg-bg-elevated transition-colors group"
                    >
                      <span className="text-text-muted text-sm w-8 shrink-0 font-mono">
                        {page * 20 + i + 1}
                      </span>
                      <span className="flex-1 text-text-primary group-hover:text-brand-primary transition-colors font-medium text-sm">
                        {problem.title}
                        {problem.isPremium && (
                          <span className="ml-2 text-xs text-warning">★ Premium</span>
                        )}
                      </span>
                      <div className="flex items-center gap-3 shrink-0">
                        {difficultyBadge(problem.difficulty)}
                        <span className="text-text-muted text-xs w-16 text-right">
                          {problem.acceptanceRate.toFixed(1)}%
                        </span>
                      </div>
                    </Link>
                  </motion.div>
                ))}
              </div>
            </AnimatePresence>
          )}
        </div>

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex justify-center gap-2 mt-8">
            <button
              className="btn-ghost"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              ← Prev
            </button>
            <span className="flex items-center px-4 text-text-secondary text-sm">
              {page + 1} / {data.totalPages}
            </span>
            <button
              className="btn-ghost"
              onClick={() => setPage((p) => p + 1)}
              disabled={!data.hasNext}
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
