<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'

const store = useDashboardStore()
</script>

<template>
  <div
    v-show="store.toastVisible"
    class="broadcast-toast"
    :class="`toast--${store.toastKind}`"
    role="status"
    aria-live="polite"
  >
    {{ store.toast }}
  </div>
</template>
