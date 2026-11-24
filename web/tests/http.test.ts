import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const SITE = 'https://openrtve.pages.dev/';
const PORTADA = 'https://www.rtve.es/play/la-1/index_apps.json';
const VIA_PROXY = `${SITE}rtve-proxy/portada/play/la-1/index_apps.json`;

// Cada test carga http.ts de nuevo: las portadas bloqueadas se recuerdan por sesión.
async function freshGetText() {
  vi.resetModules();
  return (await import('../src/lib/data/http')).getText;
}

describe('portadas de sección sin CORS', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.stubGlobal('document', { baseURI: SITE });
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('si el navegador bloquea la portada, se pide por el reenvío y después ya directamente por él', async () => {
    const getText = await freshGetText();
    fetchMock.mockImplementation(async (href: string) => {
      if (href === PORTADA) throw new TypeError('Failed to fetch');
      return new Response('{"rows":[]}');
    });

    expect(await getText(PORTADA)).toBe('{"rows":[]}');
    expect(fetchMock.mock.calls.map(([href]) => href)).toEqual([PORTADA, VIA_PROXY]);

    await getText(PORTADA);
    expect(fetchMock.mock.calls.map(([href]) => href)).toEqual([PORTADA, VIA_PROXY, VIA_PROXY]);
  });

  it('las portadas con CORS no pasan por el reenvío', async () => {
    const getText = await freshGetText();
    fetchMock.mockResolvedValue(new Response('{}'));

    await getText(PORTADA);
    expect(fetchMock.mock.calls.map(([href]) => href)).toEqual([PORTADA]);
  });

  it('el resto de documentos de RTVE no se reenvían: un fallo sigue siendo un fallo', async () => {
    const getText = await freshGetText();
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(getText('https://api.rtve.es/api/collection/27.json')).rejects.toBeInstanceOf(TypeError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
