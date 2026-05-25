import { test, expect } from '@playwright/test';

const MOCK_PROBLEMS = [
  {
    id: 'two-sum',
    slug: 'two-sum',
    title: 'Two Sum',
    difficulty: 'EASY',
    tags: ['Array', 'Hash Table'],
    solvedCount: 8450,
    acceptanceRate: 0.49,
  },
  {
    id: 'add-two-numbers',
    slug: 'add-two-numbers',
    title: 'Add Two Numbers',
    difficulty: 'MEDIUM',
    tags: ['Linked List', 'Math'],
    solvedCount: 5120,
    acceptanceRate: 0.38,
  },
  {
    id: 'median-of-two-sorted-arrays',
    slug: 'median-of-two-sorted-arrays',
    title: 'Median of Two Sorted Arrays',
    difficulty: 'HARD',
    tags: ['Array', 'Binary Search'],
    solvedCount: 1230,
    acceptanceRate: 0.35,
  },
];

async function mockLoginAndNavigate(page: any) {
  // Mock auth check so the problems page doesn't redirect to login
  await page.route('**/api/v1/auth/me', route =>
    route.fulfill({
      status: 200,
      body: JSON.stringify({
        id: 'user-1',
        email: 'test@test.com',
        displayName: 'Test User',
        roles: ['ROLE_USER'],
      }),
    })
  );

  // Mock problems list endpoint
  await page.route('**/api/v1/problems**', route => {
    const url = new URL(route.request().url());
    const difficulty = url.searchParams.get('difficulty');
    const filtered = difficulty
      ? MOCK_PROBLEMS.filter(p => p.difficulty === difficulty.toUpperCase())
      : MOCK_PROBLEMS;
    route.fulfill({
      status: 200,
      body: JSON.stringify({
        content: filtered,
        totalElements: filtered.length,
        totalPages: 1,
        number: 0,
        size: 20,
      }),
    });
  });
}

test.describe('Problems List Page', () => {
  test.beforeEach(async ({ page }) => {
    await mockLoginAndNavigate(page);
    await page.goto('/problems');
  });

  test('should display problem list with title and difficulty badge', async ({ page }) => {
    await expect(page.getByText('Two Sum')).toBeVisible();
    await expect(page.getByText('Add Two Numbers')).toBeVisible();
    await expect(page.getByText('Median of Two Sorted Arrays')).toBeVisible();
  });

  test('should display difficulty badges', async ({ page }) => {
    // Each problem row should have a difficulty indicator
    await expect(page.getByText('Easy').or(page.getByText('EASY'))).toBeVisible();
    await expect(page.getByText('Medium').or(page.getByText('MEDIUM'))).toBeVisible();
    await expect(page.getByText('Hard').or(page.getByText('HARD'))).toBeVisible();
  });

  test('should display links to individual problems', async ({ page }) => {
    const twoSumLink = page.getByRole('link', { name: /Two Sum/i });
    await expect(twoSumLink).toBeVisible();
    const href = await twoSumLink.getAttribute('href');
    expect(href).toContain('two-sum');
  });

  test('should display difficulty filter buttons', async ({ page }) => {
    // Filters may be buttons or tab-like elements
    await expect(
      page.getByRole('button', { name: /ALL/i })
        .or(page.getByText('ALL'))
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /EASY/i })
        .or(page.getByText('EASY'))
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /MEDIUM/i })
        .or(page.getByText('MEDIUM'))
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: /HARD/i })
        .or(page.getByText('HARD'))
    ).toBeVisible();
  });

  test('clicking EASY filter should show only easy problems', async ({ page }) => {
    // Click the EASY difficulty filter
    const easyFilter = page.getByRole('button', { name: /EASY/i }).or(
      page.getByRole('tab', { name: /EASY/i })
    );
    await easyFilter.first().click();

    // Only easy problems should be visible
    await expect(page.getByText('Two Sum')).toBeVisible();
    await expect(page.getByText('Add Two Numbers')).not.toBeVisible();
    await expect(page.getByText('Median of Two Sorted Arrays')).not.toBeVisible();
  });

  test('should show problem count or pagination info', async ({ page }) => {
    // Some indicator of total results
    await expect(
      page.getByText(/3 problems/i)
        .or(page.getByText(/showing/i))
        .or(page.getByText(/1 - 3/i))
    ).toBeVisible();
  });
});
