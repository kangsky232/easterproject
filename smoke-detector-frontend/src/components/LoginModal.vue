<script setup lang="ts">
import { computed, ref } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import AppIcon from '@/components/AppIcon.vue'

const store = useDashboardStore()

const username = ref('')
const password = ref('')

const submitting = computed(() => store.loginMessage === '正在登录…')

async function onSubmit(): Promise<void> {
  if (submitting.value) return
  await store.login(username.value.trim(), password.value)
}
</script>

<template>
  <div v-if="store.needsLogin" class="modal" role="dialog" aria-modal="true" aria-label="登录管理平台">
    <div class="modal-card">
      <div class="modal-head">
        <h3>登录管理平台</h3>
        <button type="button" class="modal-close" aria-label="关闭" @click="store.needsLogin = false">
          <AppIcon name="close" :size="18" />
        </button>
      </div>
      <form class="modal-form" @submit.prevent="onSubmit">
        <label>用户名<input v-model="username" autocomplete="username" required /></label>
        <label>密码<input v-model="password" type="password" autocomplete="current-password" required /></label>
        <p class="login-message" aria-live="polite">{{ store.loginMessage }}</p>
        <div class="modal-actions">
          <button type="submit" class="btn-primary" :disabled="submitting">{{ submitting ? '登录中…' : '登录' }}</button>
        </div>
      </form>
    </div>
  </div>
</template>
