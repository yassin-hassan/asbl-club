import { expect, Page, test } from '@playwright/test';

async function anAssociationWithAnEvent(page: Page, stamp: number, title: string) {
  const digits = String(stamp).slice(-10);
  await page.goto('/register');
  await page.getByLabel('Name').fill('Organiser');
  await page.getByLabel('Email address').fill(`e2e-lifecycle-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Lifecycle Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();
  await page.getByRole('link', { name: 'Events' }).click();
  await createEvent(page, title);
}

async function createEvent(page: Page, title: string) {
  await page.getByRole('link', { name: 'Create an event' }).click();
  await page.getByLabel('Title').fill(title);
  await page.getByLabel('Start').fill('2026-12-01T20:00');
  await page.getByRole('button', { name: 'Create event' }).click();
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
}

async function addTicket(page: Page, label: string, price: string, seats: string) {
  await page.getByLabel('Label').fill(label);
  await page.getByLabel('Price').fill(price);
  await page.getByLabel('Seats').fill(seats);
  await page.getByRole('button', { name: 'Add a ticket category' }).click();
  await expect(page.getByRole('row', { name: new RegExp(label) })).toBeVisible();
}

test('an administrator edits a published event and its tickets, then cancels it', async ({ page }) => {
  await anAssociationWithAnEvent(page, Date.now(), 'Autumn fair');
  await addTicket(page, 'Adult', '10', '50');
  await addTicket(page, 'Child', '5', '20');
  await page.getByRole('button', { name: 'Publish' }).click();
  const publicUrl = await page.getByRole('link', { name: 'View the public page' }).getAttribute('href');

  // A seat is taken: that category can be changed but no longer removed.
  await page.getByRole('row', { name: /Adult/ }).getByRole('button', { name: 'Book' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);
  await page.goBack();
  const adult = page.getByRole('row', { name: /Adult.*1 \/ 50/ });
  await expect(adult.getByRole('button', { name: 'Remove' })).toHaveCount(0);

  await adult.getByRole('button', { name: 'Change' }).click();
  await expect(page.getByRole('heading', { name: 'Change Adult' })).toBeVisible();
  await page.getByLabel('Label').fill('Adult (early bird)');
  await page.getByLabel('Seats').fill('40');
  await page.getByRole('button', { name: 'Save the category' }).click();
  await expect(page.getByRole('row', { name: /Adult \(early bird\).*1 \/ 40/ })).toBeVisible();

  // Nobody booked Child: it can go.
  await page.getByRole('row', { name: /Child/ }).getByRole('button', { name: 'Remove' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Remove' }).click();
  await expect(page.getByRole('row', { name: /Child/ })).toHaveCount(0);

  // The event itself: a new title, seen by the public.
  await page.getByRole('link', { name: 'Edit the event' }).click();
  await expect(page.getByLabel('Title')).toHaveValue('Autumn fair');
  await expect(page.getByLabel('Start')).toHaveValue('2026-12-01T20:00'); // the same local time comes back
  await page.getByLabel('Title').fill('Autumn fair (new venue)');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { name: 'Autumn fair (new venue)' })).toBeVisible();

  // Cancelled: hidden from the public, and nothing can change any more.
  await page.getByRole('button', { name: 'Cancel the event' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel the event' }).click();
  await expect(page.getByText('Cancelled: hidden from the public and no longer bookable.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Edit the event' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Book' })).toHaveCount(0);

  await page.goto(publicUrl!);
  await expect(page.getByRole('alert')).toHaveText('Not found.');
});

test('a draft can be deleted', async ({ page }) => {
  await anAssociationWithAnEvent(page, Date.now(), 'Tentative quiz');

  // Changing one's mind in the dialog keeps it.
  await page.getByRole('button', { name: 'Delete the draft' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Keep it' }).click();
  await expect(page.getByRole('heading', { name: 'Tentative quiz' })).toBeVisible();

  await page.getByRole('button', { name: 'Delete the draft' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Delete the draft' }).click();
  await expect(page).toHaveURL(/\/manage\/events$/);
  await expect(page.getByText('No events yet')).toBeVisible();
});
