import { expect, test } from '@playwright/test';

// A tab left open during a deploy: its next page's code file no longer exists, and the site answers with its
// HTML page instead. The app must recover on its own (reload into the new version), not hang.
test('a tab left open during a deploy recovers when the next page needs code that is gone', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible();

  // "The deploy": the next code file requested gets the HTML page, as Cloudflare's fallback would serve it.
  let brokenOnce = false;
  await page.route(/\/chunk-[A-Za-z0-9]+\.js$/, async (route) => {
    if (brokenOnce) {
      return route.fallback();
    }
    brokenOnce = true;
    return route.fulfill({ contentType: 'text/html', body: '<!doctype html><html><body></body></html>' });
  });

  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();

  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  expect(brokenOnce).toBe(true);
});
