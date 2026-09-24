import { expect, Page, test } from '@playwright/test';

async function logIn(page: Page, password = 'password123') {
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}

test('login, stay logged in across a reload, log out', async ({ page }) => {
  // A protected page sends a visitor to the login page, remembering where they were going.
  await page.goto('/account');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Faccount$/);

  await logIn(page);
  await expect(page).toHaveURL(/\/account$/);
  await expect(page.getByRole('heading', { name: 'My account', exact: true })).toBeVisible();

  // The refresh token is a hardened cookie that page scripts can't read...
  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'refresh_token');
  expect(refreshCookie).toMatchObject({ httpOnly: true, secure: true, sameSite: 'Strict' });
  expect(await page.evaluate(() => document.cookie)).not.toContain('refresh_token');

  // ...and it's what keeps the user logged in when the in-memory access token is lost on reload.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'My account', exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Log in' })).toBeVisible();

  // Logged out for real: the session was revoked on the server, a reload doesn't bring it back.
  await page.goto('/account');
  await expect(page).toHaveURL(/\/login/);
});

test('a wrong password is refused without saying which field was wrong', async ({ page }) => {
  await page.goto('/login');
  await logIn(page, 'wrong-password');

  await expect(page.getByRole('alert')).toHaveText('Invalid email or password');
});
