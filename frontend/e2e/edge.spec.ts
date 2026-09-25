import { expect, test } from '@playwright/test';

// What only the Cloudflare Worker does (production's front door), so these run when the tests go through it
// (E2E_THROUGH_WORKER, as in CI) and are skipped against `ng serve`.
test.skip(!process.env['E2E_THROUGH_WORKER'], 'needs the Cloudflare Worker in front (not ng serve)');

async function galaId(request: import('@playwright/test').APIRequestContext): Promise<number> {
  const events: { id: number; title: string }[] = await (await request.get('/api/v1/asbls/club-demo/events')).json();
  return events.find((e) => e.title === 'Concert de gala')!.id;
}

test('a shared event link carries its preview tags, and the page still starts the app', async ({ page, request }) => {
  const id = await galaId(request);

  // What WhatsApp or Facebook see: the HTML as served, without running JavaScript.
  const html = await (await request.get(`/events/${id}`)).text();
  expect(html).toContain('<title>Concert de gala</title>');
  expect(html).toContain('<meta property="og:title" content="Concert de gala">');
  expect(html).toContain('<meta property="og:description" content="Une soirée de démonstration">');
  expect(html).toMatch(new RegExp(`<meta property="og:url" content="http://localhost:\\d+/events/${id}">`));

  await page.goto(`/events/${id}`);
  await expect(page.getByRole('heading', { name: 'Concert de gala' })).toBeVisible();
});

test('an event that is not public gets no preview, just the app', async ({ request }) => {
  const html = await (await request.get('/events/999999999')).text();
  expect(html).not.toContain('og:title');
  expect(html).toContain('<app-root');
});

test("the site's pages carry the security headers", async ({ request }) => {
  const headers = (await request.get('/')).headers();
  expect(headers['content-security-policy']).toContain("script-src 'self' https://js.stripe.com");
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(headers['x-content-type-options']).toBe('nosniff');
});
