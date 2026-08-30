<script setup lang="ts">
import { backendUrl } from '@/api/http'

const adminUrl = backendUrl('/admin/index.html')
</script>

<template>
  <section class="view-section user-admin-entry">
    <div class="panel user-admin-entry__card">
      <span class="user-admin-entry__icon">👥</span>
      <div>
        <p class="role-workspace__eyebrow">仅系统管理员可见</p>
        <h2>账号与角色管理</h2>
        <p>创建居民、小区管理员、系统管理员和消防员账号，调整角色、启停状态或重置密码。</p>
        <a class="btn-primary user-admin-entry__link" :href="adminUrl" target="_blank" rel="noopener">
          打开用户管理控制台
        </a>
        <small>控制台由后端独立提供，打开后需要再次使用系统管理员账号登录。</small>
      </div>
    </div>
  </section>
</template>
