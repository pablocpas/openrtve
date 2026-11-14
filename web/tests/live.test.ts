import { describe, expect, it } from 'vitest';
import * as parser from '../src/lib/data/parser';
import { RtveUrls, isRenderableRow, isInlineRow } from '../src/lib/domain/models';
import { resolvePlayback } from '../src/lib/domain/playback';

/*
 * Prueba contra la API real de RTVE: detecta a tiempo si cambian los feeds.
 * Solo corre con `LIVE=1 npm test` (necesita red).
 */
const live = process.env.LIVE === '1';

async function get(url: string): Promise<string> {
  const response = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 OpenRTVE-web-tests' } });
  if (!response.ok) throw new Error(`${response.status} ${url}`);
  return response.text();
}

describe.runIf(live)('API real de RTVE', () => {
  it('la portada trae filas y sus módulos se leen', async () => {
    const home = parser.parseHome(await get(RtveUrls.TV_HOME));
    const rows = home.rows.filter(isRenderableRow);
    expect(rows.length).toBeGreaterThan(5);
    expect(rows[0].layout).toBe('HERO');
    const remote = rows.filter((r) => !isInlineRow(r)).slice(0, 6);
    for (const row of remote) {
      const module = parser.parseModule(await get(row.contentUrl!), row.title);
      expect(module.items.length, row.contentUrl!).toBeGreaterThan(0);
    }
  }, 60_000);

  it('los directos se reproducen y Explorar tiene grupos', async () => {
    const lives = parser.parseModule(await get(`${RtveUrls.LIVES}/agr-directos/1/directos.json`), '');
    const first = lives.items.find((i) => i.kind === 'LIVE')!;
    expect(first.assetId).toBeTruthy();
    expect(resolvePlayback(first).type).toBe('ready');
    const groups = parser.parseExplore(await get(RtveUrls.REMOTE_CONFIG));
    expect(groups.length).toBeGreaterThan(1);
  }, 60_000);

  it('una ficha de programa y la de uno de sus vídeos', async () => {
    const results = parser.parseSearch(await get(`${RtveUrls.SEARCH}?search=isabel&context=tve&tipology=video&type=completo`));
    const program = results.programs[0];
    expect(program).toBeTruthy();
    const detail = parser.parseProgram(await get(`${RtveUrls.PROGRAMS}/${program.id}.json`));
    expect(detail.title).toBeTruthy();
    const page = parser.parseVideoPage(await get(`${RtveUrls.PROGRAMS}/${program.id}/videos.json?page=1&type=39816`));
    expect(page.items.length).toBeGreaterThan(0);
    const video = parser.parseVideo(await get(`${RtveUrls.VIDEOS}/${page.items[0].id}.json`));
    expect(video.item.programId).toBe(program.id);
  }, 60_000);
});
