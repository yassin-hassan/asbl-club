import { readFile } from 'node:fs/promises';
import { expect, test } from '@playwright/test';

test('an administrator sees who booked and downloads the list for Excel', async ({ page }) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  await page.goto('/register');
  await page.getByLabel('Name').fill('Olivia Organiser');
  await page.getByLabel('Email address').fill(`e2e-attendees-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Attendees Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();
  await page.getByRole('link', { name: 'Events' }).click();
  await page.getByRole('link', { name: 'Create an event' }).click();
  await page.getByLabel('Title').fill('Quiz night');
  await page.getByLabel('Start').fill('2026-12-01T20:00');
  await page.getByRole('button', { name: 'Create event' }).click();
  await page.getByLabel('Label').fill('Team seat');
  await page.getByLabel('Price').fill('8');
  await page.getByLabel('Seats').fill('40');
  await page.getByRole('button', { name: 'Add a ticket category' }).click();
  await page.getByRole('button', { name: 'Publish' }).click();

  // Nobody yet.
  await expect(page.getByText('Nobody has booked yet.')).toBeVisible();

  // The administrator books a seat (and doesn't pay: the association has no Stripe account).
  await page.getByRole('row', { name: /Team seat/ }).getByRole('button', { name: 'Book' }).click();
  await expect(page).toHaveURL(/\/pay\/\d+$/);
  await page.goBack();

  const attendees = page.getByRole('table', { name: 'Attendees' });
  await expect(attendees.getByRole('row', { name: /Olivia Organiser.*Team seat.*Awaiting payment.*€8\.00/ }))
    .toBeVisible();
  await expect(page.getByText('0 paid · 1 awaiting payment')).toBeVisible();

  // The download: a CSV for Excel in the page's language (English here), with the booking in it.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Download (CSV)' }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^attendees-.+-\d+\.csv$/);
  const csv = await readFile((await download.path())!, 'utf8');
  expect(csv).toMatch(/^﻿Name;Email;Ticket;Status;Amount \(EUR\);Booked at\r\n/);
  expect(csv).toContain(`Olivia Organiser;e2e-attendees-${stamp}@club.test;Team seat;Awaiting payment;8.00;`);
});
