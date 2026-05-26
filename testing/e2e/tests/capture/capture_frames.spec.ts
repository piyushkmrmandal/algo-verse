import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';

const FRAMES_DIR = '/tmp/algoverse-video/frames';
const AUTH_STATE = JSON.stringify({
  state: {
    user: {
      id: '550e8400-e29b-41d4-a716-446655440000',
      email: 'alex@algoverse.io',
      displayName: 'Alex Chen',
      avatarUrl: null,
      role: 'USER',
      isEmailVerified: true,
    },
    accessToken: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.demo',
    refreshToken: 'demo-refresh-token',
    isAuthenticated: true,
  },
  version: 0,
});

test.beforeAll(() => {
  fs.mkdirSync(FRAMES_DIR, { recursive: true });
});

async function injectAuth(page: any) {
  await page.evaluate((s: string) => localStorage.setItem('algoverse-auth', s), AUTH_STATE);
}

async function clearAuth(page: any) {
  await page.evaluate(() => localStorage.removeItem('algoverse-auth'));
}

test.describe.serial('AlgoVerse demo capture', () => {
  test('01 - Landing page (authenticated)', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.reload();
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '01_landing.png') });
  });

  test('02 - Login page', async ({ page }) => {
    await page.goto('/');
    await clearAuth(page);
    await page.goto('/login', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '02_login.png') });
  });

  test('03 - Register page', async ({ page }) => {
    await page.goto('/register', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '03_register.png') });
  });

  test('04 - Problems list', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/problems', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(FRAMES_DIR, '04_problems.png') });
  });

  test('05 - Problem detail (two-sum)', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/problems/two-sum', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '05_problem_detail.png') });
  });

  test('06 - Dashboard', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/dashboard', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(FRAMES_DIR, '06_dashboard.png') });
  });

  test('07 - Collaborate lobby', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/collaborate', { waitUntil: 'networkidle' });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '07_collaborate.png') });
  });

  test('08 - Collab room', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/collab/room/DEMO01', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(FRAMES_DIR, '08_collab_room.png') });
  });

  test('09 - System Design list', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/sysdesign', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(FRAMES_DIR, '09_sysdesign_list.png') });
  });

  test('10 - System Design problem', async ({ page }) => {
    await page.goto('/');
    await injectAuth(page);
    await page.goto('/sysdesign/design-twitter', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);
    await page.screenshot({ path: path.join(FRAMES_DIR, '10_sysdesign_problem.png') });
  });
});
