import { Browser, expect, Page, test } from '@playwright/test';
import { createAccount } from './mail';

async function signUp(page: Page, name: string, email: string) {
  await page.getByLabel('Name').fill(name);
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill('password123');
  await createAccount(page);
}

async function newPerson(browser: Browser): Promise<Page> {
  return (await browser.newContext({ locale: 'en-US' })).newPage();
}

test('an administrator changes a member’s role, can’t leave as the only admin, and excludes the member', async ({ browser }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);
  const club = `Managed Club ${stamp}`;

  // An association, and a member who joined through the invitation link.
  const admin = await newPerson(browser);
  await admin.goto('/register');
  await signUp(admin, 'Chair', `e2e-chair-${stamp}@club.test`);
  await admin.getByRole('link', { name: 'Create an ASBL' }).click();
  await admin.getByLabel('Name').fill(club);
  await admin.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await admin.getByRole('button', { name: 'Create ASBL' }).click();
  await admin.getByRole('tab', { name: 'Invitations' }).click();
  await admin.getByRole('button', { name: 'Create an invitation link' }).click();
  const link = (await admin.getByTestId('join-link').textContent())!.trim();

  const member = await newPerson(browser);
  await member.goto('/register');
  await signUp(member, 'Treasurer To Be', `e2e-treasurer-${stamp}@club.test`);
  await expect(member.getByRole('heading', { name: 'Your associations' })).toBeVisible();
  await member.goto(link);
  await member.getByRole('button', { name: 'Ask to join' }).click();
  await admin.reload();
  await admin.getByRole('tab', { name: /^Join requests/ }).click();
  await admin.getByRole('button', { name: 'Approve Treasurer To Be' }).click();
  await admin.getByRole('tab', { name: /^Members/ }).click();
  await expect(admin.getByRole('row', { name: /Treasurer To Be/ })).toBeVisible();

  // A new role, seen by the member.
  await admin.getByLabel('Role of Treasurer To Be').selectOption('TREASURER');
  await member.goto('/');
  await expect(member.getByRole('link', { name: new RegExp(`${club}.*Treasurer`) })).toBeVisible();

  // The only administrator can't leave: someone must keep running the association.
  await admin.getByRole('button', { name: 'Leave the association' }).click();
  await admin.getByRole('dialog').getByRole('button', { name: 'Leave the association' }).click();
  await expect(admin.getByRole('alert')).toHaveText(
    'An association needs at least one administrator: appoint another one first.',
  );

  // Excluded: access ends at once.
  await admin.getByRole('button', { name: 'Exclude Treasurer To Be' }).click();
  await admin.getByRole('dialog').getByRole('button', { name: 'Exclude' }).click();
  await expect(admin.getByRole('row', { name: /Treasurer To Be.*Excluded/ })).toBeVisible();

  await member.reload();
  await expect(member.getByText('Excluded')).toBeVisible();
  await expect(member.getByRole('link', { name: new RegExp(club) })).toHaveCount(0);
});

// Thirty, forty members: the list can be searched, by name or email, whatever the accents.
test('an administrator finds a member with the search', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email address').fill('demo@asbl.club');
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('heading', { name: 'Your associations' })).toBeVisible();

  await page.goto('/asbls/club-demo/members');
  await page.getByLabel('Search members (name or email)').fill('DEMO ADMIN');
  await expect(page.getByRole('row', { name: /Demo Admin/ })).toBeVisible();
  await page.getByLabel('Search members (name or email)').fill('nobody-by-this-name');
  await expect(page.getByText('No member matches this search.')).toBeVisible();
});
