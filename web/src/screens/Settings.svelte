<script lang="ts">
  import TopBar from '../components/TopBar.svelte';
  import { documentCache } from '../lib/data/documentCache';
  import { QUALITY_CHOICES, settings } from '../lib/data/settings.svelte';
  import { contentNotice } from '../lib/ui/contentNotice.svelte';
  import { messages } from '../lib/ui/messages.svelte';
  import { rightsContactEmail, rightsContactUrl } from '../lib/ui/rightsContact';
  import { formatBytes } from '../lib/ui/text';

  /** Ajustes (SettingsScreen.kt): reproducción, almacenamiento y acerca de. */
  let cacheBytes = $state<number | null>(null);
  let widevine = $state<boolean | null>(null);

  async function measure() {
    try {
      const estimate = await navigator.storage?.estimate();
      cacheBytes = estimate?.usage ?? 0;
    } catch {
      cacheBytes = 0;
    }
  }

  async function clearCache() {
    await documentCache.clear();
    try {
      for (const key of await caches.keys()) if (key.startsWith('openrtve-runtime')) await caches.delete(key);
    } catch {
      // Sin Cache Storage no hay nada más que borrar.
    }
    messages.show('Caché borrada');
    measure();
  }

  measure();
  navigator
    .requestMediaKeySystemAccess?.('com.widevine.alpha', [
      { initDataTypes: ['cenc'], videoCapabilities: [{ contentType: 'video/mp4; codecs="avc1.42E01E"' }] },
    ])
    .then(
      () => (widevine = true),
      () => (widevine = false),
    );

  const version = __APP_VERSION__;
</script>

<TopBar title="Ajustes" />

<div class="screen-body settings">
  <h2 class="label">Reproducción</h2>
  <label class="row">
    <span class="text">
      <span class="title">Reproducción automática</span>
      <span class="summary">Encadena el siguiente episodio al terminar</span>
    </span>
    <input
      type="checkbox"
      role="switch"
      checked={settings.current.autoplayNext}
      onchange={(e) => settings.update({ autoplayNext: e.currentTarget.checked })}
    />
  </label>
  <div class="row column">
    <span class="text">
      <span class="title">Calidad máxima de vídeo</span>
      <span class="summary">Automática usa la mejor que admita la conexión; 576p equivale al ahorro de datos</span>
    </span>
    <div class="chips flush" role="radiogroup" aria-label="Calidad máxima">
      {#each QUALITY_CHOICES as height (height)}
        <button
          class="chip"
          role="radio"
          aria-checked={settings.current.maxVideoHeight === height}
          onclick={() => settings.update({ maxVideoHeight: height })}>{height === 0 ? 'Automática' : `${height}p`}</button
        >
      {/each}
    </div>
  </div>
  <label class="row">
    <span class="text">
      <span class="title">Subtítulos por defecto</span>
      <span class="summary">Activa los subtítulos en español cuando existan</span>
    </span>
    <input
      type="checkbox"
      role="switch"
      checked={settings.current.subtitlesByDefault}
      onchange={(e) => settings.update({ subtitlesByDefault: e.currentTarget.checked })}
    />
  </label>
  <div class="row">
    <span class="text">
      <span class="title">Contenido protegido (Widevine)</span>
      <span class="summary">
        {#if widevine === null}Comprobando…{:else if widevine}Disponible en este navegador{:else}No disponible: se usa la versión sin protección cuando RTVE la ofrece{/if}
      </span>
    </span>
  </div>

  <hr />
  <h2 class="label">Almacenamiento</h2>
  <button class="row action" onclick={clearCache}>
    <span class="text">
      <span class="title">Borrar caché</span>
      <span class="summary">Catálogo e imágenes guardados: {cacheBytes == null ? '…' : formatBytes(cacheBytes)}</span>
    </span>
  </button>

  <hr />
  <h2 class="label">Acerca de</h2>
  <div class="row">
    <span class="text">
      <span class="title">Versión {version}</span>
      <span class="summary">Cliente independiente para el catálogo público de RTVE Play. No está afiliado a RTVE.</span>
    </span>
  </div>
  <button class="row action" onclick={() => contentNotice.show()}>
    <span class="text">
      <span class="title">Aviso sobre los contenidos</span>
      <span class="summary">Independencia del proyecto, origen de los vídeos y derechos de sus titulares</span>
    </span>
  </button>
  <a class="row action" href={rightsContactUrl}>
    <span class="text">
      <span class="title">Derechos y solicitudes de retirada</span>
      <span class="summary">
        Escribe a {rightsContactEmail}, indicando el contenido afectado y el motivo de la solicitud.
      </span>
    </span>
  </a>
</div>

<style>
  .settings {
    max-width: 820px;
  }

  .label {
    color: var(--accent);
    font-size: 0.85rem;
    font-weight: 600;
    padding: 16px var(--gutter) 6px;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 16px;
    width: 100%;
    padding: 12px var(--gutter);
    text-align: left;
    cursor: default;
  }

  label.row,
  .row.action {
    cursor: pointer;
  }

  label.row:hover,
  .row.action:hover {
    background: rgb(255 255 255 / 0.03);
  }

  .row.column {
    flex-direction: column;
    align-items: stretch;
  }

  .text {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .title {
    font-size: 1rem;
  }

  .summary {
    color: var(--muted);
    font-size: 0.88rem;
  }

  /* Pocas opciones: se reparten en líneas en vez de quedar cortadas por el borde. */
  .chips.flush {
    padding: 4px 0 0;
    flex-wrap: wrap;
    overflow: visible;
  }

  hr {
    border: 0;
    border-top: 1px solid var(--outline);
    margin: 8px 0;
  }

  /* Interruptor de Material 3 sobre un checkbox nativo. */
  input[type='checkbox'] {
    appearance: none;
    flex: none;
    position: relative;
    width: 52px;
    height: 32px;
    margin: 0;
    border-radius: 16px;
    border: 2px solid #938f99;
    background: var(--surface-3);
    cursor: pointer;
    transition: background-color 0.2s, border-color 0.2s;
  }

  input[type='checkbox']::after {
    content: '';
    position: absolute;
    top: 50%;
    left: 6px;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: #938f99;
    transform: translateY(-50%);
    transition: left 0.2s, width 0.2s, height 0.2s, background-color 0.2s;
  }

  input[type='checkbox']:checked {
    background: var(--accent);
    border-color: var(--accent);
  }

  input[type='checkbox']:checked::after {
    left: 22px;
    width: 24px;
    height: 24px;
    background: var(--on-accent);
  }
</style>
