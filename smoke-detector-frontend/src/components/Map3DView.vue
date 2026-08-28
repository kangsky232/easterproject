<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MapBuilding, MapDevice, MapPositionPayload } from '@/api/types'
import { useDashboardStore } from '@/store/dashboard'
import { conc, fmtFull } from '@/utils/format'

interface Point {
  x: number
  y: number
}

interface BuildingVisual {
  building: MapBuilding
  top: string
  sides: Array<{
    points: string
    tone: 'a' | 'b'
    floorLines: Array<{ a: Point; b: Point }>
  }>
  center: Point
  depth: number
  status: 'ONLINE' | 'OFFLINE' | 'ALARM' | 'EMPTY'
}

interface DeviceVisual {
  device: MapDevice
  point: Point
  labelWidth: number
}

const store = useDashboardStore()
const rotation = ref(-34)
const zoom = ref(1)
const selectedDeviceId = ref<number | null>(null)
const editBuilding = ref('')
const editFloor = ref(1)
const editRoom = ref('101')
const editX = ref(4)
const editZ = ref(4)
const saving = ref(false)

const scene = computed(() => store.mapScene)
const selectedDevice = computed(() =>
  scene.value?.devices.find((device) => device.id === selectedDeviceId.value) ?? null,
)
const selectedBuilding = computed(() =>
  scene.value?.buildings.find((building) => building.buildingCode === editBuilding.value) ?? null,
)
const alarmCount = computed(() => scene.value?.devices.filter((item) => item.status === 'ALARM').length ?? 0)
const onlineCount = computed(() => scene.value?.devices.filter((item) => item.status === 'ONLINE').length ?? 0)
const offlineCount = computed(() => scene.value?.devices.filter((item) => item.status === 'OFFLINE').length ?? 0)

function project(x: number, z: number, height = 0): Point {
  const width = scene.value?.width ?? 100
  const depth = scene.value?.depth ?? 100
  const angle = (rotation.value * Math.PI) / 180
  const dx = x - width / 2
  const dz = z - depth / 2
  const rx = dx * Math.cos(angle) - dz * Math.sin(angle)
  const rz = dx * Math.sin(angle) + dz * Math.cos(angle)
  return { x: 500 + rx * 7, y: 385 + rz * 3.35 - height * 6 }
}

function points(items: Point[]): string {
  return items.map((item) => `${item.x.toFixed(1)},${item.y.toFixed(1)}`).join(' ')
}

function lerp(from: Point, to: Point, ratio: number): Point {
  return {
    x: from.x + (to.x - from.x) * ratio,
    y: from.y + (to.y - from.y) * ratio,
  }
}

const chineseDigits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']

function formatChineseNumber(value: number): string {
  if (!Number.isInteger(value) || value < 0 || value > 99) return String(value)
  if (value < 10) return chineseDigits[value]
  const tens = Math.floor(value / 10)
  const ones = value % 10
  return `${tens === 1 ? '' : chineseDigits[tens]}十${ones === 0 ? '' : chineseDigits[ones]}`
}

function formatBuildingName(building: MapBuilding): string {
  return building.buildingName.replace(/^(\d+)(?=号)/, (number) => formatChineseNumber(Number(number)))
}

const gridLines = computed(() => {
  const lines: Array<{ a: Point; b: Point }> = []
  for (let step = 0; step <= 100; step += 10) {
    lines.push({ a: project(step, 0), b: project(step, 100) })
    lines.push({ a: project(0, step), b: project(100, step) })
  }
  return lines
})

const buildingVisuals = computed<BuildingVisual[]>(() =>
  (scene.value?.buildings ?? []).map((building) => {
    const x = Number(building.positionX)
    const z = Number(building.positionZ)
    const width = Number(building.width)
    const depth = Number(building.depth)
    const height = building.floors * 4.6
    const bottom = [project(x, z), project(x + width, z), project(x + width, z + depth), project(x, z + depth)]
    const top = [project(x, z, height), project(x + width, z, height), project(x + width, z + depth, height), project(x, z + depth, height)]
    const center = project(x + width / 2, z + depth / 2, height)
    const visibleSides = [[0, 1], [1, 2], [2, 3], [3, 0]]
      .map(([from, to], index) => ({
        from,
        to,
        index,
        depth: (bottom[from].y + bottom[to].y) / 2,
      }))
      .sort((a, b) => b.depth - a.depth)
      .slice(0, 2)
    const buildingDevices = (scene.value?.devices ?? []).filter((device) => device.buildingCode === building.buildingCode)
    const status: BuildingVisual['status'] = buildingDevices.some((device) => device.status === 'ALARM')
      ? 'ALARM'
      : buildingDevices.some((device) => device.status === 'ONLINE')
        ? 'ONLINE'
        : buildingDevices.length > 0
          ? 'OFFLINE'
          : 'EMPTY'
    return {
      building,
      top: points(top),
      sides: visibleSides.map((side, sideIndex) => ({
        points: points([bottom[side.from], bottom[side.to], top[side.to], top[side.from]]),
        tone: sideIndex === 0 ? 'a' as const : 'b' as const,
        floorLines: Array.from({ length: Math.max(0, building.floors - 1) }, (_, floorIndex) => {
          const ratio = (floorIndex + 1) / building.floors
          return {
            a: lerp(bottom[side.from], top[side.from], ratio),
            b: lerp(bottom[side.to], top[side.to], ratio),
          }
        }),
      })),
      center,
      depth: project(x + width / 2, z + depth / 2).y,
      status,
    }
  }).sort((a, b) => a.depth - b.depth),
)

const buildingMap = computed(() => new Map((scene.value?.buildings ?? []).map((item) => [item.buildingCode, item])))
const deviceVisuals = computed<DeviceVisual[]>(() =>
  (scene.value?.devices ?? []).flatMap((device) => {
    const building = device.buildingCode ? buildingMap.value.get(device.buildingCode) : null
    if (!building || device.floorNo == null) return []
    const x = Number(building.positionX) + Number(device.positionX ?? 0)
    const z = Number(building.positionZ) + Number(device.positionZ ?? 0)
    const label = device.roomLabel || `第${formatChineseNumber(device.floorNo)}层`
    return [{
      device,
      point: project(x, z, device.floorNo * 4.6 + 1.5),
      labelWidth: Math.max(42, label.length * 10 + 18),
    }]
  }),
)

function chooseDevice(device: MapDevice): void {
  selectedDeviceId.value = device.id
  store.selectDevice(device.id)
}

function rotate(delta: number): void {
  rotation.value = (rotation.value + delta + 360) % 360
}

function changeZoom(delta: number): void {
  zoom.value = Math.min(1.35, Math.max(0.75, Number((zoom.value + delta).toFixed(2))))
}

async function savePosition(): Promise<void> {
  const device = selectedDevice.value
  if (!device || saving.value) return
  const payload: MapPositionPayload = {
    buildingCode: editBuilding.value,
    floorNo: editFloor.value,
    roomLabel: editRoom.value.trim(),
    positionX: editX.value,
    positionZ: editZ.value,
  }
  saving.value = true
  try {
    await store.saveMapPosition(device.id, payload)
  } finally {
    saving.value = false
  }
}

watch(
  () => scene.value?.devices,
  (devices) => {
    if (!devices?.length) {
      selectedDeviceId.value = null
      return
    }
    if (!devices.some((device) => device.id === selectedDeviceId.value)) {
      selectedDeviceId.value = devices.find((device) => device.status === 'ALARM')?.id ?? devices[0].id
    }
  },
  { immediate: true },
)

watch(selectedDevice, (device) => {
  if (!device) return
  editBuilding.value = device.buildingCode ?? scene.value?.buildings[0]?.buildingCode ?? ''
  editFloor.value = device.floorNo ?? 1
  editRoom.value = device.roomLabel ?? '101'
  editX.value = Number(device.positionX ?? 4)
  editZ.value = Number(device.positionZ ?? 4)
}, { immediate: true })
</script>

<template>
  <section class="map3d-view view-section">
    <div class="map3d-head">
      <div>
        <span class="role-workspace__eyebrow">SIMULATED DIGITAL TWIN</span>
        <h2>{{ scene?.sceneName ?? '模拟 3D 社区地图' }}</h2>
        <p>楼栋、楼层、房间和设备坐标来自后端数据库，颜色随设备与告警状态实时变化。</p>
      </div>
      <div class="map3d-summary">
        <span class="is-online">在线 {{ onlineCount }}</span>
        <span class="is-alarm">告警 {{ alarmCount }}</span>
        <span class="is-offline">离线 {{ offlineCount }}</span>
      </div>
    </div>

    <div class="map3d-layout">
      <div class="map3d-canvas panel">
        <div class="map3d-tools">
          <button type="button" @click="rotate(-15)">↶ 旋转</button>
          <button type="button" @click="rotate(15)">旋转 ↷</button>
          <button type="button" @click="changeZoom(0.1)">＋ 放大</button>
          <button type="button" @click="changeZoom(-0.1)">－ 缩小</button>
          <span>{{ rotation }}° · {{ Math.round(zoom * 100) }}%</span>
        </div>

        <div v-if="!scene" class="map3d-empty">地图数据加载中…</div>
        <svg v-else viewBox="0 0 1000 620" role="img" aria-label="智慧社区模拟三维地图">
          <g class="map3d-world" :style="{ transform: `scale(${zoom})`, transformOrigin: '500px 330px' }">
            <polygon class="map3d-ground" :points="points([project(0, 0), project(100, 0), project(100, 100), project(0, 100)])" />
            <line
              v-for="(line, index) in gridLines"
              :key="`grid-${index}`"
              class="map3d-gridline"
              :x1="line.a.x" :y1="line.a.y" :x2="line.b.x" :y2="line.b.y"
            />

            <g
              v-for="visual in buildingVisuals"
              :key="visual.building.buildingCode"
              class="map3d-building"
              :class="`map3d-building--${visual.status.toLowerCase()}`"
            >
              <g v-for="(side, sideIndex) in visual.sides" :key="`${visual.building.buildingCode}-side-${sideIndex}`">
                <polygon :class="`map3d-building__side map3d-building__side--${side.tone}`" :points="side.points" />
                <line
                  v-for="(floorLine, floorIndex) in side.floorLines"
                  :key="`${visual.building.buildingCode}-floor-${sideIndex}-${floorIndex}`"
                  class="map3d-building__floor-line"
                  :x1="floorLine.a.x" :y1="floorLine.a.y" :x2="floorLine.b.x" :y2="floorLine.b.y"
                />
              </g>
              <polygon class="map3d-building__top" :points="visual.top" />
              <g class="map3d-building__label" :transform="`translate(${visual.center.x} ${visual.center.y - 18})`">
                <rect x="-66" y="-31" width="132" height="38" rx="8" />
                <text class="map3d-building__name" x="0" y="-16" text-anchor="middle">{{ formatBuildingName(visual.building) }}</text>
                <text class="map3d-building__meta" x="0" y="-3" text-anchor="middle">共{{ formatChineseNumber(visual.building.floors) }}层</text>
              </g>
            </g>

            <g
              v-for="visual in deviceVisuals"
              :key="visual.device.id"
              class="map3d-device"
              :class="[`map3d-device--${visual.device.status.toLowerCase()}`, { selected: selectedDeviceId === visual.device.id }]"
              tabindex="0"
              role="button"
              :aria-label="`${visual.device.deviceName || visual.device.deviceId}，${visual.device.status}`"
              @click="chooseDevice(visual.device)"
              @keydown.enter="chooseDevice(visual.device)"
            >
              <line :x1="visual.point.x" :y1="visual.point.y" :x2="visual.point.x" :y2="visual.point.y + 20" />
              <circle :cx="visual.point.x" :cy="visual.point.y" r="9" />
              <circle class="map3d-device__pulse" :cx="visual.point.x" :cy="visual.point.y" r="15" />
              <g class="map3d-device__label" :transform="`translate(${visual.point.x + 13} ${visual.point.y - 24})`">
                <rect x="0" y="0" :width="visual.labelWidth" height="22" rx="6" />
                <text x="9" y="15">{{ visual.device.roomLabel || `第${formatChineseNumber(visual.device.floorNo ?? 0)}层` }}</text>
              </g>
            </g>
          </g>
        </svg>

        <div class="map3d-legend">
          <span><i class="legend-dot legend-dot--online"></i>在线</span>
          <span><i class="legend-dot legend-dot--alarm"></i>告警</span>
          <span><i class="legend-dot legend-dot--offline"></i>离线</span>
        </div>
      </div>

      <aside class="map3d-detail panel">
        <template v-if="selectedDevice">
          <div class="map3d-detail__head">
            <div>
              <span class="role-workspace__eyebrow">设备空间详情</span>
              <h3>{{ selectedDevice.deviceName || selectedDevice.deviceId }}</h3>
            </div>
            <span class="map3d-state" :class="`map3d-state--${selectedDevice.status.toLowerCase()}`">
              {{ selectedDevice.status === 'ONLINE' ? '在线' : selectedDevice.status === 'ALARM' ? '告警' : '离线' }}
            </span>
          </div>
          <dl class="map3d-location">
            <div><dt>设备编号</dt><dd>{{ selectedDevice.deviceId }}</dd></div>
            <div><dt>空间位置</dt><dd>{{ selectedDevice.buildingName }} 第{{ formatChineseNumber(selectedDevice.floorNo ?? 0) }}层 {{ selectedDevice.roomLabel }}</dd></div>
            <div><dt>安装说明</dt><dd>{{ selectedDevice.location || '—' }}</dd></div>
            <div><dt>最新数据</dt><dd>{{ fmtFull(selectedDevice.latestTimestamp) }}</dd></div>
          </dl>
          <div class="map3d-metrics">
            <span><b>{{ conc(selectedDevice.smoke) }}</b> ppm<small>烟雾</small></span>
            <span><b>{{ conc(selectedDevice.temperature) }}</b> ℃<small>温度</small></span>
            <span><b>{{ conc(selectedDevice.coValue) }}</b> ppm<small>CO</small></span>
            <span><b>{{ selectedDevice.battery ?? '—' }}</b> %<small>电量</small></span>
          </div>

          <form v-if="store.canManageMapPositions" class="map3d-form" @submit.prevent="savePosition">
            <h4>调整数据库位置</h4>
            <label>楼栋
              <select v-model="editBuilding">
                <option v-for="building in scene?.buildings" :key="building.buildingCode" :value="building.buildingCode">
                  {{ building.buildingName }}
                </option>
              </select>
            </label>
            <div class="map3d-form__row">
              <label>楼层<input v-model.number="editFloor" type="number" min="1" :max="selectedBuilding?.floors ?? 1" /></label>
              <label>房间<input v-model="editRoom" maxlength="64" required /></label>
            </div>
            <div class="map3d-form__row">
              <label>楼内 X<input v-model.number="editX" type="number" min="0" :max="Number(selectedBuilding?.width ?? 0)" step="0.1" /></label>
              <label>楼内 Z<input v-model.number="editZ" type="number" min="0" :max="Number(selectedBuilding?.depth ?? 0)" step="0.1" /></label>
            </div>
            <button class="btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存地图位置' }}</button>
          </form>
          <p v-else class="map3d-readonly">当前账号为只读地图权限，只有小区管理员和系统管理员可调整位置。</p>
        </template>
        <div v-else class="map3d-empty">暂无可定位设备</div>
      </aside>
    </div>
  </section>
</template>
