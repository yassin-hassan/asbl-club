import { expect, test } from '@playwright/test';
import { createAccount, linkFromEmail } from './mail';

// The whole "forgot password" path, with a real email (read from Mailpit, see mail.ts).
test('someone who forgot their password gets a link by email and chooses a new one', async ({ page }) => {
  test.setTimeout(60_000);
  const email = `e2e-forgot-${Date.now()}@club.test`;

  await page.goto('/register');
  await page.getByLabel('Name').fill('Forgetful Fred');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('the-old-password');
  await createAccount(page);
  await page.getByRole('button', { name: 'Log out' }).click();

  await page.goto('/login');
  await page.getByRole('link', { name: 'Forgot your password?' }).click();
  // The new page first: typing too early would fill the login form's email field, which is still on screen.
  await expect(page.getByRole('heading', { name: 'Forgot your password?' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Send the link' }).click();
  await expect(page.getByText("If an account exists for this address, we've sent it a link.")).toBeVisible();

  const link = await linkFromEmail(email, 'reset-password');
  await page.goto(link);
  await expect(page).toHaveURL(/\/reset-password$/); // the token is gone from the address bar
  await page.getByLabel('New password', { exact: true }).fill('the-new-password');
  await page.getByLabel('Repeat the new password').fill('the-new-password');
  await page.getByRole('button', { name: 'Save the new password' }).click();
  await expect(page.getByText('Your password has been changed')).toBeVisible();

  // The new password works; the link doesn't work twice.
  await page.goto('/login');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('the-new-password');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('button', { name: 'Log out' })).toBeVisible();
  await page.goto(link);
  await page.getByLabel('New password', { exact: true }).fill('yet-another-one');
  await page.getByLabel('Repeat the new password').fill('yet-another-one');
  await page.getByRole('button', { name: 'Save the new password' }).click();
  await expect(page.getByRole('alert')).toHaveText('This link is invalid, expired or already used.');
});

test('asking for a link for an unknown address looks exactly the same', async ({ page }) => {
  await page.goto('/forgot-password');
  await page.getByLabel('Email address').fill(`nobody-${Date.now()}@club.test`);
  await page.getByRole('button', { name: 'Send the link' }).click();
  await expect(page.getByText("If an account exists for this address, we've sent it a link.")).toBeVisible();
});
