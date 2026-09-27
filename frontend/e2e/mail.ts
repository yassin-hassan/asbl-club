import { expect, Page } from '@playwright/test';

// The API sends its emails to Mailpit (a fake mail server: in compose.yaml locally, a service in CI); the tests read
// them through Mailpit's API, like an inbox.
const MAILPIT = process.env['MAILPIT_URL'] ?? 'http://localhost:8025';

// The newest link to this page (e.g. "verify-email", "reset-password") in the emails sent to this address. Waits for
// the background sender (every half second in the e2e runs).
export async function linkFromEmail(address: string, page: string): Promise<string> {
  const pattern = new RegExp(`https?://\\S+/${page}#token=[\\w-]+`);
  for (let attempt = 0; attempt < 40; attempt++) {
    const search = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`)).json();
    for (const summary of search.messages ?? []) { // newest first
      const message = await (await fetch(`${MAILPIT}/api/v1/message/${summary.ID}`)).json();
      const link = pattern.exec(message.Text)?.[0];
      if (link) {
        return link;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No email with a ${page} link for ${address}`);
}

// The newest email's subject for this address.
export async function lastSubjectTo(address: string): Promise<string | undefined> {
  const search = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}`)).json();
  return search.messages?.[0]?.Subject;
}

// Submits the filled-in sign-up form, then confirms the address the way a person would: opens the link from the
// email. Ends logged in, where the person was heading (the dashboard, or e.g. the invitation link).
export async function createAccount(page: Page): Promise<void> {
  const email = await page.getByLabel('Email address').inputValue();
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Check your inbox')).toBeVisible();
  await page.goto(await linkFromEmail(email, 'verify-email'));
  await page.waitForURL((url) => !url.pathname.startsWith('/verify-email'));
}
