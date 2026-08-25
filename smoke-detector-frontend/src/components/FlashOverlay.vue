<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const store = useDashboardStore()
</script>

<template>
  <div v-show="store.flashVisible" class="flash-overlay" role="alertdialog" aria-label="新告警" @click="store.hideFlash()">
    <div class="flash-banner">
      <div class="flash-icon" aria-hidden="true"><AppIcon name="siren" :size="52" /></div>
      <div class="flash-title">新告警</div>
      <div class="flash-text">{{ store.flashText }}</div>
    </div>
  </div>
</template>
