<script setup lang="ts">
import { isDark, toggleTheme } from '@/theme'
import AppIcon from '@/components/AppIcon.vue'
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :title="isDark ? '切换到白天模式' : '切换到黑夜模式'"
    :aria-label="isDark ? '切换到白天模式' : '切换到黑夜模式'"
    @click="toggleTheme"
  >
    <AppIcon :name="isDark ? 'sun' : 'moon'" :size="16" />
  </button>
</template>
