<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const store = useDashboardStore()
const copied = ref(false)

watch(
  () => store.tokenModalVisible,
  (visible) => {
    if (visible) copied.value = false
  },
)

async function copyToken(): Promise<void> {
  const text = store.deviceAccessToken
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 2000)
  } catch (_) {
    // 降级：选中文本便于手动复制
    window.getSelection()?.selectAllChildren(document.querySelector('.token-box') ?? document.body)
  }
}
</script>

<template>
  <div v-if="store.tokenModalVisible" class="modal" role="dialog" aria-modal="true" aria-label="设备接入令牌">
    <div class="modal-card">
      <div class="modal-head">
        <h3>设备接入令牌</h3>
        <button type="button" class="modal-close" aria-label="关闭" @click="store.closeTokenModal()">
          <AppIcon name="close" :size="18" />
        </button>
      </div>

      <p class="token-warn">该令牌仅显示这一次，请立即保存到设备安全存储，服务端只保存其摘要。</p>
      <div class="token-box">{{ store.deviceAccessToken }}</div>

      <div class="modal-actions">
        <button type="button" class="btn-primary" @click="copyToken">
          <AppIcon name="copy" :size="14" /> {{ copied ? '已复制' : '复制令牌' }}
        </button>
        <button type="button" class="btn-ghost" @click="store.closeTokenModal()">我已保存</button>
      </div>
    </div>
  </div>
</template>
