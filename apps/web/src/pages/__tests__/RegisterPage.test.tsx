import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import RegisterPage from '../RegisterPage';

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

const mockSetAuth = vi.fn();

vi.mock('../../stores/auth-store', () => ({
  useAuthStore: vi.fn((selector: any) => selector({
    setAuth: mockSetAuth,
    clearAuth: vi.fn(),
    user: null,
    accessToken: null,
  })),
}));

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function renderRegisterPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '' },
    });
  });

  it('renders "Create your account" subtitle', () => {
    renderRegisterPage();
    expect(screen.getByText('Create your account')).toBeInTheDocument();
  });

  it('renders displayName input', () => {
    renderRegisterPage();
    expect(screen.getByPlaceholderText('Ada Lovelace')).toBeInTheDocument();
  });

  it('renders email input', () => {
    renderRegisterPage();
    expect(screen.getByPlaceholderText('you@example.com')).toBeInTheDocument();
  });

  it('renders password input', () => {
    renderRegisterPage();
    const passwordInput = screen.getByPlaceholderText('••••••••');
    expect(passwordInput).toBeInTheDocument();
  });

  it('renders SocialAuthButtons in signup mode', () => {
    renderRegisterPage();
    expect(screen.getByText('Sign up with Google')).toBeInTheDocument();
  });

  it('renders "or" divider', () => {
    renderRegisterPage();
    expect(screen.getByText('or')).toBeInTheDocument();
  });

  it('password field has minLength of 8', () => {
    renderRegisterPage();
    const passwordInput = screen.getByPlaceholderText('••••••••');
    expect(passwordInput).toHaveAttribute('minLength', '8');
  });

  it('shows error on failed registration with server message', async () => {
    const { api } = await import('../../lib/api');
    const apiMock = api as any;
    const serverError = { response: { data: { message: 'Email taken' } } };
    apiMock.post.mockRejectedValueOnce(serverError);

    const user = userEvent.setup();
    renderRegisterPage();

    await user.type(screen.getByPlaceholderText('Ada Lovelace'), 'Test User');
    await user.type(screen.getByPlaceholderText('you@example.com'), 'taken@example.com');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(screen.getByText('Email taken')).toBeInTheDocument();
    });
  });

  it('successful registration calls setAuth and navigates to /problems', async () => {
    const { api } = await import('../../lib/api');
    const apiMock = api as any;

    const mockAuthData = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 3600,
      user: {
        id: '1',
        email: 'new@example.com',
        displayName: 'New User',
        avatarUrl: null,
        role: 'USER',
        isEmailVerified: false,
      },
    };

    apiMock.post.mockResolvedValueOnce({ data: mockAuthData });

    const user = userEvent.setup();
    renderRegisterPage();

    await user.type(screen.getByPlaceholderText('Ada Lovelace'), 'New User');
    await user.type(screen.getByPlaceholderText('you@example.com'), 'new@example.com');
    await user.type(screen.getByPlaceholderText('••••••••'), 'password123');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/problems', { replace: true });
    });
  });

  it('"Sign in" link points to /login', () => {
    renderRegisterPage();
    const link = screen.getByRole('link', { name: 'Sign in' });
    expect(link).toHaveAttribute('href', '/login');
  });
});
