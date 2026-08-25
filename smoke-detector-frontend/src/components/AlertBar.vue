<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const store = useDashboardStore()

defineEmits<{ (e: 'show-alarms'): void }>()
</script>

<template>
  <div v-show="store.pendingCount > 0" class="alert-bar" role="button" tabindex="0" @click="$emit('show-alarms')">
    <span class="alert-bar-icon" aria-hidden="true"><AppIcon name="alert" :size="18" /></span>
    <span>{{ store.pendingCount }} 条未处置告警</span>
    <span class="alert-bar-cta">点击查看</span>
  </div>
</template>
