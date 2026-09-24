import { expect, test } from '@playwright/test';

test('a visitor finds an association and sees an event with live seat availability', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Association').fill('club-demo');
  await page.getByRole('link', { name: 'View events' }).click();

  await expect(page.getByRole('heading', { name: 'Events for "club-demo"' })).toBeVisible();
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
