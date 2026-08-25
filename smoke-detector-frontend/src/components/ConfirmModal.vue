<script setup lang="ts">
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const store = useDashboardStore()
</script>

<template>
  <div v-if="store.confirmVisible" class="modal" role="alertdialog" aria-modal="true" :aria-label="store.confirmTitle">
    <div class="modal-card">
      <div class="modal-head">
        <h3>{{ store.confirmTitle }}</h3>
        <button type="button" class="modal-close" aria-label="关闭" @click="store.resolveConfirm(false)">
          <AppIcon name="close" :size="18" />
        </button>
      </div>
      <p class="confirm-text">{{ store.confirmMessage }}</p>
      <div class="modal-actions">
        <button type="button" class="btn-ghost" @click="store.resolveConfirm(false)">取消</button>
        <button type="button" class="btn-danger" @click="store.resolveConfirm(true)">确定</button>
      </div>
    </div>
  </div>
</template>
