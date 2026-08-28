<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/store/dashboard'

const store = useDashboardStore()

const title = computed(() => store.workspace?.homeTitle ?? '智慧消防工作台')
const description = computed(() => store.workspace?.description ?? '正在加载当前账号的工作范围…')
const permissionLabels: Record<string, string> = {
  READ_ONLY: '只读查看',
  ALERT_HANDLE: '告警处置',
  BROADCAST_SEND: '广播下发',
  BROADCAST_DELETE: '广播管理',
  DEVICE_MANAGE: '设备管理',
  MAP_POSITION_MANAGE: '地图配置',
  USER_MANAGE: '账号管理',
}
</script>

<template>
  <section class="role-workspace" :class="`role-workspace--${store.userRole.toLowerCase() || 'guest'}`">
    <div>
      <span class="role-workspace__eyebrow">{{ store.workspace?.roleLabel ?? '当前账号' }}专属界面</span>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div class="role-workspace__permissions" aria-label="当前权限">
      <span v-for="permission in store.workspace?.permissions ?? []" :key="permission">
        {{ permissionLabels[permission] ?? permission }}
      </span>
    </div>
  </section>
</template>
