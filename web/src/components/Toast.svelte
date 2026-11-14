<script lang="ts">
  import { messages } from '../lib/ui/messages.svelte';
</script>

<div class="toast-host" aria-live="polite">
  {#if messages.current}
    {@const message = messages.current}
    <div class="toast" role="status">
      <span>{message.text}</span>
      {#if message.action}
        <button
          class="btn btn-text"
          onclick={() => {
            message.action?.run();
            messages.dismiss(message.id);
          }}>{message.action.label}</button
        >
      {/if}
    </div>
  {/if}
</div>

<style>
  .toast-host {
    position: fixed;
    left: 0;
    right: 0;
    bottom: calc(var(--bottom-nav-h) + 16px + env(safe-area-inset-bottom));
    display: flex;
    justify-content: center;
    pointer-events: none;
    z-index: 100;
    padding: 0 16px;
  }

  @media (min-width: 840px) {
    .toast-host {
      bottom: 24px;
      left: var(--rail-w);
    }
  }

  .toast {
    pointer-events: auto;
    display: flex;
    align-items: center;
    gap: 12px;
    min-height: 48px;
    max-width: 560px;
    padding: 6px 8px 6px 16px;
    border-radius: 6px;
    background: #e6e1e6;
    color: #1c1b1f;
    font-size: 0.92rem;
    box-shadow: 0 8px 24px rgb(0 0 0 / 0.45);
    animation: rise 0.2s ease-out;
  }

  .toast .btn-text {
    color: #b3361a;
  }

  @keyframes rise {
    from {
      transform: translateY(12px);
      opacity: 0;
    }
  }
</style>
