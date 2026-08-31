<script setup lang="ts">
import { computed } from 'vue'
import { useDashboardStore } from '@/store/dashboard'
import { ROLE_LABEL, type ModuleKey } from '@/constants'
import AppIcon from '@/components/AppIcon.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import bgHome from '@/assets/bg-home.png'

const emit = defineEmits<{
  (e: 'navigate', view: string): void
  (e: 'open-login'): void
}>()

const store = useDashboardStore()

const MENU: { key: ModuleKey; label: string }[] = [
  { key: 'monitor', label: '📊 数据总览' },
  { key: 'map', label: '🏙️ 3D地图' },
  { key: 'devices', label: '🔧 设备管理' },
  { key: 'notifications', label: '🔔 通知记录' },
  { key: 'broadcasts', label: '📣 广播管理' },
  { key: 'users', label: '👥 用户管理' },
]

const menu = computed(() => MENU.filter((item) => store.canViewModule(item.key)))

const userName = computed(() => store.currentUser?.displayName || store.currentUser?.username || '未登录')
const roleLabel = computed(() => ROLE_LABEL[store.currentUser?.role ?? ''] ?? '')

const FEATURES = [
  { icon: 'flame', title: '智能感知', subtitle: '高精度烟雾传感器，快速响应', desc: '实时监测环境变化' },
  { icon: 'alert', title: '实时告警', subtitle: '异常情况立即推送，多端通知', desc: '随时随地掌握安全动态' },
  { icon: 'chart', title: '数据可视化', subtitle: '历史数据统计分析，趋势预测', desc: '助力科学决策与管理' },
  { icon: 'shield', title: '稳定可靠', subtitle: '工业级设计，稳定运行', desc: '适应各种复杂环境' },
]
</script>

<template>
  <div class="home">
    <header class="home-topbar">
      <div class="brand">
        <span class="logo" aria-hidden="true"><AppIcon name="flame" :size="30" /></span>
        <div class="brand-text">
          <h1>智慧烟感预警系统</h1>
          <p class="subtitle">Smart Smoke Detector · 实时监控</p>
        </div>
      </div>

      <nav class="home-nav" aria-label="功能导航">
        <button
          v-for="item in menu"
          :key="item.key"
          type="button"
          class="home-nav__link"
          @click="emit('navigate', item.key)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="home-account">
        <ThemeToggle />
        <button v-if="!store.currentUser" type="button" class="btn-primary" @click="emit('open-login')">
          <AppIcon name="user" :size="14" /> 登录
        </button>
        <div v-else class="user-chip">
          <span class="user-avatar" aria-hidden="true"><AppIcon name="user" :size="15" /></span>
          <div class="user-meta">
            <span class="user-name">{{ userName }}</span>
            <span v-if="roleLabel" class="user-role">{{ roleLabel }}</span>
          </div>
          <button class="logout-btn" title="退出登录" aria-label="退出登录" @click="store.logout()">
            <AppIcon name="logout" :size="15" />
          </button>
        </div>
      </div>
    </header>

    <main class="home-body" :style="{ backgroundImage: `url(${bgHome})` }">
      <div class="home-body__intro">
        <section class="hero-intro">
          <p class="hero-eyebrow">SMART SMOKE DETECTOR</p>
          <h2>智慧烟感预警系统</h2>
          <p class="hero-desc">
            面向社区与园区的消防安全监测平台，实时采集烟雾浓度、环境温湿度、电气电流与一氧化碳等数据，
            多传感器告警研判、3D 社区态势与钉钉广播联动，7×24 小时守护每一户安全。
          </p>
          <div class="hero-features">
            <span>实时监测</span>
            <span>多传感器</span>
            <span>3D 态势</span>
            <span>告警联动</span>
          </div>
          <button type="button" class="hero-cta" @click="emit('navigate', 'monitor')">
            进入监控大屏 →
          </button>
        </section>
      </div>

      <section class="feature-panel" aria-label="系统特性">
        <div v-for="feature in FEATURES" :key="feature.title" class="feature-card">
          <span class="feature-icon" aria-hidden="true"><AppIcon :name="feature.icon" :size="24" /></span>
          <h3>{{ feature.title }}</h3>
          <p class="feature-subtitle">{{ feature.subtitle }}</p>
          <p class="feature-desc">{{ feature.desc }}</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.home {
  height: calc(100vh - 44px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.home-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--border);
  flex: none;
}

.home-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.home-nav__link {
  background: none;
  border: 1px solid transparent;
  color: var(--ink-2);
  font-size: 14px;
  padding: 8px 13px;
  border-radius: 8px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
}
.home-nav__link:hover {
  color: var(--ink-1);
  border-color: var(--border);
  background: rgba(15, 42, 82, 0.04);
}

.home-account { flex: none; display: inline-flex; align-items: center; gap: 10px; }

/* 内容区：铺满背景图，上方为介绍文案（靠左），下方为特性白底卡片区 */
.home-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 28px clamp(24px, 7vw, 110px) 34px;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.home-body__intro {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

/* 项目介绍：左侧半透明卡片，保证在背景图上清晰可读 */
.hero-intro {
  text-align: left;
  max-width: 620px;
  padding: 30px 34px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: var(--radius);
  box-shadow: 0 18px 44px rgba(15, 43, 82, 0.18);
}

.hero-eyebrow {
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  margin-bottom: 12px;
}

.hero-intro h2 {
  font-size: 30px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.hero-desc {
  color: var(--ink-2);
  font-size: 15px;
  line-height: 1.8;
  max-width: 560px;
  margin: 0 0 20px;
}

.hero-features {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-start;
  margin-bottom: 24px;
}
.hero-features span {
  padding: 6px 12px;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--ink-2);
  font-size: 12px;
  background: rgba(15, 42, 82, 0.04);
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--accent);
  border: none;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  padding: 11px 20px;
  border-radius: 9px;
  cursor: pointer;
  transition: filter 0.15s, transform 0.15s;
}
.hero-cta:hover { filter: brightness(1.12); transform: translateX(2px); }

/* 特性介绍：底部白色圆角区域，四列小图标卡片 */
.feature-panel {
  flex: none;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  background: #ffffff;
  border: 1px solid rgba(15, 42, 82, 0.06);
  border-radius: var(--radius);
  padding: 24px 26px;
  box-shadow: 0 16px 40px rgba(15, 43, 82, 0.14);
}

.feature-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 5px;
}

.feature-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  color: var(--accent);
  background: rgba(37, 99, 235, 0.12);
  margin-bottom: 6px;
}

.feature-card h3 {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-1);
}

.feature-subtitle {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-1);
}

.feature-desc {
  font-size: 12px;
  color: var(--ink-3);
}

/* 黑夜模式：介绍卡片 / 特性区域改为深色半透明，保持可读 */
html.dark .hero-intro {
  background: rgba(17, 21, 28, 0.82);
  border-color: rgba(255, 255, 255, 0.12);
}
html.dark .feature-panel {
  background: rgba(17, 21, 28, 0.96);
  border-color: rgba(255, 255, 255, 0.12);
}
html.dark .feature-icon {
  background: rgba(76, 141, 255, 0.14);
}

@media (max-width: 1160px) {
  .home-topbar { flex-direction: column; align-items: flex-start; gap: 14px; }
  .home-body { padding: 28px 20px; }
  .hero-intro h2 { font-size: 26px; }
}
@media (max-width: 900px) {
  .feature-panel { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .feature-panel { grid-template-columns: 1fr; }
}
</style>
