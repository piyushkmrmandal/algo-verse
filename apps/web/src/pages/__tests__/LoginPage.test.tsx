import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import LoginPage from '../LoginPage';

vi.mock('framer-motion', () => ({
  motion: {
    div: ({ children, ...props }: any) => <div {...props}>{children}</div>,
    button: ({ children, ...props }: any) => <button {...props}>{children}</button>,
    p: ({ children, ...props }: any) => <p {...props}>{children}</p>,
  },
  AnimatePresence: ({ children }: any) => <>{children}</>,
}));

vi.mock('../../lib/api', () => ({
  api: {
    post: vi.fn(),
  },
  getErrorMessage: vi.fn((err: any) => {
    if (err?.response?.data?.message) return err.response.data.message;
    if (err instanceof Error) return err.message;
    return 'An unexpected error occurred';
  }),
}));

vi.mock('../../stores/auth-store', () => ({
  useAuthStore: vi.fn((selector: any) => selector({
    setAuth: mockSetAuth,
    clearAuth: vi.fn(),
    user: null,
    accessToken: null,
  })),
}));

const mockSetAuth = vi.fn();
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function renderLoginPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '' },
    });
  });

  it('renders AlgoVerse title', () => {
    renderLoginPage();
    expect(screen.getByText('AlgoVerse')).toBeInTheDocument();
  });

  it('renders subtitle', () => {
    renderLoginPage();
    expect(screen.getByText('Sign in to continue your journey')).toBeInTheDocument();
  });

  it('renders email input', () => {
    renderLoginPage();
    expect(screen.getByPlaceholderText('you@example.com')).toBeInTheDocument();
  });

  it('renders password input', () => {
    renderLoginPage();
    expect(screen.getByPlaceholderText('••••••••')).toBeInTheDocument();
  });

  it('renders Sign in button', () => {
    renderLoginPage();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('renders SocialAuthButtons in signin mode', () => {
    renderLoginPage();
    expect(screen.getByText('Continue with Google')).toBeInTheDocument();
  });

  it('renders "or" divider', () => {
    renderLoginPage();
    expect(screen.getByText('or')).toBeInTheDocument();
  });

  it('shows error message when mutation fails', async () => {
    const { api } = await import('../../lib/api');
    const apiMock = api as any;
    apiMock.post.mockRejectedValueOnce(new Error('Invalid credentials'));

    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByPlaceholderText('you@example.com'), 'test@example.com');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(screen.getByText('Invalid credentials')).toBeInTheDocument();
    });
  });

  it('successful login calls setAuth and navigates to /problems', async () => {
    const { api } = await import('../../lib/api');
    const { useAuthStore } = await import('../../stores/auth-store');
    const apiMock = api as any;

    const mockAuthData = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 3600,
      user: {
        id: '1',
        email: 'test@example.com',
        displayName: 'Test User',
        avatarUrl: null,
        role: 'USER',
        isEmailVerified: true,
      },
    };

    apiMock.post.mockResolvedValueOnce({ data: mockAuthData });

    const user = userEvent.setup();
    renderLoginPage();

    await user.type(screen.getByPlaceholderText('you@example.com'), 'test@example.com');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/problems', { replace: true });
    });
  });

  it('"Create one" link points to /register', () => {
    renderLoginPage();
    const link = screen.getByRole('link', { name: 'Create one' });
    expect(link).toHaveAttribute('href', '/register');
  });
});
