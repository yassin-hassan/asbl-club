import { expect, test } from '@playwright/test';

test('an administrator creates a draft event, adds tickets, publishes it, and the public sees it', async ({ page }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  // A fresh user and association (the demo database is shared by every run).
  await page.goto('/register');
  await page.getByLabel('Name').fill('Organiser');
  await page.getByLabel('Email address').fill(`e2e-organiser-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Events Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();

  // Members page → the association's events → create one.
  await page.getByRole('link', { name: 'Events' }).click();
  await expect(page.getByText('No events yet')).toBeVisible();
  await page.getByRole('link', { name: 'Create an event' }).click();
  await page.getByLabel('Title').fill('Spring gala');
  await page.getByLabel('Start').fill('2026-12-01T20:00');
  await page.getByLabel('Location').fill('Namur');
  await page.getByRole('button', { name: 'Create event' }).click();

  // It starts as a draft, hidden from the public.
  await expect(page.getByRole('heading', { name: 'Spring gala' })).toBeVisible();
  await expect(page.getByText('Draft: hidden from the public until published.')).toBeVisible();

  // A ticket priced the way Belgians write it.
  await page.getByLabel('Label').fill('Adult');
  await page.getByLabel('Price').fill('12,50');
  await page.getByLabel('Seats').fill('80');
  await page.getByRole('button', { name: 'Add a ticket category' }).click();
  await expect(page.getByRole('row', { name: /Adult.*€12\.50.*0 \/ 80/ })).toBeVisible();

  await page.getByRole('button', { name: 'Publish' }).click();
  await expect(page.getByText('Draft: hidden from the public')).toBeHidden();

  await page.getByRole('link', { name: 'View the public page' }).click();
  await expect(page.getByRole('heading', { name: 'Spring gala' })).toBeVisible();
  await expect(page.getByRole('row', { name: /Adult/ })).toContainText('€12.50');

  // Booking: the seat is taken at once; paying needs the association to have connected Stripe (it hasn't).
  await page.goBack();
  await page.getByRole('row', { name: /Adult/ }).getByRole('button', { name: 'Book' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);
  await expect(page.getByRole('alert')).toHaveText("This association can't receive payments yet.");

  await page.goBack();
  await expect(page.getByRole('row', { name: /Adult.*1 \/ 80/ })).toBeVisible();
});
