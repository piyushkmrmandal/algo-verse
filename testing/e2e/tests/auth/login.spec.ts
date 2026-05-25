import { test, expect } from '@playwright/test';

test.describe('Login Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('should display AlgoVerse branding', async ({ page }) => {
    await expect(page.getByText('AlgoVerse')).toBeVisible();
    await expect(page.getByText('Sign in to continue your journey')).toBeVisible();
  });

  test('should display social auth buttons', async ({ page }) => {
    await expect(page.getByText('Continue with Google')).toBeVisible();
    await expect(page.getByText('Continue with GitHub')).toBeVisible();
    await expect(page.getByText('Continue with LinkedIn')).toBeVisible();
  });

  test('should display or divider', async ({ page }) => {
    await expect(page.getByText('or')).toBeVisible();
  });

  test('should display email and password fields', async ({ page }) => {
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await expect(page.getByPlaceholder('••••••••')).toBeVisible();
  });

  test('should show validation error for empty form', async ({ page }) => {
    await page.getByRole('button', { name: 'Sign in' }).click();
    // HTML5 validation prevents submission — email field should be focused
    await expect(page.getByPlaceholder('you@example.com')).toBeFocused();
  });

  test('should navigate to register page', async ({ page }) => {
    await page.getByRole('link', { name: 'Create one' }).click();
    await expect(page).toHaveURL('/register');
  });

  test('social auth buttons redirect correctly', async ({ page }) => {
    // Intercept the navigation to avoid leaving the test domain
    await page.route('/oauth2/authorization/google', route => route.abort());
    await page.getByText('Continue with Google').click();
    // Check URL changed (navigation was attempted)
    await expect(page).toHaveURL(/oauth2\/authorization\/google/);
  });
});
