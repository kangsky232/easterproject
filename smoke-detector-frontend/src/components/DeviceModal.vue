<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Device } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const props = defineProps<{
  open: boolean
  device: Device | null
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const store = useDashboardStore()

const deviceCode = ref('')
const name = ref('')
const location = ref('')
const threshold = ref(100)
const touched = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    deviceCode.value = props.device?.deviceCode ?? ''
    name.value = props.device?.name ?? ''
    location.value = props.device?.location ?? ''
    threshold.value = 100
    touched.value = false
  },
)

const errors = computed(() => {
  const result: Record<string, string> = {}
  if (!props.device && !deviceCode.value.trim()) result.deviceCode = '请输入设备编码'
  if (!name.value.trim()) result.name = '请输入名称'
  if (!location.value.trim()) result.location = '请输入安装位置'
  return result
})

const invalid = computed(() => Object.keys(errors.value).length > 0)

async function onSubmit(): Promise<void> {
  touched.value = true
  if (invalid.value) return
  const ok = await store.saveDevice({
    id: props.device?.id,
    deviceId: deviceCode.value.trim(),
    deviceName: name.value.trim(),
    location: location.value.trim(),
    threshold: Number(threshold.value),
  })
  if (ok) emit('close')
}
</script>

<template>
  <div v-if="open" class="modal" role="dialog" aria-modal="true" :aria-label="device ? '编辑设备' : '添加设备'">
    <div class="modal-card">
      <div class="modal-head">
        <h3>{{ device ? '编辑设备' : '添加设备' }}</h3>
        <button type="button" class="modal-close" aria-label="关闭" @click="emit('close')">
          <AppIcon name="close" :size="18" />
        </button>
      </div>
      <form class="modal-form" novalidate @submit.prevent="onSubmit">
        <label>设备编码<input v-model="deviceCode" :disabled="Boolean(device)" placeholder="如 SMOKE-004" /></label>
        <p v-if="touched && errors.deviceCode" class="field-error">{{ errors.deviceCode }}</p>
        <label>名称<input v-model="name" placeholder="如 3栋1单元101室烟感" /></label>
        <p v-if="touched && errors.name" class="field-error">{{ errors.name }}</p>
        <label>位置<input v-model="location" placeholder="如 3栋-1单元-101" /></label>
        <p v-if="touched && errors.location" class="field-error">{{ errors.location }}</p>
        <label>烟雾预警阈值(ppm)<input v-model.number="threshold" type="number" disabled /></label>
        <p class="field-help">按当前安全规则固定为 100 ppm；烟雾浓度大于 300 ppm 判定为危险。</p>
        <div class="modal-actions">
          <button type="button" class="btn-ghost" @click="emit('close')">取消</button>
          <button type="submit" class="btn-primary">保存</button>
        </div>
      </form>
    </div>
  </div>
</template>
