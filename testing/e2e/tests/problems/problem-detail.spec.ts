import { test, expect } from '@playwright/test';

const MOCK_PROBLEM = {
  id: 'two-sum',
  slug: 'two-sum',
  title: 'Two Sum',
  difficulty: 'EASY',
  tags: ['Array', 'Hash Table'],
  description: `Given an array of integers \`nums\` and an integer \`target\`, return indices of the two numbers such that they add up to target.

You may assume that each input would have exactly one solution, and you may not use the same element twice.

**Example 1:**
\`\`\`
Input: nums = [2,7,11,15], target = 9
Output: [0,1]
\`\`\``,
  constraints: ['2 <= nums.length <= 10^4', '-10^9 <= nums[i] <= 10^9'],
  starterCode: {
    python: 'def twoSum(self, nums: List[int], target: int) -> List[int]:\n    pass',
    java: 'public int[] twoSum(int[] nums, int target) {\n    // your code here\n}',
    javascript: '/**\n * @param {number[]} nums\n * @param {number} target\n * @return {number[]}\n */\nvar twoSum = function(nums, target) {\n\n};',
  },
};

const MOCK_AI_HINT = {
  hint: 'Consider using a hash map to store the complement of each number as you iterate.',
  level: 1,
};

test.describe('Problem Detail Page', () => {
  test.beforeEach(async ({ page }) => {
    // Mock auth
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

    // Mock problem detail endpoint
    await page.route('**/api/v1/problems/two-sum', route =>
      route.fulfill({
        status: 200,
        body: JSON.stringify(MOCK_PROBLEM),
      })
    );

    // Mock AI hint endpoint
    await page.route('**/api/v1/hints**', route =>
      route.fulfill({
        status: 200,
        body: JSON.stringify(MOCK_AI_HINT),
      })
    );

    // Mock code execution endpoint
    await page.route('**/api/v1/execute', route =>
      route.fulfill({
        status: 200,
        body: JSON.stringify({
          status: 'ACCEPTED',
          runtime: '64 ms',
          memory: '41.2 MB',
          testCasesPassed: 3,
          testCasesTotal: 3,
        }),
      })
    );

    await page.goto('/problems/two-sum');
  });

  test('should display problem title', async ({ page }) => {
    await expect(page.getByText('Two Sum')).toBeVisible();
  });

  test('should display problem description panel', async ({ page }) => {
    // Description text from mock
    await expect(
      page.getByText(/Given an array of integers/i)
    ).toBeVisible();
  });

  test('should render Monaco editor container', async ({ page }) => {
    // Monaco editor mounts into a .monaco-editor container
    // Wait for it to appear (may take a moment to hydrate)
    await expect(
      page.locator('.monaco-editor')
        .or(page.locator('[data-testid="code-editor"]'))
        .or(page.locator('[class*="editor"]'))
    ).toBeVisible({ timeout: 10000 });
  });

  test('should display Run Code button', async ({ page }) => {
    await expect(
      page.getByRole('button', { name: /Run/i })
        .or(page.getByRole('button', { name: /Run Code/i }))
    ).toBeVisible();
  });

  test('should display Submit button', async ({ page }) => {
    await expect(
      page.getByRole('button', { name: /Submit/i })
    ).toBeVisible();
  });

  test('should display AI hint panel or button', async ({ page }) => {
    // The AI panel may be a sidebar, a tab, or a toggle button
    await expect(
      page.getByText(/AI/i)
        .or(page.getByText(/Hint/i))
        .or(page.getByRole('button', { name: /hint/i }))
        .or(page.locator('[data-testid="ai-panel"]'))
    ).toBeVisible();
  });

  test('should show difficulty badge', async ({ page }) => {
    await expect(
      page.getByText('Easy').or(page.getByText('EASY'))
    ).toBeVisible();
  });

  test('should display tags', async ({ page }) => {
    await expect(
      page.getByText('Array').or(page.getByText('Hash Table'))
    ).toBeVisible();
  });

  test('Run Code button triggers execution and shows result', async ({ page }) => {
    const runButton = page.getByRole('button', { name: /Run Code/i })
      .or(page.getByRole('button', { name: /Run/i }));
    await runButton.first().click();
    // Expect some output panel or result indicator to appear
    await expect(
      page.getByText(/ACCEPTED/i)
        .or(page.getByText(/Passed/i))
        .or(page.locator('[data-testid="output-panel"]'))
    ).toBeVisible({ timeout: 10000 });
  });
});
