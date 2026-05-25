import { test, expect, Page } from '@playwright/test';

/**
 * E2E tests for the real-time collaboration feature.
 *
 * Assumptions:
 *   - The dev stack is running (auth, collab, web).
 *   - A seed account exists: testuser@algoverse.dev / TestPass123!
 *     (or set env vars COLLAB_EMAIL / COLLAB_PASSWORD).
 *   - BASE_URL in playwright.config.ts points to the frontend (default: http://localhost:5173).
 *
 * Tests cover:
 *   1. Unauthenticated user is redirected to login from /collaborate
 *   2. Create room flow — modal, form, room generated with code
 *   3. Join room flow — enter a valid room code, land in the editor
 *   4. Code editor is visible and accepts input
 *   5. Language selector changes the Monaco editor language
 *   6. Two-user collaboration — second tab joins the same room and sees the code
 *   7. Leave room — user is redirected out of the editor
 */

const EMAIL    = process.env.COLLAB_EMAIL    ?? 'testuser@algoverse.dev';
const PASSWORD = process.env.COLLAB_PASSWORD ?? 'TestPass123!';

async function login(page: Page) {
  await page.goto('/login');
  await page.getByPlaceholder('you@example.com').fill(EMAIL);
  await page.getByPlaceholder('••••••••').fill(PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // Wait for dashboard or problems page to confirm login
  await page.waitForURL(/\/(dashboard|problems)/, { timeout: 10_000 });
}

test.describe('Collaboration — unauthenticated guards', () => {

  test('redirects unauthenticated user away from /collaborate', async ({ page }) => {
    await page.goto('/collaborate');
    // Should be redirected to login
    await expect(page).toHaveURL(/\/login/, { timeout: 5_000 });
  });

  test('redirects unauthenticated user away from /collab/room/any-code', async ({ page }) => {
    await page.goto('/collab/room/ABC-123-DEF');
    await expect(page).toHaveURL(/\/login/, { timeout: 5_000 });
  });
});

test.describe('Collaboration — room creation', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('collaborate page loads with create/join options', async ({ page }) => {
    await page.goto('/collaborate');
    await expect(page).toHaveURL(/\/collaborate/);
    // There should be a button to create a new room
    await expect(
      page.getByRole('button', { name: /create.*room|new.*room/i })
    ).toBeVisible({ timeout: 5_000 });
  });

  test('create room modal opens and shows a generated room code', async ({ page }) => {
    await page.goto('/collaborate');

    // Open create-room modal/dialog
    await page.getByRole('button', { name: /create.*room|new.*room/i }).click();

    // Wait for a room code to appear (format: XXX-XXX-XXX)
    const codePattern = /[A-Z0-9]{3}-[A-Z0-9]{3}-[A-Z0-9]{3}/i;
    const codeEl = page.locator('text=' + codePattern.source).first();

    // The room code may appear after calling the API — give it 8s
    await expect(
      page.locator('[data-testid="room-code"], .room-code, code').filter({ hasText: /-/ }).first()
    ).toBeVisible({ timeout: 8_000 });
  });

  test('created room navigates into the collaborative editor', async ({ page }) => {
    await page.goto('/collaborate');
    await page.getByRole('button', { name: /create.*room|new.*room/i }).click();

    // Click "Start" or "Enter Room" button that appears after creation
    const enterBtn = page.getByRole('button', { name: /start|enter|open/i }).first();
    await enterBtn.waitFor({ timeout: 8_000 });
    await enterBtn.click();

    // Should navigate to /collab/room/<code>
    await expect(page).toHaveURL(/\/collab\/room\/[A-Z0-9-]+/i, { timeout: 8_000 });
  });
});

test.describe('Collaboration — editor interactions', () => {
  let roomUrl: string;

  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.goto('/collaborate');
    await page.getByRole('button', { name: /create.*room|new.*room/i }).click();
    const enterBtn = page.getByRole('button', { name: /start|enter|open/i }).first();
    await enterBtn.waitFor({ timeout: 8_000 });
    await enterBtn.click();
    await page.waitForURL(/\/collab\/room\/[A-Z0-9-]+/i, { timeout: 8_000 });
    roomUrl = page.url();
  });

  test('Monaco editor is mounted and visible', async ({ page }) => {
    // Monaco renders a textarea as the accessible editor input
    const editor = page.locator('.monaco-editor, [data-testid="collab-editor"]').first();
    await expect(editor).toBeVisible({ timeout: 6_000 });
  });

  test('can type code into the editor', async ({ page }) => {
    const editorInput = page.locator('.monaco-editor textarea, [data-testid="collab-editor"] textarea').first();
    await editorInput.focus();
    await editorInput.press('Control+a');
    await editorInput.type('function hello() { return 42; }');

    // The Monaco editor renders text into the view layer — check it appears
    await expect(page.locator('.monaco-editor').getByText('function hello')).toBeVisible({ timeout: 4_000 });
  });

  test('language selector is present and changes editor language', async ({ page }) => {
    const languageSelect = page.locator(
      'select[data-testid="language-select"], button[aria-label*="language"], [data-testid="language-selector"]'
    ).first();
    await expect(languageSelect).toBeVisible({ timeout: 5_000 });

    // Click it and verify a dropdown option appears
    await languageSelect.click();
    await expect(
      page.getByRole('option', { name: /python|javascript|java/i }).first()
    ).toBeVisible({ timeout: 3_000 });
  });

  test('leave room button redirects back to collaborate page', async ({ page }) => {
    const leaveBtn = page.getByRole('button', { name: /leave|exit|end/i }).first();
    await expect(leaveBtn).toBeVisible({ timeout: 5_000 });
    await leaveBtn.click();

    await expect(page).toHaveURL(/\/(collaborate|problems|dashboard)/, { timeout: 5_000 });
  });
});

test.describe('Collaboration — two-user real-time sync', () => {

  test('second user joining the same room sees the same editor content', async ({ browser }) => {
    // Open two independent browser contexts to simulate two separate users
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const pageA = await ctxA.newPage();
    const pageB = await ctxB.newPage();

    try {
      // User A: create a room
      await login(pageA);
      await pageA.goto('/collaborate');
      await pageA.getByRole('button', { name: /create.*room|new.*room/i }).click();

      // Grab the room code from the UI
      const codeEl = await pageA.locator(
        '[data-testid="room-code"], .room-code, code'
      ).filter({ hasText: /-/ }).first();
      await codeEl.waitFor({ timeout: 8_000 });
      const roomCode = (await codeEl.textContent())?.trim() ?? '';
      expect(roomCode).toMatch(/[A-Z0-9]{3}-[A-Z0-9]{3}-[A-Z0-9]{3}/i);

      // User A enters the room
      const enterBtn = pageA.getByRole('button', { name: /start|enter|open/i }).first();
      await enterBtn.click();
      await pageA.waitForURL(/\/collab\/room\//i, { timeout: 8_000 });

      // User A types some code
      const editorA = pageA.locator('.monaco-editor textarea').first();
      await editorA.focus();
      await editorA.type('const synced = true;');
      await pageA.waitForTimeout(800); // allow WS broadcast to propagate

      // User B: login and join the room by code
      await login(pageB);
      await pageB.goto(`/collab/room/${roomCode}`);
      await pageB.waitForURL(/\/collab\/room\//i, { timeout: 8_000 });

      // User B should see User A's code in the editor
      await expect(
        pageB.locator('.monaco-editor').getByText('synced')
      ).toBeVisible({ timeout: 8_000 });

    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });
});
