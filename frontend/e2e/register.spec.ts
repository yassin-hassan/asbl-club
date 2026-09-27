import { expect, test } from '@playwright/test';
import { createAccount, lastSubjectTo } from './mail';

test('a visitor signs up from the landing page, confirms their email, and is logged in', async ({ page }) => {
  const email = `e2e-${Date.now()}@club.test`; // the demo database is shared by every run

  await page.goto('/');
  await page.getByRole('link', { name: 'Create my ASBL for free' }).first().click();
  await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible();

  await page.getByLabel('Name').fill('E2E Visitor');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await createAccount(page); // "Check your inbox", then the link from the email

  // Logged in by the link, on the dashboard: a brand-new account belongs to no association yet.
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await expect(page.getByText('You have no associations yet')).toBeVisible();
  await expect(
    page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'My account' }),
  ).toHaveAttribute('title', email);
});

test('logging in before confirming says so, and can send the link again', async ({ page }) => {
  const email = `e2e-unconfirmed-${Date.now()}@club.test`;
  await page.goto('/register');
  await page.getByLabel('Name').fill('Not Yet');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Check your inbox')).toBeVisible();

  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toHaveText('Confirm your email address first: open the link we sent you.');
  await page.getByRole('button', { name: 'Send the link again' }).click();
  await expect(page.getByText("We've sent the link again.")).toBeVisible();
});

// Account enumeration: the page answers the same for an address that already has an account; its owner gets an
// email instead ("you already have an account").
test('an address that already has an account gets the same answer', async ({ page }) => {
  await page.goto('/register');
  await page.getByLabel('Name').fill('Someone');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page.getByText('Check your inbox')).toBeVisible();
  await expect.poll(() => lastSubjectTo('demo@asbl.club'), { timeout: 20_000 })
    .toBe('You already have an asbl.club account');
});

test('the login page links to sign-up', async ({ page }) => {
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create an account' }).click();

  await expect(page).toHaveURL(/\/register$/);
});
