import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SocialAuthButtons from '../SocialAuthButtons';

vi.mock('framer-motion', () => ({
  motion: {
    div: ({ children, ...props }: any) => <div {...props}>{children}</div>,
    button: ({ children, ...props }: any) => <button {...props}>{children}</button>,
    p: ({ children, ...props }: any) => <p {...props}>{children}</p>,
  },
  AnimatePresence: ({ children }: any) => <>{children}</>,
}));

describe('SocialAuthButtons', () => {
  beforeEach(() => {
    // Reset location before each test
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { href: '' },
    });
  });

  describe('signup mode', () => {
    it('renders 3 buttons with correct signup labels', () => {
      render(<SocialAuthButtons mode="signup" />);
      expect(screen.getByText('Sign up with Google')).toBeInTheDocument();
      expect(screen.getByText('Sign up with GitHub')).toBeInTheDocument();
      expect(screen.getByText('Sign up with LinkedIn')).toBeInTheDocument();
    });
  });

  describe('signin mode', () => {
    it('renders 3 buttons with correct signin labels', () => {
      render(<SocialAuthButtons mode="signin" />);
      expect(screen.getByText('Continue with Google')).toBeInTheDocument();
      expect(screen.getByText('Continue with GitHub')).toBeInTheDocument();
      expect(screen.getByText('Continue with LinkedIn')).toBeInTheDocument();
    });
  });

  it('renders brand icons with aria-hidden on all buttons', () => {
    render(<SocialAuthButtons mode="signin" />);
    const svgs = document.querySelectorAll('svg[aria-hidden="true"]');
    expect(svgs.length).toBe(3);
  });

  it('clicking Google button navigates to /oauth2/authorization/google', async () => {
    const user = userEvent.setup();
    render(<SocialAuthButtons mode="signin" />);
    await user.click(screen.getByText('Continue with Google'));
    expect(window.location.href).toBe('/oauth2/authorization/google');
  });

  it('clicking GitHub button navigates to /oauth2/authorization/github', async () => {
    const user = userEvent.setup();
    render(<SocialAuthButtons mode="signin" />);
    await user.click(screen.getByText('Continue with GitHub'));
    expect(window.location.href).toBe('/oauth2/authorization/github');
  });

  it('clicking LinkedIn button navigates to /oauth2/authorization/linkedin', async () => {
    const user = userEvent.setup();
    render(<SocialAuthButtons mode="signin" />);
    await user.click(screen.getByText('Continue with LinkedIn'));
    expect(window.location.href).toBe('/oauth2/authorization/linkedin');
  });

  it('renders buttons in order: Google, GitHub, LinkedIn', () => {
    render(<SocialAuthButtons mode="signin" />);
    const buttons = screen.getAllByRole('button');
    expect(buttons).toHaveLength(3);
    expect(buttons[0]).toHaveTextContent('Continue with Google');
    expect(buttons[1]).toHaveTextContent('Continue with GitHub');
    expect(buttons[2]).toHaveTextContent('Continue with LinkedIn');
  });
});
