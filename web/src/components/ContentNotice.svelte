<script lang="ts">
  import PlaybackMetricsInfo from './PlaybackMetricsInfo.svelte';
  import { contentNotice } from '../lib/ui/contentNotice.svelte';
  import { rightsContactEmail, rightsContactUrl } from '../lib/ui/rightsContact';

  let dialog = $state<HTMLDialogElement>();
  let continueButton: HTMLButtonElement;

  $effect(() => {
    if (!dialog) return;
    if (contentNotice.open && !dialog.open) {
      dialog.showModal();
      continueButton?.focus({ preventScroll: true });
      dialog.scrollTop = 0;
      dialog.querySelector('.description')?.scrollTo(0, 0);
    } else if (!contentNotice.open && dialog.open) {
      dialog.close();
    }
  });
</script>

<dialog
  bind:this={dialog}
  aria-labelledby="content-notice-title"
  aria-describedby="content-notice-description"
  onclose={() => contentNotice.dismiss()}
>
  <p class="eyebrow">Sobre OpenRTVE</p>
  <h2 id="content-notice-title">Una aplicación independiente</h2>
  <div id="content-notice-description" class="description">
    <p>OpenRTVE es una aplicación independiente y no oficial, gratuita y sin publicidad. No está afiliada a RTVE ni cuenta con su patrocinio.</p>
    <p>
      Los vídeos se reproducen directamente desde los servidores de RTVE. OpenRTVE no aloja ni almacena copias de los vídeos
      en sus servidores.
    </p>
    <p>
      Los contenidos y las marcas pertenecen a sus respectivos titulares. El acceso está sujeto a la disponibilidad y a las
      <a href="https://www.rtve.es/comunes/aviso_legal.html" target="_blank" rel="noopener noreferrer">condiciones de RTVE Play</a>.
    </p>
    <p>
      Para cuestiones de derechos o solicitudes de retirada de enlaces, contacta con el responsable de OpenRTVE en
      <a href={rightsContactUrl}>{rightsContactEmail}</a>, indicando el contenido afectado y el motivo de la solicitud.
    </p>
    <p>Medimos las reproducciones y su duración, sin perfiles de usuario.</p>
    <PlaybackMetricsInfo />
  </div>
  <div class="actions">
    <a class="btn btn-tonal" href="https://www.rtve.es/play/">Ir a RTVE Play</a>
    <button class="btn btn-primary" bind:this={continueButton} onclick={() => contentNotice.dismiss()}>Entendido, continuar</button>
  </div>
</dialog>

<style>
  dialog {
    width: 560px;
    max-width: calc(100vw - 32px);
    max-height: calc(100dvh - 32px);
    margin: auto;
    padding: clamp(24px, 4vw, 32px);
    overflow: hidden;
    overscroll-behavior: contain;
    border: 1px solid var(--outline);
    border-radius: var(--radius-lg);
    background: var(--surface);
    color: var(--text);
    box-shadow: 0 24px 80px rgb(0 0 0 / 0.45);
  }

  dialog::backdrop {
    background: rgb(0 0 0 / 0.8);
  }

  dialog[open] {
    display: flex;
    flex-direction: column;
  }

  .eyebrow {
    flex-shrink: 0;
    margin-bottom: 8px;
    color: var(--accent);
    font-size: 0.85rem;
    font-weight: 600;
  }

  h2 {
    flex-shrink: 0;
    margin-bottom: 20px;
    font-size: clamp(1.4rem, 4vw, 1.7rem);
    line-height: 1.2;
  }

  .description {
    min-height: 0;
    overflow: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--outline) var(--surface);
    display: grid;
    gap: 14px;
    color: var(--muted);
    line-height: 1.55;
  }

  .description a {
    color: var(--accent);
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  .actions {
    flex-shrink: 0;
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 24px;
  }

  @media (max-width: 480px) {
    .actions {
      flex-direction: column-reverse;
    }

    .actions .btn {
      width: 100%;
      min-height: 48px;
      padding: 12px 18px;
      white-space: normal;
      text-align: center;
    }
  }
</style>
