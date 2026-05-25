import { test, expect } from '@playwright/test';

test.describe('Register Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/register');
  });

  test('should display AlgoVerse branding', async ({ page }) => {
    await expect(page.getByText('AlgoVerse')).toBeVisible();
    await expect(page.getByText('Create your account')).toBeVisible();
  });

  test('should display social auth buttons for signup', async ({ page }) => {
    await expect(page.getByText('Sign up with Google')).toBeVisible();
    await expect(page.getByText('Sign up with GitHub')).toBeVisible();
    await expect(page.getByText('Sign up with LinkedIn')).toBeVisible();
  });

  test('should display all form fields', async ({ page }) => {
    await expect(page.getByPlaceholder('Ada Lovelace')).toBeVisible();
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await expect(page.getByPlaceholder('••••••••')).toBeVisible();
  });

  test('should have password min length of 8', async ({ page }) => {
    const passwordInput = page.getByPlaceholder('••••••••');
    const minLength = await passwordInput.getAttribute('minlength');
    expect(minLength).toBe('8');
  });

  test('should navigate to login page', async ({ page }) => {
    await page.getByRole('link', { name: 'Sign in' }).click();
    await expect(page).toHaveURL('/login');
  });

  test('should fill and submit the form', async ({ page }) => {
    // Mock the API response
    await page.route('**/api/v1/auth/register', route =>
      route.fulfill({
        status: 200,
        body: JSON.stringify({
          user: { id: 'uuid-1', email: 'test@test.com', displayName: 'Test User' },
          accessToken: 'mock-token',
          refreshToken: 'mock-refresh',
        }),
      })
    );
    await page.getByPlaceholder('Ada Lovelace').fill('Test User');
    await page.getByPlaceholder('you@example.com').fill('test@test.com');
    await page.getByPlaceholder('••••••••').fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page).toHaveURL('/problems');
  });

  test('should show error message on registration failure', async ({ page }) => {
    await page.route('**/api/v1/auth/register', route =>
      route.fulfill({
        status: 409,
        body: JSON.stringify({ message: 'Email already in use' }),
      })
    );
    await page.getByPlaceholder('Ada Lovelace').fill('Test User');
    await page.getByPlaceholder('you@example.com').fill('taken@test.com');
    await page.getByPlaceholder('••••••••').fill('password123');
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByText('Email already in use')).toBeVisible();
  });
});
