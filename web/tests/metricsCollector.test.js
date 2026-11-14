import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { onRequest } from '../functions/api/playback-metrics.js';

describe('recogida privada de métricas (SQL real)', () => {
  let sql;
  let db;
  const event = (patch = {}) => ({
    eventId: crypto.randomUUID(), contentId: '123', title: 'Película de ejemplo', kind: 'vod',
    starts: 1, completions: 0, watchedMs: 30_000, ...patch,
  });
  const request = (events, headers = {}) => new Request('https://openrtve.pages.dev/api/playback-metrics', {
    method: 'POST', headers: { Origin: 'https://openrtve.pages.dev', 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify({ version: 1, events }),
  });
  const collect = (req) => onRequest({ request: req, env: { PLAYBACK_METRICS: db, CF_PAGES_BRANCH: 'main' } });

  beforeEach(() => {
    sql = new DatabaseSync(':memory:');
    sql.exec(readFileSync(new URL('../migrations/0001_playback_metrics.sql', import.meta.url), 'utf8'));
    db = {
      prepare: (query) => ({ bind: (...args) => () => sql.prepare(query).run(...args), run: () => sql.prepare(query).run() }),
      batch: async (statements) => {
        sql.exec('BEGIN');
        try {
          for (const stmt of statements) typeof stmt === 'function' ? stmt() : stmt.run();
          sql.exec('COMMIT');
        } catch (e) { sql.exec('ROLLBACK'); throw e; }
      },
    };
  });
  afterEach(() => sql.close());

  it('agrega, separa directos y vídeos, y deduplica envíos y reintentos en el mismo batch', async () => {
    const first = event();
    expect((await collect(request([first, first]))).status).toBe(204);
    expect((await collect(request([first, event({ starts: 0, completions: 1, watchedMs: 5_000 })]))).status).toBe(204);
    expect((await collect(request([event({ kind: 'live', watchedMs: 10_000 })]))).status).toBe(204);
    expect(sql.prepare('SELECT kind, starts, completions, watched_ms FROM playback_daily ORDER BY kind').all()).toEqual([
      { kind: 'live', starts: 1, completions: 0, watched_ms: 10_000 },
      { kind: 'vod', starts: 1, completions: 1, watched_ms: 35_000 },
    ]);
    expect(sql.prepare("SELECT reproducciones, finales_alcanzados, segundos_por_reproduccion FROM playback_summary WHERE kind='vod'").get())
      .toEqual({ reproducciones: 1, finales_alcanzados: 1, segundos_por_reproduccion: 35 });
    const receiptColumns = sql.prepare('PRAGMA table_info(metric_receipts)').all().map((r) => r.name);
    expect(receiptColumns).toEqual(['event_id', 'expires_at']);
  });

  it('rechaza solicitudes de otras webs y no expone una API pública de lectura', async () => {
    expect((await collect(request([event()], { Origin: 'https://otro.example' }))).status).toBe(403);
    expect((await collect(request([event()], { 'Sec-Fetch-Site': 'cross-site' }))).status).toBe(403);
    expect((await collect(new Request('https://openrtve.pages.dev/api/playback-metrics'))).status).toBe(405);
    expect(sql.prepare('SELECT COUNT(*) AS n FROM playback_daily').get().n).toBe(0);
  });

  it.each([
    { watchedMs: -1 }, { watchedMs: 60_001 }, { watchedMs: 1.5 }, { starts: 3 }, { contentId: '../privado' },
    { eventId: 'identificador-personal' }, { ip: '192.0.2.1' }, { title: 'a\nb' }, { kind: 'live', completions: 1 },
  ])('no escribe datos inesperados o inválidos: %j', async (patch) => {
    expect((await collect(request([event(patch)]))).status).toBe(400);
    expect(sql.prepare('SELECT COUNT(*) AS n FROM playback_daily').get().n).toBe(0);
  });

  it('no registra señales de privacidad y no mezcla vistas previas', async () => {
    expect((await collect(request([event()], { DNT: '1' }))).status).toBe(204);
    expect((await collect(request([event()], { 'Sec-GPC': '1' }))).status).toBe(204);
    expect((await onRequest({ request: request([event()]), env: { PLAYBACK_METRICS: db, CF_PAGES_BRANCH: 'feature' } })).status).toBe(204);
    expect(sql.prepare('SELECT COUNT(*) AS n FROM playback_daily').get().n).toBe(0);
  });

  it('limita el cuerpo de la solicitud y falla sin afectar al vídeo si falta la base', async () => {
    expect((await collect(request([event({ title: 'a'.repeat(9_000) })]))).status).toBe(413);
    expect((await onRequest({ request: request([event()]), env: {} })).status).toBe(503);
    expect((await collect(new Request('https://openrtve.pages.dev/api/playback-metrics', {
      method: 'POST', headers: { Origin: 'https://openrtve.pages.dev', 'Content-Type': 'application/json' }, body: '{',
    }))).status).toBe(400);
  });

  it('purga recibos caducados y totales fuera de la ventana de 90 días', async () => {
    sql.exec("INSERT INTO metric_receipts VALUES ('caducado', 1)");
    sql.exec("INSERT INTO playback_daily VALUES ('2000-01-01', '1', 'Antiguo', 'vod', 1, 0, 1000)");
    expect((await collect(request([event()]))).status).toBe(204);
    expect(sql.prepare("SELECT COUNT(*) AS n FROM metric_receipts WHERE event_id='caducado'").get().n).toBe(0);
    expect(sql.prepare("SELECT COUNT(*) AS n FROM playback_daily WHERE day='2000-01-01'").get().n).toBe(0);
  });
});
