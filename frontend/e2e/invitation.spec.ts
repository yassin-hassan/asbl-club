import { Browser, expect, Page, test } from '@playwright/test';
import { createAccount, linkFromEmail } from './mail';

async function newPerson(browser: Browser): Promise<Page> {
  return (await browser.newContext({ locale: 'en-US' })).newPage();
}

test('an administrator invites someone by email, who signs up and joins without approval', async ({ browser }) => {
  test.setTimeout(90_000);
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);
  const club = `Invite Club ${stamp}`;
  const invitee = `e2e-invitee-${stamp}@club.test`;

  const admin = await newPerson(browser);
  await admin.goto('/register');
  await admin.getByLabel('Name').fill('Chair');
  await admin.getByLabel('Email address').fill(`e2e-inviter-${stamp}@club.test`);
  await admin.getByLabel('Password').fill('password123');
  await createAccount(admin);
  await admin.getByRole('link', { name: 'Create an ASBL' }).click();
  await admin.getByLabel('Name').fill(club);
  await admin.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await admin.getByRole('button', { name: 'Create ASBL' }).click();

  // Invite by email; it appears among the pending invitations.
  await admin.getByRole('textbox', { name: 'Email address' }).fill(invitee);
  await admin.getByRole('button', { name: 'Send the invitation' }).click();
  await expect(admin.getByText(`Invitation sent to ${invitee}.`)).toBeVisible();
  await expect(admin.getByRole('listitem').filter({ hasText: invitee })).toBeVisible();

  // The invitee has no account yet: the link says who invites them, then they sign up with that address.
  const guest = await newPerson(browser);
  await guest.goto(await linkFromEmail(invitee, 'invitation'));
  await expect(guest).toHaveURL(/\/invitation$/); // the token is gone from the address bar
  await expect(guest.getByText(`Chair invites you to join ${club}.`)).toBeVisible();
  await guest.getByRole('link', { name: 'Create an account' }).click();
  await guest.getByLabel('Name').fill('New Member');
  await guest.getByLabel('Email address').fill(invitee);
  await guest.getByLabel('Password').fill('password123');
  await createAccount(guest); // confirms the address through its own email, then back to the invitation

  await expect(guest).toHaveURL(/\/invitation$/);
  await guest.getByRole('button', { name: `Join ${club}` }).click();
  await expect(guest.getByRole('heading', { name: club })).toBeVisible(); // straight in: no approval step
  await expect(guest.getByRole('row', { name: /New Member/ })).toContainText('Active');

  // The administrator's list of pending invitations is empty again.
  await admin.reload();
  await expect(admin.getByRole('listitem').filter({ hasText: invitee })).toHaveCount(0);
});
