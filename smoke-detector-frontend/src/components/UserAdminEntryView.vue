<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import * as api from '@/api'
import type { UserAccount } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'

type RoleCode = UserAccount['role']
type EditorMode = 'create' | 'edit' | null

const store = useDashboardStore()
const users = ref<UserAccount[]>([])
const loading = ref(false)
const loadError = ref('')
const editorMode = ref<EditorMode>(null)
const editingId = ref<number | null>(null)
const passwordUser = ref<UserAccount | null>(null)
const saving = ref(false)

const filters = reactive({ keyword: '', role: '', enabled: '' as '' | '0' | '1' })
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'RESIDENT' as RoleCode,
  phone: '',
})
const passwordForm = reactive({ password: '' })

const roleOptions: { value: RoleCode; label: string }[] = [
  { value: 'RESIDENT', label: '居民' },
  { value: 'COMMUNITY_ADMIN', label: '社区管理员' },
  { value: 'SYSTEM_ADMIN', label: '系统管理员' },
  { value: 'FIREFIGHTER', label: '消防人员' },
]

const editorTitle = computed(() => (editorMode.value === 'create' ? '创建账号' : '编辑账号'))
const currentUsername = computed(() => String(store.currentUser?.username || ''))

function roleLabel(role: RoleCode): string {
  return roleOptions.find((item) => item.value === role)?.label ?? role
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

async function loadUsers(): Promise<void> {
  if (!store.canManageUsers || !store.token) return
  loading.value = true
  loadError.value = ''
  try {
    const page = await api.fetchUsers(filters)
    users.value = page.records || []
  } catch (error) {
    loadError.value = (error as Error).message
  } finally {
    loading.value = false
  }
}

function resetForm(): void {
  form.username = ''
  form.password = ''
  form.displayName = ''
  form.role = 'RESIDENT'
  form.phone = ''
}

function openCreate(): void {
  resetForm()
  editingId.value = null
  editorMode.value = 'create'
}

function openEdit(user: UserAccount): void {
  editingId.value = user.id
  form.username = user.username
  form.password = ''
  form.displayName = user.displayName
  form.role = user.role
  form.phone = user.phone || ''
  editorMode.value = 'edit'
}

function closeEditor(): void {
  if (saving.value) return
  editorMode.value = null
  editingId.value = null
}

async function saveUser(): Promise<void> {
  if (!form.displayName.trim()) {
    store.showToast('请填写姓名。', 'error')
    return
  }
  if (editorMode.value === 'create' && (!form.username.trim() || form.password.length < 8)) {
    store.showToast('请填写用户名，初始密码至少需要 8 位。', 'error')
    return
  }
  saving.value = true
  try {
    const common = {
      displayName: form.displayName.trim(),
      role: form.role,
      phone: form.phone.trim(),
    }
    if (editorMode.value === 'create') {
      await api.createUser({
        ...common,
        username: form.username.trim(),
        password: form.password,
      })
      store.showToast(`账号 ${form.username.trim()} 已创建。`, 'success')
    } else if (editingId.value !== null) {
      await api.updateUser(editingId.value, common)
      store.showToast(`账号 ${form.username} 已更新。`, 'success')
    }
    editorMode.value = null
    await loadUsers()
  } catch (error) {
    store.showToast(`保存账号失败：${(error as Error).message}`, 'error')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(user: UserAccount): Promise<void> {
  const action = user.enabled ? '禁用' : '启用'
  const ok = await store.confirm(`确定${action}账号 ${user.username} 吗？`, `${action}账号`)
  if (!ok) return
  try {
    await api.updateUserStatus(user.id, !user.enabled)
    store.showToast(`账号 ${user.username} 已${action}。`, 'success')
    await loadUsers()
  } catch (error) {
    store.showToast(`${action}账号失败：${(error as Error).message}`, 'error')
  }
}

async function removeUser(user: UserAccount): Promise<void> {
  const ok = await store.confirm(`确定删除账号 ${user.username} 吗？此操作不可撤销。`, '删除账号')
  if (!ok) return
  try {
    await api.deleteUser(user.id)
    store.showToast(`账号 ${user.username} 已删除。`, 'success')
    await loadUsers()
  } catch (error) {
    store.showToast(`删除账号失败：${(error as Error).message}`, 'error')
  }
}

function openPassword(user: UserAccount): void {
  passwordUser.value = user
  passwordForm.password = ''
}

function closePassword(): void {
  if (saving.value) return
  passwordUser.value = null
  passwordForm.password = ''
}

async function resetPassword(): Promise<void> {
  if (!passwordUser.value || passwordForm.password.length < 8) {
    store.showToast('新密码至少需要 8 位。', 'error')
    return
  }
  saving.value = true
  try {
    await api.resetUserPassword(passwordUser.value.id, passwordForm.password)
    store.showToast(`账号 ${passwordUser.value.username} 的密码已重置。`, 'success')
    passwordUser.value = null
    passwordForm.password = ''
  } catch (error) {
    store.showToast(`重置密码失败：${(error as Error).message}`, 'error')
  } finally {
    saving.value = false
  }
}

watch(
  () => [store.canManageUsers, store.token],
  ([allowed, token]) => {
    if (allowed && token) void loadUsers()
    if (!token) users.value = []
  },
)

onMounted(() => {
  if (store.canManageUsers && store.token) void loadUsers()
})
</script>

<template>
  <section class="view-section user-admin">
    <template v-if="store.canManageUsers">
      <div class="section-head user-admin__head">
        <div>
          <h2>账号与角色管理</h2>
          <p>直接使用当前登录状态管理居民、社区管理员、系统管理员和消防人员账号。</p>
        </div>
        <button type="button" class="btn-primary" @click="openCreate">＋ 创建账号</button>
      </div>

      <form class="user-admin__filters panel" @submit.prevent="loadUsers">
        <label>
          搜索账号
          <input v-model="filters.keyword" type="search" placeholder="用户名、姓名或手机号" />
        </label>
        <label>
          角色
          <select v-model="filters.role">
            <option value="">全部角色</option>
            <option v-for="role in roleOptions" :key="role.value" :value="role.value">{{ role.label }}</option>
          </select>
        </label>
        <label>
          状态
          <select v-model="filters.enabled">
            <option value="">全部状态</option>
            <option value="1">已启用</option>
            <option value="0">已禁用</option>
          </select>
        </label>
        <button type="submit" class="btn-primary" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
      </form>

      <p v-if="loadError" class="capability-note">用户列表加载失败：{{ loadError }}</p>
      <div class="table-wrap">
        <table class="device-table user-admin__table">
          <thead>
            <tr>
              <th>用户名</th>
              <th>姓名</th>
              <th>角色</th>
              <th>手机号</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && users.length === 0"><td colspan="7" class="user-admin__empty">正在加载账号…</td></tr>
            <tr v-else-if="users.length === 0"><td colspan="7" class="user-admin__empty">没有符合条件的账号</td></tr>
            <tr v-for="user in users" :key="user.id">
              <td><strong>{{ user.username }}</strong><small v-if="user.username === currentUsername">当前账号</small></td>
              <td>{{ user.displayName }}</td>
              <td><span class="user-admin__role">{{ roleLabel(user.role) }}</span></td>
              <td>{{ user.phone || '—' }}</td>
              <td :class="user.enabled ? 'user-admin__enabled' : 'user-admin__disabled'">{{ user.enabled ? '已启用' : '已禁用' }}</td>
              <td>{{ formatTime(user.createdAt) }}</td>
              <td class="broadcast-actions">
                <button type="button" class="btn-mini" @click="openEdit(user)">编辑</button>
                <button type="button" class="btn-mini" :disabled="user.username === currentUsername" @click="openPassword(user)">重置密码</button>
                <button type="button" class="btn-mini" :disabled="user.username === currentUsername" @click="toggleStatus(user)">{{ user.enabled ? '禁用' : '启用' }}</button>
                <button type="button" class="btn-mini btn-mini-danger" :disabled="user.username === currentUsername" @click="removeUser(user)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <div v-else class="panel user-admin__forbidden">
      <span>🔒</span>
      <h2>无权访问用户管理</h2>
      <p>该功能仅对系统管理员开放。</p>
    </div>

    <div v-if="editorMode" class="modal" role="dialog" aria-modal="true" :aria-label="editorTitle">
      <div class="modal-card">
        <div class="modal-head">
          <h3>{{ editorTitle }}</h3>
          <button type="button" class="modal-close" aria-label="关闭" @click="closeEditor">×</button>
        </div>
        <form class="modal-form" @submit.prevent="saveUser">
          <label>用户名<input v-model="form.username" :disabled="editorMode === 'edit'" maxlength="64" required /></label>
          <label v-if="editorMode === 'create'">初始密码<input v-model="form.password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required /></label>
          <label>姓名<input v-model="form.displayName" maxlength="64" required /></label>
          <label>
            角色
            <select v-model="form.role" :disabled="form.username === currentUsername">
              <option v-for="role in roleOptions" :key="role.value" :value="role.value">{{ role.label }}</option>
            </select>
          </label>
          <label>手机号<input v-model="form.phone" maxlength="20" /></label>
          <p v-if="editorMode === 'edit' && form.username === currentUsername" class="field-help">当前账号不能修改自身角色。</p>
          <div class="modal-actions">
            <button type="button" class="btn-ghost" @click="closeEditor">取消</button>
            <button type="submit" class="btn-primary" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
          </div>
        </form>
      </div>
    </div>

    <div v-if="passwordUser" class="modal" role="dialog" aria-modal="true" aria-label="重置密码">
      <div class="modal-card">
        <div class="modal-head">
          <h3>重置 {{ passwordUser.username }} 的密码</h3>
          <button type="button" class="modal-close" aria-label="关闭" @click="closePassword">×</button>
        </div>
        <form class="modal-form" @submit.prevent="resetPassword">
          <label>新密码<input v-model="passwordForm.password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required /></label>
          <p class="field-help">密码重置后，该账号现有登录状态会立即失效。</p>
          <div class="modal-actions">
            <button type="button" class="btn-ghost" @click="closePassword">取消</button>
            <button type="submit" class="btn-primary" :disabled="saving">{{ saving ? '重置中…' : '确认重置' }}</button>
          </div>
        </form>
      </div>
    </div>
  </section>
</template>
