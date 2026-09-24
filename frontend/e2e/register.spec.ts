import { expect, test } from '@playwright/test';

test('a visitor creates an account from the landing page and is logged straight in', async ({ page }) => {
  const email = `e2e-${Date.now()}@club.test`; // the demo database is shared by every run

  await page.goto('/');
  await page.getByRole('link', { name: 'Create my ASBL for free' }).first().click();
  await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible();

  await page.getByLabel('Name').fill('E2E Visitor');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();

  // Logged in straight away, on the dashboard: a brand-new account belongs to no association yet.
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await expect(page.getByText('You have no associations yet')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Main navigation' })).toContainText(email);
});

test('an email that already has an account is flagged on the email field', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('Name').fill('Someone');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('This email address is already in use')).toBeVisible();
  await expect(page).toHaveURL(/\/register$/);
});

test('the login page links to sign-up', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create an account' }).click();

  await expect(page).toHaveURL(/\/register$/);
});
