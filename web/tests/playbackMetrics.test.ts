import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { trackPlayback, type PlaybackMetricEvent } from '../src/lib/data/playbackMetrics';

class Media extends EventTarget {
  currentTime = 0;
  playbackRate = 1;
  seeking = false;
  emit(type: string) { this.dispatchEvent(new Event(type)); }
}

describe('métricas de reproducción', () => {
  let video: Media;
  let events: PlaybackMetricEvent[];
  let tracker: ReturnType<typeof trackPlayback>;
  let send: ReturnType<typeof vi.fn<(events: PlaybackMetricEvent[], closing: boolean) => Promise<boolean>>>;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('navigator', { doNotTrack: '0' });
    vi.stubGlobal('window', new EventTarget());
    vi.stubGlobal('document', Object.assign(new EventTarget(), { visibilityState: 'visible' }));
    video = new Media();
    events = [];
    send = vi.fn(async (batch: PlaybackMetricEvent[]) => { events.push(...batch); return true; });
    tracker = trackPlayback(video as unknown as HTMLVideoElement, { contentId: '123', title: 'Ejemplo', kind: 'vod' }, {
      enabled: true, now: () => Date.now(), send,
    });
  });
  afterEach(() => { tracker.destroy(); vi.useRealTimers(); vi.unstubAllGlobals(); });

  async function advance(seconds: number, moving = true) {
    for (let i = 0; i < seconds; i++) {
      if (moving) video.currentTime += video.playbackRate;
      await vi.advanceTimersByTimeAsync(1_000);
    }
  }
  const total = (items: PlaybackMetricEvent[]) => items.reduce((n, e) => n + e.watchedMs, 0);

  it('mide avance real y excluye carga inicial, pausas y buffering', async () => {
    await advance(10, false);
    expect(events).toEqual([]);
    video.emit('playing');
    await advance(5);
    video.emit('pause');
    await advance(20, false);
    video.emit('playing');
    await advance(4);
    video.emit('waiting');
    await advance(10, false);
    await Promise.resolve();
    expect(total(events)).toBe(9_000);
    expect(events.reduce((n, e) => n + e.starts, 0)).toBe(1);
  });

  it('no convierte un salto de posición en minutos reproducidos', async () => {
    video.emit('playing');
    await advance(1);
    video.seeking = true;
    video.currentTime += 900;
    video.emit('seeking');
    await advance(2, false);
    video.seeking = false;
    video.emit('seeked');
    video.emit('playing');
    await advance(1);
    video.emit('pause');
    await Promise.resolve();
    expect(total(events)).toBe(2_000);
  });

  it('a velocidad doble mide segundos de reloj, no el doble de tiempo', async () => {
    video.playbackRate = 2;
    video.emit('playing');
    await advance(10);
    video.emit('pause');
    await Promise.resolve();
    expect(total(events)).toBe(10_000);
  });

  it('no suma tiempo cuando la posición no avanza aunque falte el evento de espera', async () => {
    video.emit('playing');
    await advance(2);
    await advance(15, false);
    video.emit('pause');
    await Promise.resolve();
    expect(total(events)).toBe(2_000);
  });

  it('cuenta una llegada al final y una nueva reproducción al repetir', async () => {
    video.emit('playing');
    await advance(3);
    video.emit('ended');
    video.emit('ended');
    await Promise.resolve();
    video.currentTime = 0;
    video.emit('playing');
    await advance(2);
    video.emit('ended');
    await Promise.resolve();
    expect(events.reduce((n, e) => n + e.starts, 0)).toBe(2);
    expect(events.reduce((n, e) => n + e.completions, 0)).toBe(2);
    expect(total(events)).toBe(5_000);
  });

  it('los reintentos conservan el nonce de cada envío, sin un identificador compartido', async () => {
    send.mockResolvedValue(false);
    video.emit('playing');
    await advance(35);
    const first = send.mock.calls[0][0][0];
    const retry = send.mock.calls.at(-1)![0];
    expect(retry[0].eventId).toBe(first.eventId);
    expect(new Set(retry.map((e: PlaybackMetricEvent) => e.eventId)).size).toBe(retry.length);
    expect(Object.keys(first).sort()).toEqual(['completions', 'contentId', 'eventId', 'kind', 'starts', 'title', 'watchedMs']);
  });

  it.each([{ doNotTrack: '1' }, { globalPrivacyControl: true }])('respeta la señal de privacidad %j', async (privacy) => {
    tracker.destroy();
    vi.stubGlobal('navigator', privacy);
    tracker = trackPlayback(video as unknown as HTMLVideoElement, { contentId: '123', title: 'Ejemplo', kind: 'vod' }, { enabled: true, send });
    video.emit('playing');
    await advance(35);
    tracker.destroy();
    expect(send).not.toHaveBeenCalled();
  });

  it('envía el último tramo al cerrar y no vuelve a contar al destruir dos veces', async () => {
    video.emit('playing');
    await advance(4);
    tracker.destroy();
    tracker.destroy();
    await Promise.resolve();
    expect(send.mock.calls.at(-1)![1]).toBe(true);
    expect(total(events)).toBe(4_000);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('un directo que termina no genera una finalización ni un evento vacío', async () => {
    tracker.destroy();
    tracker = trackPlayback(video as unknown as HTMLVideoElement, { contentId: '123', title: 'Canal', kind: 'live' }, {
      enabled: true, now: () => Date.now(), send,
    });
    video.emit('playing');
    await advance(3);
    video.emit('pause');
    await Promise.resolve();
    video.emit('ended');
    await Promise.resolve();
    expect(events.reduce((n, e) => n + e.completions, 0)).toBe(0);
    expect(events.every((e) => e.watchedMs >= 1_000)).toBe(true);
    expect(total(events)).toBe(3_000);
  });

  it('descarta huecos de reloj largos en vez de inventar minutos tras una suspensión', async () => {
    let clock = 0;
    tracker.destroy();
    tracker = trackPlayback(video as unknown as HTMLVideoElement, { contentId: '123', title: 'Ejemplo', kind: 'vod' }, {
      enabled: true, now: () => clock, send,
    });
    video.emit('playing');
    clock = 60_000;
    video.currentTime = 60;
    video.emit('timeupdate');
    clock += 1_000;
    video.currentTime += 1;
    video.emit('timeupdate');
    await Promise.resolve();
    expect(total(events)).toBe(1_000);
  });
});
