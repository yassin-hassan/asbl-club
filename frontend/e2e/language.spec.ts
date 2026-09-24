import { expect, test } from '@playwright/test';

test('switching language translates the page and is remembered', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByText('Log in', { exact: true }).first()).toBeVisible();

  await page.getByRole('button', { name: 'Language' }).click();
  await page.getByRole('menuitem', { name: 'Nederlands' }).click();

  await expect(page.getByLabel('Wachtwoord')).toBeVisible();
  expect(await page.locator('html').getAttribute('lang')).toBe('nl');

  await page.reload();
  await expect(page.getByLabel('Wachtwoord')).toBeVisible();
});
