import { expect, test } from '@playwright/test';

test('a visitor finds an association and sees an event with live seat availability', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Association').fill('club-demo');
  await page.getByRole('link', { name: 'View events' }).click();

  await expect(page.getByRole('heading', { name: 'Events for "club-demo"' })).toBeVisible();
  await page.getByRole('link', { name: /Concert de gala/ }).click();

  await expect(page.getByText('Concert de gala')).toBeVisible();
  await expect(page.getByText(/\d+ left/)).toBeVisible(); // polled from the API every 5 s
});

test('an unknown association shows a readable error', async ({ page }) => {
  await page.goto('/asbls/does-not-exist/events');

  await expect(page.getByRole('alert')).toHaveText('Not found.');
});
