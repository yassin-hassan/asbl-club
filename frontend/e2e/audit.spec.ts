import { expect, test } from '@playwright/test';

test("an administrator reads their association's audit log, without IP addresses", async ({ page }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);
  const email = `e2e-audit-${stamp}@club.test`;

  // A fresh user and association (the demo database is shared by every run).
  await page.goto('/register');
  await page.getByLabel('Name').fill('Secretary');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Audited Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();

  await page.getByRole('link', { name: 'Audit log' }).click();
  await expect(page.getByRole('row', { name: new RegExp(`${email}.*ASBL_CREATED`) })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'IP address' })).toHaveCount(0);
});

test('a platform super-administrator reads the whole audit log, with IP addresses', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('admin@demo.asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();

  await page.getByRole('link', { name: 'Platform audit log' }).click();
  await expect(page.getByRole('heading', { name: 'Platform audit log' })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: 'IP address' })).toBeVisible();
  await expect(page.getByRole('row').nth(1)).toBeVisible(); // at least this login
});

test('an ordinary user is refused the platform audit log', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Platform audit log' })).toHaveCount(0);

  await page.goto('/admin/audit');
  await expect(page.getByRole('alert')).toHaveText('Only administrators can read this log.');
});
