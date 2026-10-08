import { expect, test } from '@playwright/test';
import { lastSubjectTo } from './mail';

// A visitor without an account books a ticket from the public event page: a name and an email address, then the
// booking's secret link (the page, and the email). Paying needs a real Stripe payment (not possible in CI): the
// backend tests cover a paid guest booking and its ticket email.
test('a visitor books without an account and gets the link to their booking', async ({ page }) => {
  const email = `e2e-guest-${Date.now()}@club.test`; // the e2e database is shared by every run

  await page.goto('/asbls/club-demo/events');
  await page.getByRole('link', { name: /Concert de gala/ }).click();
  await page.getByRole('row', { name: /Place standard/ }).getByRole('button', { name: 'Book' }).click();

  await page.getByLabel('Name').fill('Gaston Guest');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Continue to payment' }).click();

  // The booking's own page, reached through its secret link: no login.
  await expect(page).toHaveURL(/\/tickets\/[\w-]{20,}$/);
  await expect(page.getByRole('heading', { name: /Concert de gala/ })).toBeVisible();
  await expect(page.getByText(`Gaston Guest (${email})`)).toBeVisible();
  await expect(page.getByText('Awaiting payment')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Pay' })).toBeVisible();

  // The same link, by email.
  await expect.poll(() => lastSubjectTo(email), { timeout: 20_000 }).toBe('Your booking for Concert de gala');

  // A made-up link opens nothing.
  await page.goto('/tickets/not-a-real-link');
  await expect(page.getByText('This booking link is unknown.')).toBeVisible();
});
