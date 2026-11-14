<script lang="ts">
  import { onDestroy, onMount, tick } from 'svelte';
  import Icon from '../components/Icon.svelte';
  import { repository } from '../lib/data/repository';
  import { settings } from '../lib/data/settings.svelte';
  import { isInProgress, watchHistory } from '../lib/data/watchHistory.svelte';
  import { canHaveNextEpisode } from '../lib/domain/classification';
  import { cueAt, type CatalogItem, type PreviewSprite } from '../lib/domain/models';
  import { resolvePlayback, type PlaybackReady } from '../lib/domain/playback';
  import { messages } from '../lib/ui/messages.svelte';
  import { nav, recallItem, rememberItem } from '../lib/ui/nav.svelte';
  import { loadShaka } from '../lib/ui/playerPreload';
  import { blockedText, formatClock, languageName } from '../lib/ui/text';

  /*
   * Reproductor (PlayerActivity.kt) sobre Shaka Player: DASH y HLS, Widevine para lo
   * protegido y la variante sin DRM si el navegador no lo tiene. Calidad, velocidad,
   * audio y subtítulos salen de las pistas reales; miniaturas al arrastrar, siguiente
   * episodio con cuenta atrás, directo con ventana DVR, teclado y Media Session.
   * Shaka se descarga solo aquí: la portada arranca sin él.
   */

  type Props =
    | { kind: 'vod'; id: string; restart: boolean }
    | { kind: 'live'; assetId: string; drm: boolean; title: string };
  let props: Props = $props();

  const LIVE_EDGE_TOLERANCE_S = 20;
  const NEXT_PROMPT_BEFORE_END_S = 20;
  const PROGRESS_SAVE_EVERY_MS = 10_000;
  const CONTROLS_HIDE_MS = 3000;
  const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];

  let root = $state<HTMLDivElement>();
  let video = $state<HTMLVideoElement>();
  let textContainer = $state<HTMLDivElement>();

  let item = $state<CatalogItem | null>(null);
  let request: PlaybackReady | null = null;
  let errorMessage = $state<string | null>(null);
  let title = $state('');
  let subtitle = $state<string | null>(null);

  let paused = $state(true);
  let buffering = $state(true);
  let currentTime = $state(0);
  let duration = $state(0);
  let bufferedEnd = $state(0);
  let isLive = $state(false);
  let seekStart = $state(0);
  let seekEnd = $state(0);
  let volume = $state(settings.current.volume);
  let muted = $state(settings.current.muted);
  let fullscreen = $state(false);
  /** iPhone: sin pantalla completa de páginas, solo la del vídeo (reproductor nativo). */
  let fullscreenSupported = $state(true);
  /** Último tipo de puntero: Safari no lo dice en el evento `click`. */
  let lastPointerType = 'mouse';
  let controlsVisible = $state(true);
  let menuOpen = $state(false);
  let pipSupported = $state(false);

  let sprite = $state<PreviewSprite | null>(null);
  let scrub = $state<{ time: number; fraction: number } | null>(null);
  let dragging = $state(false);

  let nextItem = $state<CatalogItem | null>(null);
  let tracksVersion = $state(0);
  let gestureFeedback = $state<{ text: string; side: 'left' | 'right' | 'center'; id: number } | null>(null);

  // Shaka sin tipos estrictos: su API es amplia y aquí se usa una parte pequeña.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let player: any = $state.raw(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  let shakaLib: any = null;
  let usedFallback = false;
  let destroyed = false;
  let hideTimer: ReturnType<typeof setTimeout> | null = null;
  let saveTimer: ReturnType<typeof setInterval> | null = null;
  let wakeLock: WakeLockSentinel | null = null;
  const extras = new AbortController();

  const atLiveEdge = $derived(isLive && seekEnd - currentTime <= LIVE_EDGE_TOLERANCE_S);
  const rangeStart = $derived(isLive ? seekStart : 0);
  const rangeEnd = $derived(isLive ? seekEnd : duration);
  const fraction = $derived(rangeEnd > rangeStart ? (currentTime - rangeStart) / (rangeEnd - rangeStart) : 0);
  const bufferedFraction = $derived(rangeEnd > rangeStart ? (bufferedEnd - rangeStart) / (rangeEnd - rangeStart) : 0);
  const remaining = $derived(duration - currentTime);
  const showNext = $derived(!isLive && nextItem != null && duration > 0 && remaining >= 0 && remaining <= NEXT_PROMPT_BEFORE_END_S);
  const nextCountdown = $derived(showNext && settings.current.autoplayNext ? Math.max(0, Math.floor(remaining)) : null);
  const scrubCue = $derived(sprite && scrub ? cueAt(sprite, scrub.time * 1000) : null);

  // ---- Arranque ----

  async function resolveItem(): Promise<CatalogItem> {
    if (props.kind === 'live') {
      return (
        recallItem(`live:${props.assetId}`) ?? {
          id: props.assetId, playbackId: props.assetId, assetId: props.assetId, title: props.title, subtitle: null, imageUrl: null,
          kind: 'LIVE', allowedInCountry: null, loginRequired: false, paid: false, drm: props.drm,
        }
      );
    }
    const known = recallItem(`play:${props.id}`);
    if (known) return known;
    const loaded = (await repository.loadVideo(props.id, { signal: extras.signal })).value.item;
    watchHistory.register(loaded);
    return loaded;
  }

  async function widevineAvailable(): Promise<boolean> {
    try {
      await navigator.requestMediaKeySystemAccess('com.widevine.alpha', [
        { initDataTypes: ['cenc'], videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }], audioCapabilities: [{ contentType: 'audio/mp4; codecs="mp4a.40.2"' }] },
      ]);
      return true;
    } catch {
      return false;
    }
  }

  async function start() {
    errorMessage = null;
    buffering = true;
    try {
      item = await resolveItem();
    } catch {
      if (!destroyed) errorMessage = 'No se pudo reproducir el contenido';
      return;
    }
    if (destroyed) return;
    const decision = resolvePlayback(item);
    if (decision.type === 'blocked') {
      errorMessage = blockedText(decision.reason);
      return;
    }
    request = decision;
    title = decision.title;
    subtitle = item.kind === 'PROGRAM' ? item.title : item.kind === 'LIVE' ? item.subtitle : item.subtitle;
    document.title = `${title} · OpenRTVE`;
    setupMediaSession();
    loadExtras(decision.videoId);

    // Shaka, Widevine y la licencia a la vez: el token no espera a que llegue el reproductor.
    const tokenUrl = decision.drmTokenUrl;
    const drm = tokenUrl
      ? widevineAvailable().then(async (has) => ({
          hasWidevine: has,
          licenseUrl: has ? await repository.widevineLicenseUrl(tokenUrl, extras.signal).catch(() => null) : null,
        }))
      : Promise.resolve({ hasWidevine: false, licenseUrl: null });
    const [lib, { hasWidevine, licenseUrl }] = await Promise.all([loadShaka(), drm]);
    if (destroyed) return;
    shakaLib = lib;
    shakaLib.polyfill.installAll();
    if (!shakaLib.Player.isBrowserSupported()) {
      errorMessage = 'Este navegador no puede reproducir vídeo adaptativo';
      return;
    }
    // iPhone: el vídeo adaptativo va por ManagedMediaSource, que exige renunciar a
    // AirPlay salvo que haya una fuente alternativa.
    if (!('MediaSource' in window) && 'ManagedMediaSource' in window && video) video.disableRemotePlayback = true;
    player = new shakaLib.Player();
    await player.attach(video);
    player.setVideoContainer(textContainer);
    player.addEventListener('error', (e: { detail: ShakaError }) => onError(e.detail));
    player.addEventListener('buffering', (e: { buffering: boolean }) => (buffering = e.buffering));
    player.addEventListener('trackschanged', () => tracksVersion++);
    player.addEventListener('variantchanged', () => tracksVersion++);
    player.addEventListener('adaptation', () => tracksVersion++);
    player.addEventListener('textchanged', () => tracksVersion++);
    player.addEventListener('texttrackvisibility', () => tracksVersion++);

    // Sin Widevine, la variante de compatibilidad, como el cliente oficial en esos dispositivos.
    if (decision.drmTokenUrl && !hasWidevine && decision.fallbackUri) {
      usedFallback = true;
      await loadSource(decision.fallbackUri, null);
      return;
    }
    if (destroyed) return;
    await loadSource(decision.uri, licenseUrl);
  }

  async function loadSource(uri: string, licenseUrl: string | null) {
    if (!player || destroyed) return;
    const prefs = settings.current;
    player.resetConfiguration();
    player.configure({
      drm: { servers: licenseUrl ? { 'com.widevine.alpha': licenseUrl } : {} },
      preferredAudioLanguage: 'es',
      preferredTextLanguage: prefs.subtitlesByDefault ? 'es' : '',
      abr: { restrictions: { maxHeight: prefs.maxVideoHeight > 0 ? prefs.maxVideoHeight : Infinity } },
      streaming: { bufferingGoal: 30, rebufferingGoal: 2 },
    });
    const restart = props.kind === 'vod' && props.restart;
    const entry = request?.historyKey ? watchHistory.entryFor(request.historyKey) : undefined;
    const resumeAt = !restart && entry && isInProgress(entry) ? entry.positionMs / 1000 : undefined;
    try {
      await player.load(uri, resumeAt, request?.mimeType);
    } catch (error) {
      onError(error as ShakaError);
      return;
    }
    if (destroyed) return;
    isLive = player.isLive();
    if (prefs.subtitlesByDefault) player.setTextTrackVisibility(true);
    tracksVersion++;
    video!.volume = volume;
    video!.muted = muted;
    try {
      await video!.play();
    } catch {
      // Sin interacción previa el navegador no deja empezar con sonido: queda en pausa con su botón.
      paused = true;
    }
  }

  interface ShakaError {
    category?: number;
    code?: number;
    data?: unknown[];
  }

  function onError(error: ShakaError) {
    if (destroyed || !error) return;
    // 7000: carga interrumpida por otra (cambio de episodio): no es un fallo.
    if (error.code === 7000) return;
    const fallback = request?.fallbackUri;
    if (error.category === 6 && fallback && !usedFallback) {
      usedFallback = true;
      void loadSource(fallback, null);
      return;
    }
    const status = error.code === 1001 ? Number(error.data?.[1]) : null;
    if (error.category === 6) errorMessage = 'Este navegador no puede reproducir el contenido protegido';
    else if (status === 403) errorMessage = 'Este contenido no está disponible en tu ubicación';
    else if (status === 404 || status === 410) errorMessage = 'Este contenido ya no está disponible';
    else if (error.code === 1002 || error.code === 1003 || !navigator.onLine) errorMessage = 'Sin conexión a Internet';
    else errorMessage = 'No se pudo reproducir el contenido';
    buffering = false;
  }

  async function retry() {
    errorMessage = null;
    usedFallback = false;
    if (player) {
      await player.unload().catch(() => {});
      await player.destroy().catch(() => {});
      player = null;
    }
    start();
  }

  /** Miniaturas de la barra y siguiente episodio: extras que no bloquean la reproducción. */
  function loadExtras(videoId: string | null) {
    if (!videoId) return;
    repository.loadPreviewSprite(videoId, extras.signal).then((s) => (sprite = s), () => {});
    const current = item;
    if (current && !canHaveNextEpisode(current) && current.kind !== 'PROGRAM') return;
    repository.loadNextVideo(videoId, extras.signal).then(
      (next) => {
        if (!next || !canHaveNextEpisode(next)) return;
        const programId = current?.programId;
        if (programId && next.programId && programId !== next.programId) return;
        nextItem = next;
      },
      () => {},
    );
  }

  function playNext() {
    const next = nextItem;
    if (!next) return;
    const decision = resolvePlayback(next);
    if (decision.type !== 'ready') {
      messages.show(blockedText(decision.reason));
      return;
    }
    saveProgress();
    watchHistory.register(next);
    const id = next.playbackId ?? next.id;
    rememberItem(`play:${id}`, next);
    nav.navigate({ name: 'play', id, restart: false }, { replace: true });
  }

  // ---- Progreso y sistema ----

  function saveProgress() {
    const key = request?.historyKey;
    if (!key || !video || isLive || !(duration > 0)) return;
    watchHistory.updateProgress(key, video.currentTime * 1000, duration * 1000);
  }

  function setupMediaSession() {
    if (!('mediaSession' in navigator)) return;
    const art = item?.imageUrl;
    navigator.mediaSession.metadata = new MediaMetadata({
      title,
      artist: subtitle ?? 'RTVE Play',
      album: 'OpenRTVE',
      artwork: art ? [{ src: art, sizes: '960x540', type: 'image/jpeg' }] : [],
    });
    const handlers: [MediaSessionAction, MediaSessionActionHandler][] = [
      ['play', () => video?.play()],
      ['pause', () => video?.pause()],
      ['seekbackward', () => seekBy(-10)],
      ['seekforward', () => seekBy(10)],
      ['nexttrack', () => nextItem && playNext()],
    ];
    for (const [action, handler] of handlers) {
      try {
        navigator.mediaSession.setActionHandler(action, handler);
      } catch {
        // Acción no admitida por el navegador.
      }
    }
  }

  async function keepScreenOn(on: boolean) {
    try {
      if (on && !wakeLock && 'wakeLock' in navigator) {
        wakeLock = await navigator.wakeLock.request('screen');
        wakeLock.addEventListener('release', () => (wakeLock = null));
      } else if (!on && wakeLock) {
        await wakeLock.release();
        wakeLock = null;
      }
    } catch {
      // Sin permiso o sin soporte: la pantalla se apaga como siempre.
    }
  }

  // ---- Controles ----

  function showControls() {
    controlsVisible = true;
    if (hideTimer) clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      if (!paused && !menuOpen && !dragging) controlsVisible = false;
    }, CONTROLS_HIDE_MS);
  }

  function togglePlay() {
    if (!video) return;
    if (video.paused || video.ended) {
      if (video.ended) video.currentTime = rangeStart;
      video.play().catch(() => {});
    } else {
      video.pause();
    }
    showControls();
  }

  function seekTo(time: number) {
    if (!video) return;
    video.currentTime = Math.min(Math.max(time, rangeStart), Math.max(rangeStart, rangeEnd - 0.5));
    currentTime = video.currentTime;
  }

  function seekBy(seconds: number) {
    if (!video) return;
    seekTo(video.currentTime + seconds);
    flash(seconds > 0 ? `+${seconds} s` : `${seconds} s`, seconds > 0 ? 'right' : 'left');
    showControls();
  }

  function goLive() {
    player?.goToLive?.();
    showControls();
  }

  function setVolume(value: number) {
    if (!video) return;
    volume = Math.min(1, Math.max(0, value));
    video.volume = volume;
    if (volume > 0 && muted) {
      muted = false;
      video.muted = false;
    }
    settings.update({ volume, muted });
  }

  function toggleMute() {
    if (!video) return;
    muted = !muted;
    video.muted = muted;
    settings.update({ muted });
    flash(muted ? 'Silencio' : `Volumen ${Math.round(volume * 100)} %`, 'center');
  }

  async function toggleFullscreen() {
    try {
      if (document.fullscreenElement) {
        await document.exitFullscreen();
      } else if (!root?.requestFullscreen) {
        // iPhone: pantalla completa del propio vídeo, con los controles de iOS.
        (video as HTMLVideoElement & { webkitEnterFullscreen?: () => void })?.webkitEnterFullscreen?.();
      } else {
        await root.requestFullscreen({ navigationUI: 'hide' });
        // En el móvil, apaisado como la app.
        await (screen.orientation as ScreenOrientation & { lock?: (o: string) => Promise<void> }).lock?.('landscape').catch(() => {});
      }
    } catch {
      // Sin permiso de pantalla completa.
    }
  }

  async function togglePip() {
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await video?.requestPictureInPicture();
    } catch {
      messages.show('Imagen en imagen no disponible');
    }
  }

  let flashId = 0;
  function flash(text: string, side: 'left' | 'right' | 'center') {
    const id = ++flashId;
    gestureFeedback = { text, side, id };
    setTimeout(() => gestureFeedback?.id === id && (gestureFeedback = null), 700);
  }

  // ---- Barra de progreso ----

  let bar = $state<HTMLDivElement>();

  function timeAt(clientX: number) {
    if (!bar) return { time: 0, fraction: 0 };
    const rect = bar.getBoundingClientRect();
    const f = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    return { time: rangeStart + f * (rangeEnd - rangeStart), fraction: f };
  }

  function onBarPointerDown(event: PointerEvent) {
    if (!(rangeEnd > rangeStart)) return;
    dragging = true;
    bar?.setPointerCapture(event.pointerId);
    scrub = timeAt(event.clientX);
    showControls();
  }

  function onBarPointerMove(event: PointerEvent) {
    if (!(rangeEnd > rangeStart)) return;
    if (dragging || event.pointerType === 'mouse') scrub = timeAt(event.clientX);
  }

  function onBarPointerUp(event: PointerEvent) {
    if (!dragging) return;
    dragging = false;
    seekTo(timeAt(event.clientX).time);
    if (event.pointerType !== 'mouse') scrub = null;
    showControls();
  }

  function onBarKey(event: KeyboardEvent) {
    const step = event.shiftKey ? 60 : 10;
    if (event.key === 'ArrowRight') seekBy(step);
    else if (event.key === 'ArrowLeft') seekBy(-step);
    else if (event.key === 'Home') seekTo(rangeStart);
    else if (event.key === 'End') isLive ? goLive() : seekTo(rangeEnd);
    else return;
    event.preventDefault();
    event.stopPropagation();
  }

  const timeLabel = (t: number) => (isLive ? `-${formatClock(seekEnd - t)}` : formatClock(t));

  // ---- Menú de ajustes ----

  interface Option {
    label: string;
    selected: boolean;
    apply: () => void;
  }

  /** RTVE etiqueta la versión original como `qaa` y la audiodescripción como `ads`. */
  function trackLabel(language: string, roles: string[] = [], label: string | null = null, forced = false) {
    const lang = language?.toLowerCase();
    let base: string;
    if (lang === 'qaa') base = 'Versión original';
    else if (lang === 'ads' || roles.includes('description')) base = 'Audiodescripción';
    else if (!lang || lang === 'und') base = label ?? 'Pista';
    else base = languageName(lang === 'spa' ? 'es' : lang);
    return forced ? `${base} (forzados)` : base;
  }

  const menu = $derived.by(() => {
    void tracksVersion;
    if (!player || !shakaLib) return null;
    const variants = (player.getVariantTracks?.() ?? []) as {
      id: number; active: boolean; height: number | null; audioId: number | null; bandwidth: number;
    }[];
    const activeVariant = variants.find((v) => v.active);
    const abrEnabled = player.getConfiguration().abr.enabled;
    const byHeight = new Map<number, (typeof variants)[number]>();
    for (const v of variants.filter((v) => v.audioId === activeVariant?.audioId)) {
      if (!v.height) continue;
      const prev = byHeight.get(v.height);
      if (!prev || v.bandwidth > prev.bandwidth) byHeight.set(v.height, v);
    }
    const heights = [...byHeight.keys()].sort((a, b) => b - a);
    const qualities: Option[] =
      heights.length < 2
        ? []
        : [
            {
              label: abrEnabled && activeVariant?.height ? `Automática (${activeVariant.height}p)` : 'Automática',
              selected: abrEnabled,
              apply: () => player.configure({ abr: { enabled: true } }),
            },
            ...heights.map((h) => ({
              label: `${h}p`,
              selected: !abrEnabled && activeVariant?.height === h,
              apply: () => {
                player.configure({ abr: { enabled: false } });
                player.selectVariantTrack(byHeight.get(h), true);
              },
            })),
          ];

    const speeds: Option[] = SPEEDS.map((s) => ({
      label: s === 1 ? 'Normal' : `${String(s).replace('.', ',')}x`,
      selected: (video?.playbackRate ?? 1) === s,
      apply: () => video && (video.playbackRate = s),
    }));

    const audioTracks = (player.getAudioTracks?.() ?? []) as { language: string; roles: string[]; label: string | null; active: boolean }[];
    const seenAudio = new Set<string>();
    const audios: Option[] = [];
    for (const track of audioTracks) {
      const label = trackLabel(track.language, track.roles, track.label);
      if (seenAudio.has(label)) continue;
      seenAudio.add(label);
      audios.push({ label, selected: track.active, apply: () => player.selectAudioTrack(track) });
    }

    const textTracks = (player.getTextTracks?.() ?? []) as {
      id: number; language: string; roles: string[]; label: string | null; active: boolean; forced: boolean;
    }[];
    const visible = player.isTextTrackVisible();
    const seenText = new Set<string>();
    const subtitles: Option[] = textTracks.length
      ? [{ label: 'Desactivados', selected: !visible, apply: () => player.setTextTrackVisibility(false) }]
      : [];
    for (const track of textTracks) {
      const label = trackLabel(track.language, track.roles, track.label, track.forced);
      if (seenText.has(label)) continue;
      seenText.add(label);
      subtitles.push({
        label,
        selected: visible && track.active,
        apply: () => {
          player.selectTextTrack(track);
          player.setTextTrackVisibility(true);
        },
      });
    }
    return { qualities, speeds, audios: audios.length > 1 ? audios : [], subtitles, subtitlesOn: visible };
  });

  function choose(option: Option) {
    option.apply();
    tracksVersion++;
  }

  function toggleSubtitles() {
    if (!player || !menu?.subtitles.length) return;
    const visible = player.isTextTrackVisible();
    player.setTextTrackVisibility(!visible);
    flash(visible ? 'Subtítulos desactivados' : 'Subtítulos activados', 'center');
    tracksVersion++;
  }

  // ---- Gestos y teclado ----

  let lastTap = { time: 0, x: 0 };

  function onSurfaceClick(event: MouseEvent) {
    const pointer = (event as PointerEvent).pointerType || lastPointerType;
    if (pointer === 'touch' || pointer === 'pen') {
      const now = Date.now();
      const width = root?.clientWidth ?? 1;
      // Doble toque a los lados: saltar 10 s, como en la app.
      if (now - lastTap.time < 300 && Math.abs(event.clientX - lastTap.x) < 80) {
        if (event.clientX < width * 0.4) seekBy(-10);
        else if (event.clientX > width * 0.6) seekBy(10);
        lastTap = { time: 0, x: 0 };
        return;
      }
      lastTap = { time: now, x: event.clientX };
      if (controlsVisible) {
        setTimeout(() => {
          if (lastTap.time === now) controlsVisible = false;
        }, 300);
      } else {
        showControls();
      }
      return;
    }
    if (menuOpen) {
      menuOpen = false;
      return;
    }
    togglePlay();
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.target instanceof HTMLInputElement && event.target.type === 'range') return;
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    const handled = (() => {
      switch (event.key) {
        case ' ':
        case 'k':
        case 'K':
          if (event.target instanceof HTMLButtonElement && event.key === ' ') return false;
          togglePlay();
          return true;
        case 'ArrowLeft':
        case 'j':
          seekBy(-10);
          return true;
        case 'ArrowRight':
        case 'l':
          seekBy(10);
          return true;
        case 'ArrowUp':
          setVolume(volume + 0.05);
          flash(`Volumen ${Math.round(volume * 100)} %`, 'center');
          return true;
        case 'ArrowDown':
          setVolume(volume - 0.05);
          flash(`Volumen ${Math.round(volume * 100)} %`, 'center');
          return true;
        case 'f':
        case 'F':
          toggleFullscreen();
          return true;
        case 'm':
        case 'M':
          toggleMute();
          return true;
        case 'c':
        case 'C':
          toggleSubtitles();
          return true;
        case 'n':
        case 'N':
          if (nextItem) playNext();
          return true;
        case 'i':
        case 'I':
          if (pipSupported) togglePip();
          return true;
        case 'Escape':
          if (menuOpen) {
            menuOpen = false;
            return true;
          }
          if (!document.fullscreenElement) {
            close();
            return true;
          }
          return false;
        default:
          return false;
      }
    })();
    if (handled) {
      event.preventDefault();
      showControls();
    }
  }

  function close() {
    saveProgress();
    nav.back();
  }

  // ---- Ciclo de vida ----

  onMount(() => {
    pipSupported = 'pictureInPictureEnabled' in document && document.pictureInPictureEnabled;
    fullscreenSupported =
      !!root?.requestFullscreen || typeof (video as HTMLVideoElement & { webkitEnterFullscreen?: unknown })?.webkitEnterFullscreen === 'function';
    const onFullscreen = () => (fullscreen = document.fullscreenElement === root);
    const onHide = () => saveProgress();
    document.addEventListener('fullscreenchange', onFullscreen);
    window.addEventListener('pagehide', onHide);
    document.addEventListener('visibilitychange', onHide);
    saveTimer = setInterval(() => {
      if (!paused) saveProgress();
      if (player && isLive) {
        const range = player.seekRange();
        seekStart = range.start;
        seekEnd = range.end;
      }
    }, PROGRESS_SAVE_EVERY_MS / 10);
    tick().then(() => root?.focus());
    start();
    showControls();
    return () => {
      document.removeEventListener('fullscreenchange', onFullscreen);
      window.removeEventListener('pagehide', onHide);
      document.removeEventListener('visibilitychange', onHide);
    };
  });

  let ticks = 0;
  function onTimeUpdate() {
    if (!video) return;
    currentTime = video.currentTime;
    const b = video.buffered;
    bufferedEnd = b.length ? b.end(b.length - 1) : 0;
    if (player && isLive) {
      const range = player.seekRange();
      seekStart = range.start;
      seekEnd = range.end;
    }
    // Cada ~10 s de reproducción, como el ticker de Android.
    if (++ticks % 40 === 0) saveProgress();
  }

  onDestroy(() => {
    destroyed = true;
    saveProgress();
    watchHistory.flush();
    extras.abort();
    if (hideTimer) clearTimeout(hideTimer);
    if (saveTimer) clearInterval(saveTimer);
    void keepScreenOn(false);
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    if (document.pictureInPictureElement) document.exitPictureInPicture().catch(() => {});
    if ('mediaSession' in navigator) navigator.mediaSession.metadata = null;
    player?.destroy();
    player = null;
    document.title = 'OpenRTVE';
  });
</script>

<!-- El reproductor gestiona su propio teclado, como una aplicación. -->
<!-- svelte-ignore a11y_no_noninteractive_tabindex, a11y_no_noninteractive_element_interactions -->
<div
  class="player"
  class:hide-cursor={!controlsVisible}
  bind:this={root}
  tabindex="0"
  role="application"
  aria-roledescription="reproductor"
  aria-label="Reproductor: {title}"
  onkeydown={onKeydown}
  onpointermove={(e) => e.pointerType === 'mouse' && showControls()}
  onpointerdown={(e) => (lastPointerType = e.pointerType)}
>
  <!-- svelte-ignore a11y_media_has_caption -->
  <video
    bind:this={video}
    playsinline
    onplay={() => { paused = false; keepScreenOn(true); showControls(); }}
    onpause={() => { paused = true; keepScreenOn(false); saveProgress(); controlsVisible = true; }}
    onended={() => { paused = true; saveProgress(); if (nextItem && settings.current.autoplayNext) playNext(); }}
    onwaiting={() => (buffering = true)}
    onplaying={() => (buffering = false)}
    ontimeupdate={onTimeUpdate}
    ondurationchange={() => (duration = Number.isFinite(video?.duration) ? video!.duration : 0)}
    onratechange={() => tracksVersion++}
    ondblclick={(e) => (e.target as Element) === video && toggleFullscreen()}
    onclick={onSurfaceClick}
  ></video>
  <div class="text-layer" bind:this={textContainer}></div>

  {#if buffering && !errorMessage}
    <div class="center-spinner" aria-label="Cargando"><span></span></div>
  {/if}

  {#if gestureFeedback}
    <div class="feedback {gestureFeedback.side}">{gestureFeedback.text}</div>
  {/if}

  {#if errorMessage}
    <div class="error">
      <p>{errorMessage}</p>
      <div class="error-actions">
        <button class="btn btn-tonal" onclick={close}>Volver</button>
        <button class="btn btn-primary" onclick={retry}>Reintentar</button>
      </div>
    </div>
  {/if}

  <div class="chrome" class:visible={controlsVisible || paused || !!errorMessage}>
    <div class="top">
      <button class="icon-btn" onclick={close} aria-label="Cerrar reproductor"><Icon name="back" /></button>
      <div class="titles">
        <span class="title">{title}</span>
        {#if subtitle && subtitle !== title}<span class="subtitle">{subtitle}</span>{/if}
      </div>
    </div>

    {#if !errorMessage}
      <div class="middle">
        {#if !isLive || seekEnd - seekStart > 60}
          <button class="icon-btn big-side" onclick={() => seekBy(-10)} aria-label="Retroceder 10 segundos"><Icon name="rewind" size={36} badge="10" /></button>
        {/if}
        <button class="icon-btn big" onclick={togglePlay} aria-label={paused ? 'Reproducir' : 'Pausar'}>
          <Icon name={paused ? 'play' : 'pause'} size={48} />
        </button>
        {#if !isLive || !atLiveEdge}
          <button class="icon-btn big-side" onclick={() => seekBy(10)} aria-label="Avanzar 10 segundos"><Icon name="fastForward" size={36} badge="10" /></button>
        {/if}
      </div>

      <div class="bottom">
        {#if rangeEnd > rangeStart}
          <div
            class="bar"
            class:dragging
            bind:this={bar}
            role="slider"
            tabindex="0"
            aria-label="Posición"
            aria-valuemin={0}
            aria-valuemax={Math.round(rangeEnd - rangeStart)}
            aria-valuenow={Math.round(currentTime - rangeStart)}
            aria-valuetext={timeLabel(currentTime)}
            onpointerdown={onBarPointerDown}
            onpointermove={onBarPointerMove}
            onpointerup={onBarPointerUp}
            onpointerleave={() => !dragging && (scrub = null)}
            onkeydown={onBarKey}
          >
            <div class="rail">
              <div class="buffered" style:width="{Math.min(1, bufferedFraction) * 100}%"></div>
              <div class="played" style:width="{Math.min(1, (dragging && scrub ? scrub.fraction : fraction)) * 100}%"></div>
            </div>
            <div class="thumb" style:left="{Math.min(1, (dragging && scrub ? scrub.fraction : fraction)) * 100}%"></div>
            {#if scrub}
              <div class="preview" style:left="clamp(80px, {scrub.fraction * 100}%, calc(100% - 80px))">
                {#if scrubCue && sprite}
                  <div class="sprite" style:aspect-ratio="{scrubCue.width} / {scrubCue.height}">
                    <div
                      style:width="{scrubCue.width}px"
                      style:height="{scrubCue.height}px"
                      style:background-image="url({sprite.imageUrl})"
                      style:background-position="-{scrubCue.x}px -{scrubCue.y}px"
                      style:transform="scale({160 / scrubCue.width})"
                    ></div>
                  </div>
                {/if}
                <span>{timeLabel(scrub.time)}</span>
              </div>
            {/if}
          </div>
        {/if}

        <div class="buttons">
          <button class="icon-btn" onclick={togglePlay} aria-label={paused ? 'Reproducir' : 'Pausar'}><Icon name={paused ? 'play' : 'pause'} /></button>
          {#if nextItem}
            <button class="icon-btn" onclick={playNext} aria-label="Siguiente episodio" title="Siguiente: {nextItem.title}"><Icon name="skipNext" /></button>
          {/if}
          <div class="volume">
            <button class="icon-btn" onclick={toggleMute} aria-label={muted ? 'Activar sonido' : 'Silenciar'}><Icon name={muted || volume === 0 ? 'volumeOff' : 'volume'} /></button>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={muted ? 0 : volume}
              oninput={(e) => setVolume(Number(e.currentTarget.value))}
              aria-label="Volumen"
            />
          </div>
          {#if isLive}
            {#if atLiveEdge}
              <span class="live on">DIRECTO</span>
            {:else}
              <button class="live" onclick={goLive}>IR AL DIRECTO</button>
            {/if}
          {:else if duration > 0}
            <span class="time">{formatClock(currentTime)} <span class="sep">/</span> {formatClock(duration)}</span>
          {/if}
          <span class="spacer"></span>
          {#if menu?.subtitles.length}
            <button class="icon-btn" class:on={menu.subtitlesOn} onclick={toggleSubtitles} aria-label="Subtítulos" title="Subtítulos (c)">
              <Icon name="subtitles" />
            </button>
          {/if}
          <div class="menu-anchor">
            <button class="icon-btn" onclick={() => (menuOpen = !menuOpen)} aria-label="Ajustes de reproducción" aria-expanded={menuOpen}>
              <Icon name="tune" />
            </button>
            {#if menuOpen && menu}
              <div class="menu" role="dialog" aria-label="Ajustes de reproducción">
                {#each [{ title: 'Calidad', options: menu.qualities }, { title: 'Velocidad', options: menu.speeds }, { title: 'Pista de audio', options: menu.audios }, { title: 'Subtítulos', options: menu.subtitles }] as group (group.title)}
                  {#if group.options.length}
                    <div class="group">
                      <h3>{group.title}</h3>
                      <div class="options">
                        {#each group.options as option (option.label)}
                          <button class="option" aria-pressed={option.selected} onclick={() => choose(option)}>
                            {#if option.selected}<Icon name="check" size={18} />{/if}
                            {option.label}
                          </button>
                        {/each}
                      </div>
                    </div>
                  {/if}
                {/each}
              </div>
            {/if}
          </div>
          {#if pipSupported}
            <button class="icon-btn" onclick={togglePip} aria-label="Imagen en imagen"><Icon name="pip" /></button>
          {/if}
          {#if fullscreenSupported}
            <button class="icon-btn" onclick={toggleFullscreen} aria-label={fullscreen ? 'Salir de pantalla completa' : 'Pantalla completa'}>
              <Icon name={fullscreen ? 'fullscreenExit' : 'fullscreen'} />
            </button>
          {/if}
        </div>
      </div>
    {/if}
  </div>

  {#if showNext && nextItem && !errorMessage}
    <div class="next-card">
      <span class="next-label">{nextCountdown != null ? `Siguiente en ${nextCountdown} s` : 'Siguiente'}</span>
      <span class="next-title">{nextItem.title}</span>
      <button class="btn btn-primary" onclick={playNext}><Icon name="skipNext" size={20} /> Siguiente episodio</button>
    </div>
  {/if}
</div>

<style>
  .player {
    position: fixed;
    inset: 0;
    z-index: 200;
    background: black;
    color: white;
    outline: none;
    user-select: none;
    -webkit-user-select: none;
  }

  .player.hide-cursor {
    cursor: none;
  }

  video {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: black;
  }

  .text-layer {
    position: absolute;
    inset: 0;
    pointer-events: none;
  }

  .text-layer :global(.shaka-text-container) {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    padding-bottom: 6%;
    font-size: clamp(16px, 3.4vh, 38px);
    line-height: 1.35;
    color: white;
    transition: padding-bottom 0.2s;
  }

  /* Con los controles a la vista, los subtítulos suben para no quedar debajo. */
  .text-layer:has(~ .chrome.visible) :global(.shaka-text-container) {
    padding-bottom: 130px;
  }

  .text-layer :global(.shaka-text-container span.shaka-text-wrapper) {
    display: inline;
    background: none;
  }

  .center-spinner {
    position: absolute;
    inset: 0;
    display: grid;
    place-items: center;
    pointer-events: none;
  }

  .center-spinner span {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    border: 4px solid rgb(255 255 255 / 0.2);
    border-top-color: var(--accent);
    animation: spin 0.9s linear infinite;
  }

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }

  .feedback {
    position: absolute;
    top: 50%;
    transform: translateY(-50%);
    padding: 10px 18px;
    border-radius: 999px;
    background: rgb(0 0 0 / 0.6);
    font-weight: 600;
    pointer-events: none;
    z-index: 3;
  }

  .feedback.left {
    left: 12%;
  }

  .feedback.right {
    right: 12%;
  }

  .feedback.center {
    left: 50%;
    transform: translate(-50%, -50%);
  }

  .chrome {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    opacity: 0;
    transition: opacity 0.25s;
    pointer-events: none;
    background:
      linear-gradient(to bottom, rgb(0 0 0 / 0.7), transparent 22%),
      linear-gradient(to top, rgb(0 0 0 / 0.8), transparent 32%);
  }

  .chrome.visible {
    opacity: 1;
  }

  .chrome.visible > * {
    pointer-events: auto;
  }

  .top {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: max(12px, env(safe-area-inset-top)) max(clamp(8px, 2vw, 32px), env(safe-area-inset-right)) 0
      max(clamp(8px, 2vw, 32px), env(safe-area-inset-left));
  }

  .titles {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .title {
    font-size: clamp(1rem, 0.9rem + 0.6vw, 1.4rem);
    font-weight: 700;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .subtitle {
    font-size: 0.9rem;
    color: rgb(255 255 255 / 0.75);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .middle {
    align-self: center;
    display: flex;
    align-items: center;
    gap: clamp(24px, 6vw, 72px);
    pointer-events: none !important;
  }

  .middle > * {
    pointer-events: auto;
  }

  .big {
    width: 76px;
    height: 76px;
    background: rgb(0 0 0 / 0.45);
  }

  .big-side {
    width: 60px;
    height: 60px;
  }

  @media (hover: hover) and (pointer: fine) {
    .middle {
      visibility: hidden;
    }
  }

  .bottom {
    padding: 0 max(clamp(8px, 2vw, 32px), env(safe-area-inset-right)) max(10px, env(safe-area-inset-bottom))
      max(clamp(8px, 2vw, 32px), env(safe-area-inset-left));
  }

  .bar {
    position: relative;
    height: 28px;
    margin: 0 8px;
    display: flex;
    align-items: center;
    cursor: pointer;
    touch-action: none;
  }

  .rail {
    position: relative;
    width: 100%;
    height: 4px;
    border-radius: 2px;
    background: rgb(255 255 255 / 0.25);
    overflow: hidden;
    transition: height 0.15s;
  }

  .bar:hover .rail,
  .bar.dragging .rail {
    height: 6px;
  }

  .buffered,
  .played {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
  }

  .buffered {
    background: rgb(255 255 255 / 0.35);
  }

  .played {
    background: var(--accent);
  }

  .thumb {
    position: absolute;
    top: 50%;
    width: 14px;
    height: 14px;
    margin-left: -7px;
    margin-top: -7px;
    border-radius: 50%;
    background: var(--accent);
    transform: scale(0);
    transition: transform 0.15s;
  }

  .bar:hover .thumb,
  .bar.dragging .thumb,
  .bar:focus-visible .thumb {
    transform: scale(1);
  }

  .preview {
    position: absolute;
    bottom: 30px;
    transform: translateX(-50%);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    pointer-events: none;
  }

  .preview span {
    padding: 2px 8px;
    border-radius: 4px;
    background: rgb(0 0 0 / 0.75);
    font-size: 0.85rem;
    font-variant-numeric: tabular-nums;
  }

  .sprite {
    width: 160px;
    overflow: hidden;
    border-radius: 6px;
    border: 2px solid white;
    background: black;
  }

  .sprite > div {
    transform-origin: top left;
  }

  .buttons {
    display: flex;
    align-items: center;
    gap: 2px;
  }

  .buttons .icon-btn:hover {
    background: rgb(255 255 255 / 0.12);
  }

  .icon-btn.on {
    color: var(--accent);
  }

  .volume {
    display: flex;
    align-items: center;
  }

  .volume input {
    width: 0;
    opacity: 0;
    transition: width 0.2s, opacity 0.2s;
    accent-color: white;
  }

  .volume:hover input,
  .volume:focus-within input {
    width: 90px;
    opacity: 1;
    margin-right: 8px;
  }

  @media (pointer: coarse) {
    .volume input {
      display: none;
    }
  }

  .time {
    margin-left: 8px;
    font-size: 0.9rem;
    font-variant-numeric: tabular-nums;
  }

  .sep {
    opacity: 0.6;
  }

  .live {
    margin-left: 8px;
    padding: 4px 10px;
    border-radius: 4px;
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    background: rgb(255 255 255 / 0.15);
  }

  .live.on {
    background: var(--live);
  }

  button.live:hover {
    background: rgb(255 255 255 / 0.28);
  }

  .spacer {
    flex: 1;
  }

  .menu-anchor {
    position: relative;
  }

  .menu {
    position: absolute;
    right: 0;
    bottom: 52px;
    width: min(340px, 86vw);
    max-height: min(70vh, 520px);
    overflow-y: auto;
    padding: 8px 0;
    border-radius: 12px;
    background: rgb(28 31 44 / 0.97);
    backdrop-filter: blur(16px);
    box-shadow: 0 16px 40px rgb(0 0 0 / 0.6);
  }

  .group {
    padding: 6px 16px 10px;
  }

  .group + .group {
    border-top: 1px solid rgb(255 255 255 / 0.08);
  }

  .group h3 {
    font-size: 0.78rem;
    font-weight: 600;
    color: rgb(255 255 255 / 0.6);
    text-transform: uppercase;
    letter-spacing: 0.06em;
    padding: 6px 0;
  }

  .options {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .option {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 34px;
    padding: 0 12px;
    border-radius: 8px;
    border: 1px solid rgb(255 255 255 / 0.18);
    font-size: 0.88rem;
  }

  .option:hover {
    background: rgb(255 255 255 / 0.08);
  }

  .option[aria-pressed='true'] {
    background: rgb(255 106 71 / 0.22);
    border-color: transparent;
    color: #ffb4a2;
  }

  .error {
    position: absolute;
    inset: 0;
    z-index: 2;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 18px;
    padding: 24px;
    text-align: center;
    background: rgb(0 0 0 / 0.7);
    font-size: 1.05rem;
  }

  .error-actions {
    display: flex;
    gap: 12px;
  }

  .next-card {
    position: absolute;
    right: clamp(12px, 3vw, 40px);
    bottom: 120px;
    z-index: 3;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    max-width: 340px;
    padding: 14px 16px;
    border-radius: 12px;
    background: rgb(20 22 32 / 0.92);
    box-shadow: 0 12px 32px rgb(0 0 0 / 0.5);
    animation: rise 0.25s ease-out;
  }

  .next-label {
    font-size: 0.8rem;
    color: rgb(255 255 255 / 0.7);
  }

  .next-title {
    font-weight: 600;
    margin-bottom: 4px;
  }

  @keyframes rise {
    from {
      transform: translateY(12px);
      opacity: 0;
    }
  }
</style>
