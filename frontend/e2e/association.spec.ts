import { expect, test } from '@playwright/test';

test('a new user creates an association, becomes its administrator and sees its members', async ({ page }) => {
  const stamp = Date.now();
  await page.goto('/register');
  await page.getByLabel('Name').fill('Founder');
  await page.getByLabel('Email address').fill(`e2e-founder-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();

  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Club Été ${stamp}`);
  // The URL identifier is suggested from the name, without accents.
  await expect(page.getByLabel('URL identifier')).toHaveValue(`club-ete-${stamp}`);
  // BCE numbers are unique: derive one from the timestamp.
  const digits = String(stamp).slice(-10);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();

  await expect(page).toHaveURL(new RegExp(`/asbls/club-ete-${stamp}/members$`));
  await expect(page.getByRole('heading', { name: `Club Été ${stamp}` })).toBeVisible();
  await expect(page.getByRole('row', { name: /Founder.*Administrator.*Active/ })).toBeVisible();

  // Back on the dashboard, the association is listed.
  await page.getByRole('link', { name: 'Home' }).click();
  await expect(page.getByRole('link', { name: new RegExp(`Club Été ${stamp}`) })).toBeVisible();
});

test('a taken URL identifier is flagged on its field', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();

  await page.getByLabel('Name').fill('Another club');
  await page.getByLabel('BCE number').fill('0000.111.222');
  await page.getByLabel('URL identifier').fill('club-demo');
  await page.getByRole('button', { name: 'Create ASBL' }).click();

  await expect(page.getByText('This URL identifier is already taken')).toBeVisible();
});

test("the members page is closed to people who aren't members", async ({ page }) => {
  const stamp = Date.now();
  await page.goto('/register');
  await page.getByLabel('Name').fill('Outsider');
  await page.getByLabel('Email address').fill(`e2e-outsider-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();

  await page.goto('/asbls/club-demo/members');
  await expect(page.getByRole('alert')).toHaveText("You're not a member of this association.");
});
