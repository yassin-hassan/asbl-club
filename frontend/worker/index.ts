// Cloudflare Worker in front of the site. The Angular build is served as static assets straight from
// Cloudflare's CDN; only the paths in `assets.run_worker_first` (wrangler.jsonc) run this code:
//
//   /api/*      relayed to the Spring API on Render (one site address: no CORS, first-party cookies)
//   RSS feeds   relayed too (/events/rss, /asbls/:slug/events/rss): Spring writes them
//   /events/:id Angular's index.html with the event's link-preview tags filled in
//
// The API trusts what we say about the visitor only because of the shared secret (see EdgeProxyFilter).

const RSS_FEED = /^(\/asbls\/[a-z0-9-]{1,255})?\/events\/rss$/;

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/') || RSS_FEED.test(url.pathname)) {
      return proxyToApi(request, url, env);
    }
    const event = /^\/events\/(\d{1,18})$/.exec(url.pathname);
    if (event && request.method === 'GET') {
      return withLinkPreview(request, event[1], env);
    }
    return env.ASSETS.fetch(request);
  },
} satisfies ExportedHandler<Env>;

function proxyToApi(request: Request, url: URL, env: Env): Promise<Response> {
  const target = new URL(url.pathname + url.search, env.API_ORIGIN);
  const headers = new Headers(request.headers);
  headers.delete('host'); // the API's own host, taken from the target address
  // Never forward what a visitor sent under our names: only this Worker sets them.
  headers.delete('x-edge-secret');
  headers.delete('x-edge-client-ip');
  headers.set('X-Edge-Secret', env.EDGE_PROXY_SECRET);
  const visitorIp = request.headers.get('CF-Connecting-IP'); // set by Cloudflare's edge, not by the visitor
  if (visitorIp) {
    headers.set('X-Edge-Client-Ip', visitorIp);
  }
  return fetch(target, {
    method: request.method,
    headers,
    body: request.body,
    redirect: 'manual', // hand redirects back to the browser rather than following them here
  });
}

// Link previews: WhatsApp, Facebook, LinkedIn… read <meta property="og:…"> tags without running JavaScript,
// so an Angular page alone would preview as a blank "ASBL Club". The tags are added to index.html here; the
// browser then starts Angular as usual. If the event can't be read in time, the plain page is served.
async function withLinkPreview(request: Request, eventId: string, env: Env): Promise<Response> {
  // The app's page is at "/" (the assets answer "/index.html" with a redirect to "/").
  const page = await env.ASSETS.fetch(new Request(new URL('/', request.url), request));
  const event = await publicEvent(eventId, env);
  if (!event || !page.ok) {
    return page;
  }
  const url = new URL(request.url);
  const tags = [
    meta('og:type', 'website'),
    meta('og:site_name', 'asbl.club'),
    meta('og:title', event.title),
    event.description ? meta('og:description', event.description) : '',
    meta('og:url', url.origin + url.pathname),
  ].join('');
  return new HTMLRewriter()
    .on('title', {
      element(title) {
        title.setInnerContent(event.title); // as text: escaped by the rewriter
      },
    })
    .on('head', {
      element(head) {
        head.append(tags, { html: true });
      },
    })
    .transform(page);
}

interface PreviewEvent {
  title: string;
  description?: string;
}

// The public API, as any visitor would call it (published public events only; anything else is a 404).
// The ID is digits only and the host is fixed, so nothing from the URL can steer this request elsewhere.
async function publicEvent(eventId: string, env: Env): Promise<PreviewEvent | null> {
  try {
    const response = await fetch(new URL(`/api/v1/events/${eventId}`, env.API_ORIGIN), {
      headers: { Accept: 'application/json', 'Accept-Language': 'fr' },
      // Preview crawlers give up after a few seconds, and the free API server may be asleep.
      signal: AbortSignal.timeout(3000),
    });
    if (!response.ok) {
      return null;
    }
    const event = (await response.json()) as Partial<PreviewEvent>;
    return typeof event.title === 'string' ? { title: event.title, description: event.description } : null;
  } catch {
    return null; // timeout or network error: serve the page without a preview
  }
}

// Values come from the database (typed by association members): escaped before going into HTML.
function meta(property: string, content: string): string {
  return `<meta property="${property}" content="${escapeAttribute(content)}">`;
}

function escapeAttribute(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}
