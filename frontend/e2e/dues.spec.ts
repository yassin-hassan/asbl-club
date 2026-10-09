import { readFileSync } from 'node:fs';
import { expect, Page, test } from '@playwright/test';

// Membership dues. Paying for real needs Stripe (not possible in CI): the backend tests cover the payment, its
// webhook and the receipt; here, the journey around it.

async function logInAsDemoAdmin(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
}

test('an administrator sets the yearly fee, members see it on their dashboard with a way to pay', async ({ page }) => {
  const year = new Date().getFullYear();
  await logInAsDemoAdmin(page);

  await page.goto('/asbls/club-demo/members?tab=dues'); // the open tab is kept in the address
  const dues = page.getByRole('region', { name: 'Membership dues' });
  await dues.getByLabel('Yearly fee').fill('25');
  await dues.getByRole('button', { name: 'Save' }).click();
  await expect(dues.getByRole('status')).toHaveText('Saved.');
  await expect(dues).toContainText(`Members pay €25.00 for ${year}.`);

  // Who paid: every current member (nobody yet), and the same list as a spreadsheet.
  await expect(dues).toContainText(new RegExp(`0 of \\d+ members have paid for ${year}\\.`));
  const list = dues.getByRole('table', { name: 'Who paid their dues' });
  await expect(list.getByRole('row', { name: /demo@asbl\.club/ })).toContainText('Unpaid');
  const downloading = page.waitForEvent('download');
  await dues.getByRole('button', { name: 'Download (CSV)' }).click();
  const file = await downloading;
  expect(file.suggestedFilename()).toBe(`dues-club-demo-${year}.csv`);
  const csv = readFileSync(await file.path(), 'utf8');
  expect(csv).toMatch(/^\uFEFFName;Email;Dues;Amount \(EUR\);Paid at\r\n/);
  expect(csv).toContain(';demo@asbl.club;Unpaid;;');

  // The administrator is a member too: this year's dues are on their dashboard.
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Your dues' })).toBeVisible();
  const row = page.locator('.row-link', { hasText: `Dues ${year}` }).filter({ hasText: 'Club Démo' });
  await expect(row).toContainText(`Dues ${year} · €25.00`);
  await row.getByRole('link', { name: 'Pay' }).click();
  await expect(page).toHaveURL(/\/asbls\/club-demo\/dues\/pay$/);
  await expect(page.getByRole('heading', { name: 'Payment' })).toBeVisible();

  // Stop collecting: the dues leave the dashboard.
  await page.goto('/asbls/club-demo/members?tab=dues');
  await dues.getByRole('button', { name: 'Stop collecting dues' }).click();
  await expect(dues).toContainText("The association doesn't collect dues yet.");
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Your dues' })).toHaveCount(0);
});

test('coming back from a failed payment offers to try again', async ({ page }) => {
  await logInAsDemoAdmin(page);
  await page.goto('/asbls/club-demo/dues/paid?redirect_status=failed');

  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Try again' })).toHaveAttribute('href', '/asbls/club-demo/dues/pay');
});
