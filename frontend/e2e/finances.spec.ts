import { expect, test } from '@playwright/test';

// An association's finances. Real payments need Stripe (not possible in CI): the backend tests cover the totals, the
// refunds and the spreadsheet; here, the tab itself.
test('an administrator opens the finances: the year, its totals, and the tab kept in the address', async ({ page }) => {
  const year = new Date().getFullYear();
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();

  await page.goto('/asbls/club-demo/members');
  await expect(page.getByRole('link', { name: 'Stripe account' })).toBeVisible();
  await page.getByRole('tab', { name: 'Finances' }).click();
  await expect(page).toHaveURL(/\/asbls\/club-demo\/members\?tab=finances$/);

  const finances = page.getByRole('region', { name: 'Finances' });
  await expect(finances.getByLabel('Year', { exact: true })).toHaveValue(String(year));
  await expect(finances.getByRole('definition').first()).toHaveText('€0.00');
  await expect(finances).toContainText('Net for the association');
  await expect(finances).toContainText(`No payments in ${year}.`);
  await expect(finances.getByRole('button', { name: 'Download the year (CSV)' })).toBeDisabled();

  await page.reload();
  await expect(page.getByRole('tab', { name: 'Finances' })).toHaveAttribute('aria-selected', 'true');
});
