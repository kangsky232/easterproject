<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/store/dashboard'

const store = useDashboardStore()

const title = computed(() => store.workspace?.homeTitle ?? '智慧消防工作台')
const description = computed(() => store.workspace?.description ?? '正在加载当前账号的工作范围…')
</script>

<template>
  <section class="role-workspace" aria-label="当前工作台信息">
    <span class="role-workspace__eyebrow">{{ store.workspace?.roleLabel ?? '当前账号' }}专属界面</span>
    <span class="role-workspace__sep" aria-hidden="true">·</span>
    <span class="role-workspace__title">{{ title }}</span>
    <span class="role-workspace__sep" aria-hidden="true">·</span>
    <span class="role-workspace__desc">{{ description }}</span>
  </section>
</template>
