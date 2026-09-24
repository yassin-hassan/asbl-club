import { expect, test } from '@playwright/test';

test('the landing page presents the platform and leads to sign-up and login', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByRole('heading', { level: 1 })).toContainText('Manage your non-profit');
  // Both the hero and the closing call-to-action lead to sign-up.
  const signUpLinks = page.getByRole('link', { name: 'Create my ASBL for free' });
  await expect(signUpLinks).toHaveCount(2);
  await expect(signUpLinks.first()).toHaveAttribute('href', '/register');
  await expect(page.getByRole('heading', { name: 'How does it work?' })).toBeVisible();
});

for (const [path, title] of [
  ['/legal', 'Legal notice'],
  ['/privacy', 'Privacy (GDPR)'],
  ['/cookies', 'Cookies'],
]) {
  test(`the ${path} page is reachable from the footer and translated`, async ({ page }) => {
    await page.goto('/');
    await page.getByRole('contentinfo').getByRole('link', { name: title }).click();

    await expect(page).toHaveURL(path);
    await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
  });
}

test('an unknown address shows a "page not found" page', async ({ page }) => {
  await page.goto('/this/does/not/exist');

  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});
