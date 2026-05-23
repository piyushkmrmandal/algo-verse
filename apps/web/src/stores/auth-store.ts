import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import axios from 'axios';

export type UserRole = 'USER' | 'ADMIN' | 'MODERATOR';

export interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  role: UserRole;
  isEmailVerified: boolean;
}

interface AuthState {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;

  // Actions
  setAuth: (user: AuthUser, accessToken: string, refreshToken: string) => void;
  setAccessToken: (token: string) => void;
  logout: () => void;
  refreshTokens: () => Promise<string>;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,

      setAuth: (user, accessToken, refreshToken) => {
        set({ user, accessToken, refreshToken, isAuthenticated: true });
      },

      setAccessToken: (accessToken) => {
        set({ accessToken });
      },

      logout: () => {
        const refreshToken = get().refreshToken;

        // Fire-and-forget logout call — don't block the UI
        if (refreshToken) {
          const accessToken = get().accessToken;
          axios
            .post(
              '/api/v1/auth/logout',
              { refreshToken },
              { headers: { Authorization: `Bearer ${accessToken}` } }
            )
            .catch(() => {
              // Ignore errors — we're logging out regardless
            });
        }

        set({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false });
      },

      refreshTokens: async () => {
        const { refreshToken } = get();
        if (!refreshToken) throw new Error('No refresh token available');

        const response = await axios.post<{
          accessToken: string;
          refreshToken: string;
          expiresIn: number;
          user: AuthUser;
        }>('/api/v1/auth/refresh', { refreshToken });

        const { accessToken, refreshToken: newRefreshToken, user } = response.data;
        set({ accessToken, refreshToken: newRefreshToken, user, isAuthenticated: true });
        return accessToken;
      },
    }),
    {
      name: 'algoverse-auth',
      storage: createJSONStorage(() => localStorage),
      // Only persist tokens + user, not derived isAuthenticated
      partialize: (state) => ({
        user: state.user,
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
