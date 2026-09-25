// Cloudflare Pages Function: every request to /api/* on the site is relayed to the Spring API on Render.
// Everything else (the Angular app's files) is served straight from Cloudflare's CDN, without this function.
//
//   browser ──► https://<site>/api/... ──► this function ──► API_ORIGIN/api/... ──► Spring
//
// One site address for the browser: no CORS, and the refresh cookie (SameSite=Strict) stays first-party.
// The API trusts what we say about the visitor only because of the shared secret (see EdgeProxyFilter).

interface Env {
  API_ORIGIN: string; // e.g. https://asbl-club.onrender.com
  EDGE_PROXY_SECRET: string; // a secret: set with `wrangler pages secret put`, never committed
}

export const onRequest: PagesFunction<Env> = async ({ request, env }) => {
  const url = new URL(request.url);
  const target = new URL(url.pathname + url.search, env.API_ORIGIN);

  const headers = new Headers(request.headers);
  headers.delete('host'); // the API's own host, taken from the target address
  // Never forward what a visitor sent under our names: only this function sets them.
  headers.delete('x-edge-secret');
  headers.delete('x-edge-client-ip');
  headers.set('X-Edge-Secret', env.EDGE_PROXY_SECRET);
  const visitorIp = request.headers.get('CF-Connecting-IP'); // set by Cloudflare, can't be forged by the visitor
  if (visitorIp) {
    headers.set('X-Edge-Client-Ip', visitorIp);
  }

  return fetch(target, {
    method: request.method,
    headers,
    body: request.body,
    redirect: 'manual', // hand redirects back to the browser rather than following them here
  });
};
