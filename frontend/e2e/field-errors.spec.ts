import { expect, test } from '@playwright/test';

// A required field left empty isn't an error until the person sends the form: clicking into it and out again
// shows nothing. Typing something wrong shows its error at once.
test('a field shows its error once typed wrong or once the form is sent, not when merely visited', async ({ page }) => {
  await page.goto('/register');
  const invalid = page.locator('mat-form-field.mat-form-field-invalid');

  await page.getByLabel('Name').click();
  await page.getByLabel('Email address').click(); // out of the name field, nothing typed
  await page.getByLabel('Password').click();
  await page.getByRole('heading', { name: 'Create an account' }).click();
  await expect(invalid).toHaveCount(0);

  await page.getByLabel('Email address').fill('not-an-email');
  await page.getByLabel('Name').click();
  await expect(invalid).toHaveCount(1); // the email, typed wrong

  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(invalid).toHaveCount(3); // sent: every field that is wrong or missing
  await expect(page.getByText('Enter your name.')).toBeVisible();
});
