<script lang="ts">
  import { nav } from '../lib/ui/nav.svelte';
  import Icon from './Icon.svelte';

  /**
   * Ajustes en el móvil, como en la app: un engranaje arriba a la derecha de Inicio y
   * de Explorar. La barra inferior queda para las cuatro secciones de uso diario; en
   * pantallas anchas Ajustes va al pie del panel lateral y este botón no se muestra.
   */
  let { veil = false }: { veil?: boolean } = $props();
</script>

<button class="icon-btn settings-btn" class:veil onclick={() => nav.navigate({ name: 'settings' })} aria-label="Ajustes" title="Ajustes">
  <Icon name="settings" />
</button>

<style>
  /* Sobre una imagen (la cabecera de Inicio): fondo oscuro para que se lea. */
  .veil {
    background: rgb(0 0 0 / 0.35);
    color: white;
  }

  .veil:hover {
    background: rgb(0 0 0 / 0.55);
  }

  @media (min-width: 840px) {
    .settings-btn {
      display: none;
    }
  }
</style>
