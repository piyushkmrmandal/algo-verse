import { lazy, Suspense } from 'react';
import { createBrowserRouter, Outlet, Navigate } from 'react-router-dom';
import { useAuthStore } from './stores/auth-store';

// Lazy-loaded pages
const ProblemPage = lazy(() => import('./pages/ProblemPage'));
const ProblemsListPage = lazy(() => import('./pages/ProblemsListPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const OAuth2CallbackPage = lazy(() => import('./pages/OAuth2CallbackPage'));
const CollaboratePage = lazy(() => import('./pages/CollaboratePage'));
const CollabRoomPage = lazy(() => import('./pages/CollabRoomPage'));
const SysdesignListPage = lazy(() => import('./pages/SysdesignListPage'));
const SysdesignProblemPage = lazy(() => import('./pages/SysdesignProblemPage'));

function PageLoader() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-bg-base">
      <div className="flex flex-col items-center gap-4">
        <svg
          className="w-10 h-10 animate-spin text-brand-primary"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          />
        </svg>
        <p className="text-text-muted text-sm font-mono">Loading...</p>
      </div>
    </div>
  );
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function RequireGuest({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return <>{children}</>;
}

function RootLayout() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Outlet />
    </Suspense>
  );
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RootLayout />,
    children: [
      { index: true, element: <Navigate to="/problems" replace /> },

      // Public routes
      {
        path: 'login',
        element: (
          <RequireGuest>
            <LoginPage />
          </RequireGuest>
        ),
      },
      {
        path: 'register',
        element: (
          <RequireGuest>
            <RegisterPage />
          </RequireGuest>
        ),
      },

      // Protected routes
      {
        path: 'dashboard',
        element: (
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        ),
      },
      {
        path: 'problems',
        element: (
          <RequireAuth>
            <ProblemsListPage />
          </RequireAuth>
        ),
      },
      {
        path: 'problems/:slug',
        element: (
          <RequireAuth>
            <ProblemPage />
          </RequireAuth>
        ),
      },

      {
        path: 'collaborate',
        element: (
          <RequireAuth>
            <CollaboratePage />
          </RequireAuth>
        ),
      },
      {
        path: 'collab/room/:code',
        element: (
          <RequireAuth>
            <CollabRoomPage />
          </RequireAuth>
        ),
      },
      {
        path: 'sysdesign',
        element: (
          <RequireAuth>
            <SysdesignListPage />
          </RequireAuth>
        ),
      },
      {
        path: 'sysdesign/:slug',
        element: (
          <RequireAuth>
            <SysdesignProblemPage />
          </RequireAuth>
        ),
      },

      // OAuth2 callback — must be public (user is not yet authenticated)
      { path: 'oauth2/callback', element: <OAuth2CallbackPage /> },

      // 404
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export default router;
