export const queryKeys = {
  // Auth
  auth: {
    me: () => ['auth', 'me'] as const,
  },

  // Problems
  problems: {
    all: () => ['problems'] as const,
    list: (filters: {
      page?: number;
      size?: number;
      difficulty?: string;
      topic?: string;
      search?: string;
      premium?: boolean;
    }) => ['problems', 'list', filters] as const,
    detail: (slug: string) => ['problems', 'detail', slug] as const,
    topics: () => ['problems', 'topics'] as const,
  },

  // Submissions
  submissions: {
    all: () => ['submissions'] as const,
    list: (filters: { problemId?: string; page?: number; size?: number }) =>
      ['submissions', 'list', filters] as const,
    detail: (id: string) => ['submissions', 'detail', id] as const,
    byProblem: (problemId: string) => ['submissions', 'problem', problemId] as const,
  },

  // Gamification
  gamification: {
    profile: (userId: string) => ['gamification', 'profile', userId] as const,
    leaderboard: (period: 'daily' | 'weekly' | 'all-time') =>
      ['gamification', 'leaderboard', period] as const,
  },
} as const;
