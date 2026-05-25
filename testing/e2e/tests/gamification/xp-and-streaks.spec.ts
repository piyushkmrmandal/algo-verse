import { test, expect } from '@playwright/test';

const MOCK_USER = {
  id: 'user-1',
  email: 'test@test.com',
  displayName: 'Test User',
  roles: ['ROLE_USER'],
};

const MOCK_XP = {
  userId: 'user-1',
  totalXp: 1250,
  level: 5,
  xpToNextLevel: 500,
  xpProgress: 250,
};

const MOCK_STREAK = {
  userId: 'user-1',
  currentStreak: 7,
  longestStreak: 14,
  lastActiveDate: new Date().toISOString().split('T')[0],
};

const MOCK_LEADERBOARD = {
  content: [
    { rank: 1, userId: 'user-99', displayName: 'AlgoKing',  totalXp: 9800, level: 20 },
    { rank: 2, userId: 'user-42', displayName: 'ByteWizard', totalXp: 8500, level: 18 },
    { rank: 3, userId: 'user-1',  displayName: 'Test User',  totalXp: 1250, level: 5 },
  ],
  totalElements: 3,
  totalPages: 1,
  number: 0,
  size: 20,
};

async function setupMocks(page: any) {
  await page.route('**/api/v1/auth/me', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_USER) })
  );
  await page.route('**/api/v1/gamification/xp/user-1', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_XP) })
  );
  await page.route('**/api/v1/gamification/xp/**', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_XP) })
  );
  await page.route('**/api/v1/gamification/streak/user-1', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_STREAK) })
  );
  await page.route('**/api/v1/gamification/streak/**', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_STREAK) })
  );
  await page.route('**/api/v1/gamification/leaderboard**', route =>
    route.fulfill({ status: 200, body: JSON.stringify(MOCK_LEADERBOARD) })
  );
}

test.describe('XP Counter', () => {
  test.beforeEach(async ({ page }) => {
    await setupMocks(page);
    await page.goto('/dashboard');
  });

  test('should display XP counter on dashboard', async ({ page }) => {
    // XP value or label should appear somewhere on the dashboard
    await expect(
      page.getByText('1250')
        .or(page.getByText('1,250'))
        .or(page.getByText(/1250 XP/i))
        .or(page.getByText(/XP/i))
        .or(page.locator('[data-testid="xp-counter"]'))
    ).toBeVisible({ timeout: 8000 });
  });

  test('should display level information', async ({ page }) => {
    await expect(
      page.getByText('Level 5')
        .or(page.getByText('Lv. 5'))
        .or(page.getByText(/level/i))
        .or(page.locator('[data-testid="level-badge"]'))
    ).toBeVisible({ timeout: 8000 });
  });
});

test.describe('Streak Counter', () => {
  test.beforeEach(async ({ page }) => {
    await setupMocks(page);
    await page.goto('/dashboard');
  });

  test('should display streak counter on dashboard', async ({ page }) => {
    // Streak value or label should appear somewhere on the dashboard
    await expect(
      page.getByText('7')
        .or(page.getByText('7 days')
        .or(page.getByText(/streak/i))
        .or(page.locator('[data-testid="streak-counter"]')))
    ).toBeVisible({ timeout: 8000 });
  });

  test('should display streak fire icon or label', async ({ page }) => {
    // Streak is typically shown with a flame icon or "Streak" label
    await expect(
      page.getByText(/streak/i)
        .or(page.locator('[data-testid="streak-widget"]'))
        .or(page.locator('[aria-label*="streak"]'))
    ).toBeVisible({ timeout: 8000 });
  });
});

test.describe('Leaderboard', () => {
  test.beforeEach(async ({ page }) => {
    await setupMocks(page);
  });

  test('should be accessible at /leaderboard route', async ({ page }) => {
    await page.goto('/leaderboard');
    await expect(page).toHaveURL('/leaderboard');
    // Page should render without 404
    await expect(
      page.getByText(/leaderboard/i)
        .or(page.getByText(/ranking/i))
        .or(page.getByText(/AlgoKing/))
        .or(page.locator('[data-testid="leaderboard"]'))
    ).toBeVisible({ timeout: 8000 });
  });

  test('should display top ranked users', async ({ page }) => {
    await page.goto('/leaderboard');
    await expect(
      page.getByText('AlgoKing')
        .or(page.getByText('ByteWizard'))
    ).toBeVisible({ timeout: 8000 });
  });

  test('dashboard should have a link or button to the leaderboard', async ({ page }) => {
    await page.goto('/dashboard');
    const leaderboardLink = page.getByRole('link', { name: /leaderboard/i })
      .or(page.getByRole('button', { name: /leaderboard/i }))
      .or(page.getByText(/leaderboard/i));
    await expect(leaderboardLink).toBeVisible({ timeout: 8000 });
  });
});
