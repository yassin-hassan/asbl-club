import { expect, test } from '@playwright/test';
import { createAccount } from './mail';

// An association sets until when buyers may cancel (a week by default); the public page tells it before buying; and
// once a seat is taken, the delay can be made longer but not shorter. Cancelling a paid ticket needs a real Stripe
// payment (not possible in CI): the backend tests cover it.
test('the cancellation policy is set per event, shown before buying, and can only grow once a seat is taken', async ({ page }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  await page.goto('/register');
  await page.getByLabel('Name').fill('Paula Policy');
  await page.getByLabel('Email address').fill(`e2e-policy-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await createAccount(page);
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Policy Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();
  await page.getByRole('link', { name: 'Events' }).click();
  await page.getByRole('link', { name: 'Create an event' }).click();
  await page.getByLabel('Title').fill('Spring concert');
  await page.getByLabel('Start').fill('2027-04-20T20:00');
  await expect(page.getByLabel('Cancellation by the buyer (days before the event)')).toHaveValue('7');
  await page.getByRole('button', { name: 'Create event' }).click();
  await expect(page.getByText('Refundable until 7 day(s) before the start')).toBeVisible();

  await page.getByLabel('Label').fill('Standard');
  await page.getByLabel('Price').fill('12');
  await page.getByLabel('Seats').fill('50');
  await page.getByRole('button', { name: 'Add a ticket category' }).click();
  await page.getByRole('button', { name: 'Publish' }).click();
  const eventUrl = page.url();

  // Before buying: the policy, with its date (7 days before 20 April).
  await page.getByRole('link', { name: 'View the public page' }).click();
  await expect(page.getByText('Tickets can be cancelled and refunded until April 13, 2027.')).toBeVisible();

  // A seat taken (booked, not paid: the association has no Stripe account): shorter is refused, longer is fine.
  await page.goto(eventUrl);
  await page.getByRole('row', { name: /Standard/ }).getByRole('button', { name: 'Book' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);
  await page.goto(`${eventUrl}/edit`);
  const delay = page.getByLabel('Cancellation by the buyer (days before the event)');
  await expect(delay).toHaveValue('7');
  await delay.fill('2');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Tickets are already sold: the cancellation delay can be made longer, but not shorter.'))
    .toBeVisible();
  await delay.fill('14');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Refundable until 14 day(s) before the start')).toBeVisible();
});
