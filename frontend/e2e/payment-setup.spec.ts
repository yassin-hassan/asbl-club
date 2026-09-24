import { expect, test } from '@playwright/test';

test('an administrator sees their new association is not connected to Stripe, and is sent to Stripe to connect it', async ({
  page,
}) => {
  const stamp = Date.now();
  const digits = String(stamp).slice(-10);

  // A fresh user and association (the demo database is shared by every run).
  await page.goto('/register');
  await page.getByLabel('Name').fill('Treasurer');
  await page.getByLabel('Email address').fill(`e2e-payments-${stamp}@club.test`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Create account' }).click();
  await page.getByRole('link', { name: 'Create an ASBL' }).click();
  await page.getByLabel('Name').fill(`Paying Club ${stamp}`);
  await page.getByLabel('BCE number').fill(`${digits.slice(0, 4)}.${digits.slice(4, 7)}.${digits.slice(7, 10)}`);
  await page.getByRole('button', { name: 'Create ASBL' }).click();

  // The status comes from the real API: a new association has no Stripe account yet.
  await page.getByRole('link', { name: 'Payments' }).click();
  await expect(page.getByText("Not connected to Stripe yet: the association can't receive payments.")).toBeVisible();

  // Stripe itself is outside the test: the onboarding link and Stripe's page are stand-ins
  // (with no Stripe keys, the real API would answer 502).
  await page.route('**/api/v1/asbls/*/manage/payments/onboarding', (route) =>
    route.fulfill({ json: { url: 'https://connect.stripe.com/setup/e/acct_test/e2e' } }),
  );
  await page.route('https://connect.stripe.com/**', (route) =>
    route.fulfill({ contentType: 'text/html', body: '<h1>Stripe onboarding</h1>' }),
  );
  await page.getByRole('button', { name: 'Connect Stripe' }).click();

  await expect(page).toHaveURL('https://connect.stripe.com/setup/e/acct_test/e2e');
});
