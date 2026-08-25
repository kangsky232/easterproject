<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = useDashboardStore()
const content = ref('请注意，检测到火情，请立即疏散！')

watch(
  () => props.open,
  (open) => {
    if (open) content.value = '请注意，检测到火情，请立即疏散！'
  },
)

async function onSubmit(): Promise<void> {
  const ok = await store.sendBroadcast(content.value)
  if (ok) emit('close')
}
</script>

<template>
  <div v-if="open" class="modal" role="dialog" aria-modal="true" aria-label="广播指令">
    <div class="modal-card">
      <div class="modal-head">
        <h3>{{ store.broadcastPersistenceOnly ? '创建广播指令' : '联动广播' }}</h3>
        <button type="button" class="modal-close" aria-label="关闭" @click="emit('close')">
          <AppIcon name="close" :size="18" />
        </button>
      </div>
      <form class="modal-form" @submit.prevent="onSubmit">
        <p v-if="store.broadcastPersistenceOnly" class="capability-note">
          当前仅保存广播指令，尚未接入 MQTT 或其他实际下发通道；创建成功不代表设备已收到。
        </p>
        <label>广播内容<textarea v-model="content" rows="3"></textarea></label>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" @click="emit('close')">取消</button>
          <button type="submit" class="btn-primary">
            {{ store.broadcastPersistenceOnly ? '创建指令记录' : '发送广播' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>
