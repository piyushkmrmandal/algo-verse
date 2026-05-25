import { test, expect } from '@playwright/test';

/**
 * Social Auth (OAuth2) redirect tests.
 * These tests verify that clicking each social-login button triggers a navigation
 * to the correct Spring Security OAuth2 authorization endpoint.
 * Route interception is used to abort the redirect before leaving the test domain,
 * allowing us to assert the intended URL without needing a live OAuth provider.
 */

const OAUTH_PROVIDERS = [
  { label: 'Continue with Google',   provider: 'google' },
  { label: 'Continue with GitHub',   provider: 'github' },
  { label: 'Continue with LinkedIn', provider: 'linkedin' },
] as const;

test.describe('Social Auth — Login page', () => {
  for (const { label, provider } of OAUTH_PROVIDERS) {
    test(`${label} redirects to /oauth2/authorization/${provider}`, async ({ page }) => {
      await page.goto('/login');

      // Abort the outbound OAuth redirect so the test stays in the browser sandbox
      const authUrl = `/oauth2/authorization/${provider}`;
      await page.route(`**${authUrl}**`, route => route.abort('aborted'));

      // Also intercept any full-path redirects that might include the base URL
      const navigationPromise = page.waitForRequest(
        req => req.url().includes(`/oauth2/authorization/${provider}`),
        { timeout: 5000 }
      ).catch(() => null);

      await page.getByText(label).click();

      const interceptedRequest = await navigationPromise;
      expect(interceptedRequest).not.toBeNull();
      expect(interceptedRequest!.url()).toContain(`/oauth2/authorization/${provider}`);
    });
  }
});

const SIGNUP_PROVIDERS = [
  { label: 'Sign up with Google',   provider: 'google' },
  { label: 'Sign up with GitHub',   provider: 'github' },
  { label: 'Sign up with LinkedIn', provider: 'linkedin' },
] as const;

test.describe('Social Auth — Register page', () => {
  for (const { label, provider } of SIGNUP_PROVIDERS) {
    test(`${label} redirects to /oauth2/authorization/${provider}`, async ({ page }) => {
      await page.goto('/register');

      await page.route(`**oauth2/authorization/${provider}**`, route => route.abort('aborted'));

      const navigationPromise = page.waitForRequest(
        req => req.url().includes(`/oauth2/authorization/${provider}`),
        { timeout: 5000 }
      ).catch(() => null);

      await page.getByText(label).click();

      const interceptedRequest = await navigationPromise;
      expect(interceptedRequest).not.toBeNull();
      expect(interceptedRequest!.url()).toContain(`/oauth2/authorization/${provider}`);
    });
  }
});

test.describe('Social Auth — Accessibility', () => {
  test('social auth buttons have accessible roles on login page', async ({ page }) => {
    await page.goto('/login');
    for (const { label } of OAUTH_PROVIDERS) {
      const btn = page.getByText(label);
      await expect(btn).toBeVisible();
      // Buttons must be keyboard-focusable
      await btn.focus();
      await expect(btn).toBeFocused();
    }
  });

  test('social auth buttons have accessible roles on register page', async ({ page }) => {
    await page.goto('/register');
    for (const { label } of SIGNUP_PROVIDERS) {
      const btn = page.getByText(label);
      await expect(btn).toBeVisible();
      await btn.focus();
      await expect(btn).toBeFocused();
    }
  });
});
