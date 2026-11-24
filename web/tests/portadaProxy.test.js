import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { onRequestGet } from '../functions/rtve-proxy/portada/[[path]].js';

const SITE = 'https://openrtve.pages.dev/';
const PORTADA = 'https://www.rtve.es/play/la-1/index_apps.json';

describe('función de reenvío de portadas', () => {
  const upstream = vi.fn();

  beforeEach(() => {
    upstream.mockReset().mockResolvedValue(new Response('{"rows":[]}', { headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', upstream);
    vi.stubGlobal('caches', { default: { match: async () => undefined, put: async () => undefined } });
  });
  afterEach(() => vi.unstubAllGlobals());

  const call = (path, init = {}) =>
    onRequestGet({
      request: new Request(`${SITE}rtve-proxy/portada/${path}${init.query ?? ''}`, {
        headers: init.site ? { 'Sec-Fetch-Site': init.site } : {},
      }),
      params: { path: path.split('/') },
      waitUntil: () => {},
    });

  it('reenvía una portada de sección de www.rtve.es', async () => {
    const response = await call('play/la-1/index_apps.json', { site: 'same-origin' });
    expect(response.status).toBe(200);
    expect(await response.text()).toBe('{"rows":[]}');
    expect(upstream.mock.calls[0][0]).toBe(PORTADA);
  });

  it('no reenvía otras rutas, parámetros ni peticiones de otras webs', async () => {
    expect((await call('play/la-1/index.html')).status).toBe(403);
    expect((await call('api/videos/1.json')).status).toBe(403);
    expect((await call('play/la-1/index_apps.json', { query: '?x=1' })).status).toBe(403);
    expect((await call('play/la-1/index_apps.json', { site: 'cross-site' })).status).toBe(403);
    expect(upstream).not.toHaveBeenCalled();
  });

  it('no sigue redirecciones fuera de la portada', async () => {
    upstream.mockResolvedValue(new Response(null, { status: 302, headers: { Location: 'https://example.com/' } }));
    expect((await call('play/la-1/index_apps.json')).status).toBe(502);
  });
});
