export interface PlaybackMetricEvent {
  /** Nonce de este envío, independiente de todos los demás; no identifica al espectador. */
  eventId: string;
  contentId: string;
  title: string;
  kind: 'vod' | 'live';
  starts: 0 | 1;
  completions: 0 | 1;
  watchedMs: number;
}

interface Content {
  contentId: string;
  title: string;
  kind: 'vod' | 'live';
}

interface Options {
  now?: () => number;
  send?: (events: PlaybackMetricEvent[], closing: boolean) => Promise<boolean>;
  enabled?: boolean;
}

const REPORT_EVERY_MS = 30_000;
const MAX_GAP_MS = 5_000;
const MAX_PENDING = 8;
const MAX_AGE_MS = 5 * 60_000;

async function send(events: PlaybackMetricEvent[], closing: boolean): Promise<boolean> {
  const body = JSON.stringify({ version: 1, events });
  if (closing && navigator.sendBeacon?.('/api/playback-metrics', new Blob([body], { type: 'application/json' }))) {
    // Un beacon aceptado no confirma recepción: se conserva para reintentar con el mismo nonce.
    return false;
  }
  try {
    const response = await fetch('/api/playback-metrics', {
      method: 'POST', body, headers: { 'Content-Type': 'application/json' },
      credentials: 'omit', cache: 'no-store', keepalive: true, signal: AbortSignal.timeout(8_000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

/** Segundos de reloj con avance efectivo del vídeo; no posición, saltos, pausas ni cargas. */
export function trackPlayback(video: HTMLVideoElement, content: Content, options: Options = {}) {
  const privacy = navigator as Navigator & { globalPrivacyControl?: boolean };
  if (!(options.enabled ?? import.meta.env.PROD) || privacy.doNotTrack === '1' || privacy.globalPrivacyControl === true) {
    return { destroy() {} };
  }
  const now = options.now ?? (() => performance.now());
  const transmit = options.send ?? send;
  let lastClock = now();
  let lastPosition = video.currentTime;
  let lastRate = video.playbackRate;
  let lastReport = lastClock;
  let playing = false;
  let ended = false;
  let started = false;
  let pendingMs = 0;
  let inFlight = false;
  let destroyed = false;
  let pending: { event: PlaybackMetricEvent; created: number }[] = [];

  function baseline() {
    lastClock = now();
    lastPosition = video.currentTime;
    lastRate = video.playbackRate;
  }

  function sample() {
    const elapsed = now() - lastClock;
    const advance = video.currentTime - lastPosition;
    // Un salto de posición o una suspensión larga del navegador no se convierte en tiempo visto.
    if (playing && !video.seeking && elapsed > 0 && elapsed <= MAX_GAP_MS && lastRate > 0
      && advance > 0 && advance <= elapsed / 1000 * lastRate * 1.5 + 0.25) {
      pendingMs += Math.min(elapsed, advance / lastRate * 1000);
    }
    baseline();
  }

  function flush(closing = false, completed = false) {
    const clock = now();
    const completion = completed && content.kind === 'vod';
    if (pendingMs >= 1_000 || (completion && started)) {
      const watchedMs = Math.min(60_000, Math.floor(pendingMs));
      pending.push({
        created: clock,
        event: {
          eventId: crypto.randomUUID(), contentId: content.contentId,
          title: content.title.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, 160), kind: content.kind,
          starts: started ? 0 : 1, completions: completion ? 1 : 0, watchedMs,
        },
      });
      pendingMs = 0;
      started = true;
    }
    pending = pending.filter((p) => clock - p.created <= MAX_AGE_MS).slice(-MAX_PENDING);
    lastReport = clock;
    if (!pending.length || (inFlight && !closing)) return;
    const batch = pending.map((p) => p.event);
    if (!closing) inFlight = true;
    // Un fallo en las métricas nunca interrumpe ni retrasa la reproducción.
    void transmit(batch, closing).then((accepted) => {
      if (accepted) {
        const ids = new Set(batch.map((e) => e.eventId));
        pending = pending.filter((p) => !ids.has(p.event.eventId));
      }
    }).catch(() => {}).finally(() => { if (!closing) inFlight = false; });
  }

  const onPlaying = () => {
    if (ended) { started = false; ended = false; }
    playing = true;
    baseline();
  };
  const onProgress = () => {
    sample();
    if ((!started && pendingMs >= 1_000) || now() - lastReport >= REPORT_EVERY_MS) flush();
  };
  const onPause = () => { sample(); playing = false; flush(); };
  const onSeeking = () => { sample(); baseline(); };
  const onSeeked = () => { baseline(); };
  const onRate = () => { sample(); baseline(); };
  const onEnded = () => {
    if (ended) return;
    sample(); playing = false; ended = true; flush(false, true);
  };
  const onHide = () => { sample(); flush(true); };
  const onVisibility = () => { if (document.visibilityState === 'hidden') onHide(); };
  const listeners: [string, EventListener][] = [
    ['playing', onPlaying], ['timeupdate', onProgress], ['pause', onPause], ['waiting', onPause],
    ['seeking', onSeeking], ['seeked', onSeeked], ['ratechange', onRate], ['ended', onEnded], ['error', onPause],
  ];
  for (const [name, handler] of listeners) video.addEventListener(name, handler);
  window.addEventListener('pagehide', onHide);
  document.addEventListener('visibilitychange', onVisibility);
  const timer = setInterval(onProgress, 1_000);

  return {
    destroy() {
      if (destroyed) return;
      destroyed = true;
      sample(); flush(true);
      clearInterval(timer);
      for (const [name, handler] of listeners) video.removeEventListener(name, handler);
      window.removeEventListener('pagehide', onHide);
      document.removeEventListener('visibilitychange', onVisibility);
    },
  };
}
