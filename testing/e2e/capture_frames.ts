import { chromium } from '@playwright/test';
import path from 'path';

const FRAMES_DIR = '/tmp/algoverse-video/frames';
const BASE_URL = 'http://localhost:5173';

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

async function capture(
  page: any,
  filename: string,
  url: string,
  opts: { clearAuth?: boolean; waitMs?: number } = {}
) {
  if (opts.clearAuth) {
    await page.evaluate(() => localStorage.removeItem('algoverse-auth'));
  } else {
    await page.evaluate((s: string) => localStorage.setItem('algoverse-auth', s), AUTH_STATE);
  }
  await page.goto(BASE_URL + url, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(opts.waitMs ?? 2000);
  const out = path.join(FRAMES_DIR, filename);
  await page.screenshot({ path: out, fullPage: false });
  console.log(`✓ ${filename}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage();

  // Seed localStorage once
  await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' }).catch(() => {});
  await page.evaluate((s: string) => localStorage.setItem('algoverse-auth', s), AUTH_STATE);

  // 01 Landing — authenticated
  await capture(page, '01_landing.png', '/', { waitMs: 2500 });

  // 02 Login — guest
  await capture(page, '02_login.png', '/login', { clearAuth: true, waitMs: 1500 });

  // 03 Register — guest
  await capture(page, '03_register.png', '/register', { waitMs: 1500 });

  // Re-inject auth for protected pages
  await page.evaluate((s: string) => localStorage.setItem('algoverse-auth', s), AUTH_STATE);

  // 04 Problems list
  await capture(page, '04_problems.png', '/problems', { waitMs: 2000 });

  // 05 Problem detail
  await capture(page, '05_problem_detail.png', '/problems/two-sum', { waitMs: 2500 });

  // 06 Dashboard
  await capture(page, '06_dashboard.png', '/dashboard', { waitMs: 2000 });

  // 07 Collaborate lobby
  await capture(page, '07_collaborate.png', '/collaborate', { waitMs: 1500 });

  // 08 Collab room
  await capture(page, '08_collab_room.png', '/collab/room/DEMO01', { waitMs: 2000 });

  // 09 System Design list
  await capture(page, '09_sysdesign_list.png', '/sysdesign', { waitMs: 2000 });

  // 10 System Design problem
  await capture(page, '10_sysdesign_problem.png', '/sysdesign/design-twitter', { waitMs: 2000 });

  await browser.close();
  console.log('\nAll frames captured!');
})();
