import { expect, test } from '@playwright/test';

// Visitors arrive through the link the association shares (its events page).
test('a visitor opens an association\'s events and sees one with live seat availability', async ({ page }) => {
  await page.goto('/asbls/club-demo/events');

  await expect(page.getByRole('heading', { name: 'Events for "Club Démo"' })).toBeVisible();
  await page.getByRole('link', { name: /Concert de gala/ }).click();

  await expect(page.getByText('Concert de gala')).toBeVisible();
  await expect(page.getByText('Organised by Club Démo')).toBeVisible();
  await expect(page.getByText('Bruxelles')).toBeVisible();
  const standardTicket = page.getByRole('row', { name: /Place standard/ });
  await expect(standardTicket).toContainText('€12.00');
  await expect(standardTicket).toContainText(/\d+/); // seats left, then refreshed every 5 s
  await expect(page.getByRole('link', { name: 'WhatsApp' })).toHaveAttribute('href', /^https:\/\/wa\.me\/\?text=Concert/);
});

test('an unknown association shows a readable error', async ({ page }) => {
  await page.goto('/asbls/does-not-exist/events');

  await expect(page.getByRole('alert')).toHaveText('Not found.');
});

// A visitor who lands on the home page finds the events of every association, and books from there.
test('a visitor goes from the home page to the upcoming events, then to one of them', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: 'See upcoming events' }).click();

  await expect(page).toHaveURL(/\/events$/);
  await expect(page.getByRole('heading', { name: 'Upcoming events' })).toBeVisible();
  const concert = page.getByRole('link', { name: /Concert de gala/ }).first();
  await expect(concert).toContainText('Club Démo');
  await concert.click();

  await expect(page.getByText('Organised by Club Démo')).toBeVisible();
  await expect(page.getByRole('row', { name: /Place standard/ }).getByRole('button', { name: 'Book' })).toBeVisible();

  // The same list from the menu, on any page.
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: "What's on" }).click();
  await expect(page.getByRole('heading', { name: 'Upcoming events' })).toBeVisible();
});
