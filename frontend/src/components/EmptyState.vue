<template>
  <div class="empty-state">
    <div class="empty-state-art" aria-hidden="true">
      <slot name="art">
        <svg viewBox="0 0 64 64" width="64" height="64" fill="none"
             stroke="currentColor" stroke-width="1.5"
             stroke-linecap="round" stroke-linejoin="round">
          <rect x="10" y="14" width="44" height="36" rx="4" />
          <path d="M10 24h44M22 14V8M42 14V8" />
          <path d="M20 36l6 6 12-14" />
        </svg>
      </slot>
    </div>
    <p v-if="title" class="empty-state-title">{{ title }}</p>
    <p v-if="message" class="empty-state-message">{{ message }}</p>
    <div v-if="$slots.default" class="empty-state-action">
      <slot />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title:   { type: String, default: '' },
  message: { type: String, default: '' },
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: var(--space-6) var(--space-5);
  gap: var(--space-3);
}
.empty-state-art {
  color: var(--purple-900, #534AB7);
  opacity: 0.55;
}
.empty-state-title {
  font-size: var(--text-xl);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}
.empty-state-message {
  font-size: var(--text-md);
  color: var(--text-secondary);
  max-width: 32ch;
  line-height: 1.5;
}
.empty-state-action {
  margin-top: var(--space-3);
}
</style>
