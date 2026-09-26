import { expect, test } from '@playwright/test';

// A real ticket needs a real Stripe payment (not possible in CI): the backend tests cover paid tickets and
// check-in. Here: the pages, their wiring through the Worker, and the refusals.
test('my bookings lists an unpaid booking with a way to pay, and the door refuses an unknown ticket', async ({ page }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  await page.goto('/register');
  await page.getByLabel('Name').fill('Tess Ticket');
  await page.getByLabel('Email address').fill(`e2e-tickets-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Tickets Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();
  await page.getByRole('link', { name: 'Events' }).click();
  await page.getByRole('link', { name: 'Create an event' }).click();
  await page.getByLabel('Title').fill('Winter ball');
  await page.getByLabel('Start').fill('2027-01-15T20:00');
  await page.getByRole('button', { name: 'Create event' }).click();
  await page.getByLabel('Label').fill('Dancer');
  await page.getByLabel('Price').fill('15');
  await page.getByLabel('Seats').fill('100');
  await page.getByRole('button', { name: 'Add a ticket category' }).click();
  await page.getByRole('button', { name: 'Publish' }).click();
  const eventUrl = page.url();
  await page.getByRole('row', { name: /Dancer/ }).getByRole('button', { name: 'Book' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);

  // My bookings: upcoming, awaiting payment, no ticket yet, a way to pay.
  await page.getByRole('link', { name: 'My bookings' }).click();
  const booking = page.getByRole('article', { name: 'Winter ball' });
  await expect(booking).toContainText(`Tickets Club ${stamp}`);
  await expect(booking).toContainText('Dancer · €15.00');
  await expect(booking).toContainText('Awaiting payment');
  await expect(booking.getByRole('img')).toHaveCount(0); // no QR code before paying
  await booking.getByRole('link', { name: 'Pay' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);

  // The door: an unknown code is refused, and the field is ready for the next one.
  await page.goto(eventUrl);
  await page.getByRole('link', { name: 'Check-in' }).click();
  const code = page.getByLabel('Ticket code');
  await expect(code).toBeFocused();
  await code.fill('0000 1111 2222 3333');
  await code.press('Enter'); // what a handheld scanner does after typing the code
  await expect(page.getByText('Unknown ticket for this event.')).toBeVisible();
  await expect(code).toHaveValue('');
  await expect(code).toBeFocused();
});
