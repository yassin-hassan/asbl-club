import { Browser, expect, Page, test } from '@playwright/test';

async function signUp(page: Page, name: string, email: string) {
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
}

async function newPerson(browser: Browser): Promise<Page> {
  const context = await browser.newContext({ locale: 'en-US' });
  return context.newPage();
}

test('someone joins an association through its invitation link, once an administrator approves', async ({ browser }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  // An administrator creates an association and an invitation link.
  const admin = await newPerson(browser);
  await admin.goto('/register');
  await signUp(admin, 'Founder', `e2e-founder-join-${stamp}@club.test`);
  await admin.getByRole('link', { name: 'Create an ASBL' }).click();
  await admin.getByLabel('Name').fill(`Joinable Club ${stamp}`);
  await admin.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await admin.getByRole('button', { name: 'Create ASBL' }).click();
  await expect(admin.getByRole('heading', { name: 'Invite members' })).toBeVisible();
  await admin.getByRole('button', { name: 'Create an invitation link' }).click();
  const link = (await admin.getByTestId('join-link').textContent())!.trim();
  expect(link).toMatch(/\/join\/[A-Za-z0-9_-]{43}$/);

  // Someone without an account opens it: sign up, come straight back to the link, ask to join.
  const newcomer = await newPerson(browser);
  await newcomer.goto(link);
  await expect(newcomer).toHaveURL(/\/login\?returnUrl=/);
  await newcomer.getByRole('link', { name: 'Create an account' }).click();
  await signUp(newcomer, 'Newcomer', `e2e-newcomer-${stamp}@club.test`);
  await expect(newcomer.getByRole('heading', { name: `Join Joinable Club ${stamp}` })).toBeVisible();
  await newcomer.getByRole('button', { name: 'Ask to join' }).click();
  await expect(newcomer.getByRole('status')).toContainText('Request sent');

  // Pending: listed on the dashboard, but nothing to open yet.
  await newcomer.goto('/');
  await expect(newcomer.getByText(`Joinable Club ${stamp}`)).toBeVisible();
  await expect(newcomer.getByText('Pending')).toBeVisible();
  await expect(newcomer.getByRole('link', { name: new RegExp(`Joinable Club ${stamp}`) })).toHaveCount(0);

  // The administrator approves the request.
  await admin.reload();
  await expect(admin.getByRole('heading', { name: 'Join requests' })).toBeVisible();
  await admin.getByRole('button', { name: 'Approve Newcomer' }).click();
  await expect(admin.getByRole('heading', { name: 'Join requests' })).toHaveCount(0);
  await expect(admin.getByRole('row', { name: /Newcomer.*Member.*Active/ })).toBeVisible();

  // The newcomer is in.
  await newcomer.reload();
  await newcomer.getByRole('link', { name: new RegExp(`Joinable Club ${stamp}.*Member`) }).click();
  await expect(newcomer.getByRole('heading', { name: `Joinable Club ${stamp}` })).toBeVisible();
});

test('a replaced invitation link no longer works', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();

  await page.goto('/join/this-is-not-a-real-invitation-token-000000000');
  await expect(page.getByRole('alert')).toHaveText('This invitation link no longer works. Ask the association for a new one.');
});
