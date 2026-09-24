import { expect, Page, test } from '@playwright/test';

async function logIn(page: Page, email: string, password = 'password123') {
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}

test('the dashboard lists my associations and my role', async ({ page }) => {
  await logIn(page, 'demo@asbl.club');

  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Club Démo.*Administrator/ })).toBeVisible();
});

test('a user closes their account: confirmation, logged out, and the password no longer works', async ({ page }) => {
  const email = `e2e-delete-${Date.now()}@club.test`;
  await page.goto('/register');
  await page.getByLabel('Name').fill('To Be Deleted');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();

  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'My account' }).click();

  // Changing one's mind is possible...
  await page.getByRole('button', { name: 'Delete my account' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('heading', { name: 'My account', exact: true })).toBeVisible();

  // ...confirming is final.
  await page.getByRole('button', { name: 'Delete my account' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete permanently' }).click();

  await expect(page).toHaveURL(/\/login\?deleted=1$/);
  await expect(page.getByRole('status')).toHaveText('Your account has been deleted');

  await logIn(page, email);
  await expect(page.getByRole('alert')).toHaveText('Invalid email or password');
});
