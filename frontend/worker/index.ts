// Cloudflare Worker in front of the site. The Angular build is served as static assets straight from
// Cloudflare's CDN; only /api/* runs this code (`assets.run_worker_first` in wrangler.jsonc), which relays the
// request to the Spring API on Render.
//
//   browser ──► https://<site>/api/... ──► this Worker ──► API_ORIGIN/api/... ──► Spring
//
// One site address for the browser: no CORS, and the refresh cookie (SameSite=Strict) stays first-party.
// The API trusts what we say about the visitor only because of the shared secret (see EdgeProxyFilter).

export default {
  async fetch(request, env): Promise<Response> {
    const url = new URL(request.url);
    if (!url.pathname.startsWith('/api/')) {
      return env.ASSETS.fetch(request); // not expected (only /api/* reaches the Worker), but stay correct
    }
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
  },
} satisfies ExportedHandler<Env>;
