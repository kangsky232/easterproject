<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { MapBuilding, MapDevice, MapPositionPayload } from '@/api/types'
import basementUtilityCorridorImage from '@/assets/corridor-cameras/basement-utility-corridor.jpg'
import bicycleStorageCorridorImage from '@/assets/corridor-cameras/bicycle-storage-corridor.jpg'
import brickStairLandingImage from '@/assets/corridor-cameras/brick-stair-landing.jpg'
import communityClubCorridorImage from '@/assets/corridor-cameras/community-club-corridor.jpg'
import electricalSmokeWarningImage from '@/assets/corridor-cameras/electrical-smoke-warning.jpg'
import elevatorLobbyImage from '@/assets/corridor-cameras/elevator-lobby.jpg'
import emergencyExitPassageImage from '@/assets/corridor-cameras/emergency-exit-passage.jpg'
import entranceCorridorImage from '@/assets/corridor-cameras/entrance-corridor.jpg'
import fireStairwellImage from '@/assets/corridor-cameras/fire-stairwell.jpg'
import laundryLandingImage from '@/assets/corridor-cameras/laundry-landing.jpg'
import modernCorridorImage from '@/assets/corridor-cameras/modern-corridor.jpg'
import nightCorridorImage from '@/assets/corridor-cameras/night-corridor.jpg'
import oldCommunityCorridorImage from '@/assets/corridor-cameras/old-community-corridor.jpg'
import parkingConnectorImage from '@/assets/corridor-cameras/parking-connector.jpg'
import rainNightCorridorImage from '@/assets/corridor-cameras/rain-night-corridor.jpg'
import renovatedCorridorImage from '@/assets/corridor-cameras/renovated-corridor.jpg'
import rentalHallwayImage from '@/assets/corridor-cameras/rental-hallway.jpg'
import rooftopAccessImage from '@/assets/corridor-cameras/rooftop-access.jpg'
import servicePipeCorridorImage from '@/assets/corridor-cameras/service-pipe-corridor.jpg'
import smokeWarningCorridorImage from '@/assets/corridor-cameras/smoke-warning-corridor.jpg'
import sunlitCorridorImage from '@/assets/corridor-cameras/sunlit-corridor.jpg'
import { useClock } from '@/composables/useClock'
import { useDashboardStore } from '@/store/dashboard'
import { conc, fmtFull } from '@/utils/format'

interface Point {
  x: number
  y: number
}

type SceneStatus = 'ONLINE' | 'OFFLINE' | 'ALARM' | 'EMPTY'
type CameraKey = 'corridor' | 'stairwell' | 'warning'

interface FloorVisual {
  floorNo: number
  points: string
  center: Point
  status: SceneStatus
}

interface BuildingVisual {
  building: MapBuilding
  top: string
  sides: Array<{
    points: string
    tone: 'a' | 'b'
    floorLines: Array<{ a: Point; b: Point }>
    floorAreas: FloorVisual[]
  }>
  center: Point
  depth: number
  status: SceneStatus
}

interface DeviceVisual {
  device: MapDevice
  point: Point
  labelWidth: number
}

interface FloorSummary {
  floorNo: number
  devices: MapDevice[]
  status: SceneStatus
}

interface CameraFeed {
  key: CameraKey
  label: string
  code: string
  image: string
}

interface CameraVariant {
  label: string
  image: string
}

const FLOOR_HEIGHT = 4.6
const BUILDING_SIDES: ReadonlyArray<readonly [number, number]> = [[0, 1], [1, 2], [2, 3], [3, 0]]

// 当前社区共 19 层，常规机位一层一张独立图片；第二机位使用错位索引，
// 因此同一楼层的两个常规机位也不会展示同一张图。
const floorCameraVariants: readonly CameraVariant[] = [
  { label: '首层入户门厅', image: entranceCorridorImage },
  { label: '现代住宅过道', image: modernCorridorImage },
  { label: '午后阳光过道', image: sunlitCorridorImage },
  { label: '电梯厅', image: elevatorLobbyImage },
  { label: '新装住宅过道', image: renovatedCorridorImage },
  { label: '屋面入口', image: rooftopAccessImage },
  { label: '老旧出租屋过道', image: rentalHallwayImage },
  { label: '老楼梯间', image: brickStairLandingImage },
  { label: '晾晒平台', image: laundryLandingImage },
  { label: '雨夜走廊', image: rainNightCorridorImage },
  { label: '老楼公共过道', image: oldCommunityCorridorImage },
  { label: '消防楼梯', image: fireStairwellImage },
  { label: '公共活动区走廊', image: communityClubCorridorImage },
  { label: '自行车库连廊', image: bicycleStorageCorridorImage },
  { label: '夜间住宅过道', image: nightCorridorImage },
  { label: '消防管线走廊', image: servicePipeCorridorImage },
  { label: '地下车库连廊', image: parkingConnectorImage },
  { label: '地下设备连廊', image: basementUtilityCorridorImage },
  { label: '疏散出口通道', image: emergencyExitPassageImage },
]

const warningCameraVariants: readonly CameraVariant[] = [
  { label: '烟雾预警演示', image: smokeWarningCorridorImage },
  { label: '电气烟雾预警', image: electricalSmokeWarningImage },
]

const store = useDashboardStore()
const now = useClock()
const rotation = ref(-34)
const zoom = ref(1)
const inspectedBuildingCode = ref('')
const inspectedFloorNo = ref(1)
const selectedCameraKey = ref<CameraKey>('corridor')
const selectedDeviceId = ref<number | null>(null)
const editBuilding = ref('')
const editFloor = ref(1)
const editRoom = ref('101')
const editX = ref(4)
const editZ = ref(4)
const saving = ref(false)
let inspectionInitialized = false

const scene = computed(() => store.mapScene)
const selectedDevice = computed(() =>
  scene.value?.devices.find((device) => device.id === selectedDeviceId.value) ?? null,
)
const inspectedBuilding = computed(() =>
  scene.value?.buildings.find((building) => building.buildingCode === inspectedBuildingCode.value) ?? null,
)
const editSelectedBuilding = computed(() =>
  scene.value?.buildings.find((building) => building.buildingCode === editBuilding.value) ?? null,
)
const alarmCount = computed(() => scene.value?.devices.filter((item) => item.status === 'ALARM').length ?? 0)
const onlineCount = computed(() => scene.value?.devices.filter((item) => item.status === 'ONLINE').length ?? 0)
const offlineCount = computed(() => scene.value?.devices.filter((item) => item.status === 'OFFLINE').length ?? 0)

function resolveStatus(devices: MapDevice[]): SceneStatus {
  if (devices.some((device) => device.status === 'ALARM')) return 'ALARM'
  if (devices.some((device) => device.status === 'ONLINE')) return 'ONLINE'
  if (devices.length > 0) return 'OFFLINE'
  return 'EMPTY'
}

function statusLabel(status: SceneStatus): string {
  if (status === 'ALARM') return '告警'
  if (status === 'ONLINE') return '在线'
  if (status === 'OFFLINE') return '离线'
  return '暂无设备'
}

const inspectedFloorSummaries = computed<FloorSummary[]>(() => {
  const building = inspectedBuilding.value
  if (!building) return []
  const buildingDevices = (scene.value?.devices ?? []).filter(
    (device) => device.buildingCode === building.buildingCode,
  )
  return Array.from({ length: building.floors }, (_, index) => {
    const floorNo = building.floors - index
    const devices = buildingDevices.filter((device) => device.floorNo === floorNo)
    return { floorNo, devices, status: resolveStatus(devices) }
  })
})

const floorDevices = computed(
  () => inspectedFloorSummaries.value.find((floor) => floor.floorNo === inspectedFloorNo.value)?.devices ?? [],
)
const selectedFloorStatus = computed<SceneStatus>(
  () => inspectedFloorSummaries.value.find((floor) => floor.floorNo === inspectedFloorNo.value)?.status ?? 'EMPTY',
)
const selectedFloorDevice = computed(
  () => floorDevices.value.find((device) => device.id === selectedDeviceId.value) ?? null,
)

function cameraLocationSeed(buildingCode: string, floorNo: number): number {
  const locationKey = (buildingCode || 'COMMUNITY') + ':' + floorNo
  let hash = 2166136261
  for (let index = 0; index < locationKey.length; index += 1) {
    hash ^= locationKey.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

function floorCameraIndex(buildingCode: string, floorNo: number): number {
  const buildings = [...(scene.value?.buildings ?? [])]
    .sort((first, second) => first.buildingCode.localeCompare(second.buildingCode))
  let offset = 0
  for (const building of buildings) {
    if (building.buildingCode === buildingCode) {
      return (offset + Math.max(0, floorNo - 1)) % floorCameraVariants.length
    }
    offset += building.floors
  }
  return cameraLocationSeed(buildingCode, floorNo) % floorCameraVariants.length
}

const cameraFeeds = computed<CameraFeed[]>(() => {
  const primaryIndex = floorCameraIndex(inspectedBuildingCode.value, inspectedFloorNo.value)
  const secondaryIndex = (primaryIndex + Math.floor(floorCameraVariants.length / 2))
    % floorCameraVariants.length
  const corridorVariant = floorCameraVariants[primaryIndex]
  const publicAreaVariant = floorCameraVariants[secondaryIndex]
  const warningVariant = warningCameraVariants[primaryIndex % warningCameraVariants.length]
  const cameraPrefix = (inspectedBuildingCode.value || 'COMMUNITY')
    + '-'
    + String(inspectedFloorNo.value).padStart(2, '0')
    + 'F'
  return [
    {
      key: 'corridor',
      label: corridorVariant.label,
      code: cameraPrefix + '-C01',
      image: corridorVariant.image,
    },
    {
      key: 'stairwell',
      label: publicAreaVariant.label,
      code: cameraPrefix + '-C02',
      image: publicAreaVariant.image,
    },
    {
      key: 'warning',
      label: warningVariant.label,
      code: cameraPrefix + '-AI',
      image: warningVariant.image,
    },
  ]
})
const selectedCamera = computed(
  () => cameraFeeds.value.find((camera) => camera.key === selectedCameraKey.value) ?? cameraFeeds.value[0],
)
const cameraTimestamp = computed(() =>
  new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(now.value),
)

function project(x: number, z: number, height = 0): Point {
  const width = scene.value?.width ?? 100
  const depth = scene.value?.depth ?? 100
  const angle = (rotation.value * Math.PI) / 180
  const dx = x - width / 2
  const dz = z - depth / 2
  const rx = dx * Math.cos(angle) - dz * Math.sin(angle)
  const rz = dx * Math.sin(angle) + dz * Math.cos(angle)
  return { x: 500 + rx * 7, y: 405 + rz * 3.35 - height * 6 }
}

function projectDeviceToFacade(building: MapBuilding, device: MapDevice): Point {
  const buildingX = Number(building.positionX)
  const buildingZ = Number(building.positionZ)
  const width = Number(building.width)
  const depth = Number(building.depth)
  const corners = [
    { x: buildingX, z: buildingZ },
    { x: buildingX + width, z: buildingZ },
    { x: buildingX + width, z: buildingZ + depth },
    { x: buildingX, z: buildingZ + depth },
  ]
  const projectedBottom = corners.map((corner) => project(corner.x, corner.z))
  const [from, to] = BUILDING_SIDES
    .map((side) => ({ side, depth: (projectedBottom[side[0]].y + projectedBottom[side[1]].y) / 2 }))
    .sort((a, b) => b.depth - a.depth)[0].side
  const faceStart = corners[from]
  const faceEnd = corners[to]
  const localX = Math.min(width, Math.max(0, Number(device.positionX ?? width / 2)))
  const localZ = Math.min(depth, Math.max(0, Number(device.positionZ ?? depth / 2)))
  const height = (Math.min(building.floors, Math.max(1, device.floorNo ?? 1)) - 0.5) * FLOOR_HEIGHT

  // 设备点需要贴在当前视角最近的楼栋立面上。若按楼内 Z 深度直接投影，
  // 透视偏移会让标记看起来落在相邻楼层。
  if (faceStart.z === faceEnd.z) {
    return project(buildingX + localX, faceStart.z, height)
  }
  return project(faceStart.x, buildingZ + localZ, height)
}

function points(items: Point[]): string {
  return items.map((item) => item.x.toFixed(1) + ',' + item.y.toFixed(1)).join(' ')
}

function lerp(from: Point, to: Point, ratio: number): Point {
  return {
    x: from.x + (to.x - from.x) * ratio,
    y: from.y + (to.y - from.y) * ratio,
  }
}

function midpoint(items: Point[]): Point {
  return {
    x: items.reduce((sum, item) => sum + item.x, 0) / items.length,
    y: items.reduce((sum, item) => sum + item.y, 0) / items.length,
  }
}

const chineseDigits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']

function formatChineseNumber(value: number): string {
  if (!Number.isInteger(value) || value < 0 || value > 99) return String(value)
  if (value < 10) return chineseDigits[value]
  const tens = Math.floor(value / 10)
  const ones = value % 10
  return (tens === 1 ? '' : chineseDigits[tens]) + '十' + (ones === 0 ? '' : chineseDigits[ones])
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
    const height = building.floors * FLOOR_HEIGHT
    const bottom = [project(x, z), project(x + width, z), project(x + width, z + depth), project(x, z + depth)]
    const top = [project(x, z, height), project(x + width, z, height), project(x + width, z + depth, height), project(x, z + depth, height)]
    const center = project(x + width / 2, z + depth / 2, height)
    const visibleSides = BUILDING_SIDES
      .map(([from, to]) => ({
        from,
        to,
        depth: (bottom[from].y + bottom[to].y) / 2,
      }))
      .sort((a, b) => b.depth - a.depth)
      .slice(0, 2)
    const buildingDevices = (scene.value?.devices ?? []).filter(
      (device) => device.buildingCode === building.buildingCode,
    )
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
        floorAreas: Array.from({ length: building.floors }, (_, floorIndex) => {
          const floorNo = floorIndex + 1
          const low = floorIndex / building.floors
          const high = floorNo / building.floors
          const corners = [
            lerp(bottom[side.from], top[side.from], low),
            lerp(bottom[side.to], top[side.to], low),
            lerp(bottom[side.to], top[side.to], high),
            lerp(bottom[side.from], top[side.from], high),
          ]
          const devices = buildingDevices.filter((device) => device.floorNo === floorNo)
          return {
            floorNo,
            points: points(corners),
            center: midpoint(corners),
            status: resolveStatus(devices),
          }
        }),
      })),
      center,
      depth: project(x + width / 2, z + depth / 2).y,
      status: resolveStatus(buildingDevices),
    }
  }).sort((a, b) => a.depth - b.depth),
)

const buildingMap = computed(() => new Map((scene.value?.buildings ?? []).map((item) => [item.buildingCode, item])))
const deviceVisuals = computed<DeviceVisual[]>(() =>
  (scene.value?.devices ?? []).flatMap((device) => {
    const building = device.buildingCode ? buildingMap.value.get(device.buildingCode) : null
    if (!building || device.floorNo == null) return []
    const label = device.roomLabel || '第' + formatChineseNumber(device.floorNo) + '层'
    return [{
      device,
      point: projectDeviceToFacade(building, device),
      labelWidth: Math.max(42, label.length * 10 + 18),
    }]
  }),
)

function inspectFloor(buildingCode: string, floorNo: number): void {
  const building = scene.value?.buildings.find((item) => item.buildingCode === buildingCode)
  if (!building) return
  const nextFloor = Math.min(building.floors, Math.max(1, floorNo))
  inspectedBuildingCode.value = buildingCode
  inspectedFloorNo.value = nextFloor
  const devices = (scene.value?.devices ?? []).filter(
    (device) => device.buildingCode === buildingCode && device.floorNo === nextFloor,
  )
  const preferredDevice = devices.find((device) => device.status === 'ALARM')
    ?? devices.find((device) => device.status === 'ONLINE')
    ?? devices[0]
  selectedDeviceId.value = preferredDevice?.id ?? null
  selectedCameraKey.value = devices.some((device) => device.status === 'ALARM') ? 'warning' : 'corridor'
}

function inspectBuilding(building: MapBuilding): void {
  if (inspectedBuildingCode.value === building.buildingCode) {
    inspectFloor(building.buildingCode, inspectedFloorNo.value)
    return
  }
  const buildingDevices = (scene.value?.devices ?? []).filter(
    (device) => device.buildingCode === building.buildingCode && device.floorNo != null,
  )
  const preferredDevice = buildingDevices.find((device) => device.status === 'ALARM')
    ?? buildingDevices.find((device) => device.status === 'ONLINE')
    ?? buildingDevices[0]
  inspectFloor(building.buildingCode, preferredDevice?.floorNo ?? 1)
}

function chooseDevice(device: MapDevice): void {
  if (device.buildingCode && device.floorNo != null) {
    inspectedBuildingCode.value = device.buildingCode
    inspectedFloorNo.value = device.floorNo
    selectedCameraKey.value = device.status === 'ALARM' ? 'warning' : 'corridor'
  }
  selectedDeviceId.value = device.id
  store.selectDevice(device.id)
}

function selectCamera(camera: CameraKey): void {
  selectedCameraKey.value = camera
}

function rotate(delta: number): void {
  rotation.value = (rotation.value + delta + 360) % 360
}

function changeZoom(delta: number): void {
  zoom.value = Math.min(1.35, Math.max(0.75, Number((zoom.value + delta).toFixed(2))))
}

function syncEditForm(device: MapDevice): void {
  editBuilding.value = device.buildingCode ?? scene.value?.buildings[0]?.buildingCode ?? ''
  editFloor.value = device.floorNo ?? 1
  editRoom.value = device.roomLabel ?? '101'
  editX.value = Number(device.positionX ?? 4)
  editZ.value = Number(device.positionZ ?? 4)
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
    const saved = await store.saveMapPosition(device.id, payload)
    if (saved) {
      inspectedBuildingCode.value = payload.buildingCode
      inspectedFloorNo.value = payload.floorNo
      const updated = scene.value?.devices.find((item) => item.id === device.id)
      if (updated) syncEditForm(updated)
    }
  } finally {
    saving.value = false
  }
}

watch(
  scene,
  (current) => {
    if (!current?.buildings.length) {
      inspectionInitialized = false
      inspectedBuildingCode.value = ''
      selectedDeviceId.value = null
      return
    }

    const selectedBuildingStillExists = current.buildings.some(
      (building) => building.buildingCode === inspectedBuildingCode.value,
    )
    if (!inspectionInitialized || !selectedBuildingStillExists) {
      const preferredDevice = current.devices.find((device) => device.status === 'ALARM' && device.buildingCode && device.floorNo != null)
        ?? current.devices.find((device) => device.buildingCode && device.floorNo != null)
      const defaultBuilding = preferredDevice?.buildingCode
        ? current.buildings.find((building) => building.buildingCode === preferredDevice.buildingCode)
        : current.buildings[0]
      if (defaultBuilding) inspectFloor(defaultBuilding.buildingCode, preferredDevice?.floorNo ?? 1)
      inspectionInitialized = true
      return
    }

    const building = current.buildings.find((item) => item.buildingCode === inspectedBuildingCode.value)
    if (building && (inspectedFloorNo.value < 1 || inspectedFloorNo.value > building.floors)) {
      inspectFloor(building.buildingCode, Math.min(building.floors, Math.max(1, inspectedFloorNo.value)))
      return
    }

    if (selectedDeviceId.value !== null && !current.devices.some((device) => device.id === selectedDeviceId.value)) {
      const replacement = current.devices.find(
        (device) => device.buildingCode === inspectedBuildingCode.value && device.floorNo === inspectedFloorNo.value,
      )
      selectedDeviceId.value = replacement?.id ?? null
    }
  },
  { immediate: true },
)

watch(
  selectedDeviceId,
  (id, previousId) => {
    if (id == null || id === previousId) return
    const device = scene.value?.devices.find((item) => item.id === id)
    if (device) syncEditForm(device)
  },
  { immediate: true },
)
</script>

<template>
  <section class="map3d-view view-section">
    <div class="map3d-head">
      <div>
        <span class="role-workspace__eyebrow">SIMULATED DIGITAL TWIN</span>
        <h2>{{ scene?.sceneName ?? '模拟 3D 社区地图' }}</h2>
        <p>点击楼栋立面上的楼层，即可联动查看过道画面、本层设备和实时告警状态。</p>
        <p v-if="store.mapSceneStale" class="map3d-stale-warning">
          地图状态刷新失败，旧的在线标记已按离线处理，请检查公网 API 连接。
        </p>
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
        <svg v-else viewBox="0 0 1000 660" role="group" aria-label="可点击楼栋和楼层的智慧社区模拟三维地图">
          <g class="map3d-world" :style="{ transform: 'scale(' + zoom + ')', transformOrigin: '500px 350px' }">
            <polygon class="map3d-ground" :points="points([project(0, 0), project(100, 0), project(100, 100), project(0, 100)])" />
            <line
              v-for="(line, index) in gridLines"
              :key="'grid-' + index"
              class="map3d-gridline"
              :x1="line.a.x" :y1="line.a.y" :x2="line.b.x" :y2="line.b.y"
            />

            <g
              v-for="visual in buildingVisuals"
              :key="visual.building.buildingCode"
              class="map3d-building"
              :class="[
                'map3d-building--' + visual.status.toLowerCase(),
                { selected: inspectedBuildingCode === visual.building.buildingCode },
              ]"
              @click="inspectBuilding(visual.building)"
            >
              <g v-for="(side, sideIndex) in visual.sides" :key="visual.building.buildingCode + '-side-' + sideIndex">
                <polygon :class="'map3d-building__side map3d-building__side--' + side.tone" :points="side.points" />
                <line
                  v-for="(floorLine, floorIndex) in side.floorLines"
                  :key="visual.building.buildingCode + '-line-' + sideIndex + '-' + floorIndex"
                  class="map3d-building__floor-line"
                  :x1="floorLine.a.x" :y1="floorLine.a.y" :x2="floorLine.b.x" :y2="floorLine.b.y"
                />
                <template v-if="sideIndex === 0">
                  <g
                    v-for="floorArea in side.floorAreas"
                    :key="visual.building.buildingCode + '-floor-' + floorArea.floorNo"
                    class="map3d-floor-area"
                    :class="[
                      'map3d-floor-area--' + floorArea.status.toLowerCase(),
                      {
                        selected: inspectedBuildingCode === visual.building.buildingCode
                          && inspectedFloorNo === floorArea.floorNo,
                      },
                    ]"
                    tabindex="0"
                    role="button"
                    :aria-label="formatBuildingName(visual.building) + '第' + formatChineseNumber(floorArea.floorNo) + '层，' + statusLabel(floorArea.status)"
                    @click.stop="inspectFloor(visual.building.buildingCode, floorArea.floorNo)"
                    @keydown.enter.stop="inspectFloor(visual.building.buildingCode, floorArea.floorNo)"
                    @keydown.space.stop.prevent="inspectFloor(visual.building.buildingCode, floorArea.floorNo)"
                  >
                    <polygon class="map3d-floor-area__shape" :points="floorArea.points" />
                    <text
                      v-if="inspectedBuildingCode === visual.building.buildingCode"
                      class="map3d-floor-area__label"
                      :x="floorArea.center.x"
                      :y="floorArea.center.y + 3"
                      text-anchor="middle"
                    >{{ floorArea.floorNo }}层</text>
                  </g>
                </template>
              </g>
              <polygon class="map3d-building__top" :points="visual.top" />
              <g class="map3d-building__label" :transform="'translate(' + visual.center.x + ' ' + (visual.center.y - 18) + ')'">
                <rect x="-76" y="-31" width="152" height="38" rx="8" />
                <text class="map3d-building__name" x="0" y="-16" text-anchor="middle">{{ formatBuildingName(visual.building) }}</text>
                <text class="map3d-building__meta" x="0" y="-3" text-anchor="middle">共{{ formatChineseNumber(visual.building.floors) }}层 · 点击楼层</text>
              </g>
            </g>

            <g
              v-for="visual in deviceVisuals"
              :key="visual.device.id"
              class="map3d-device"
              :class="[
                'map3d-device--' + visual.device.status.toLowerCase(),
                { selected: selectedDeviceId === visual.device.id },
              ]"
              tabindex="0"
              role="button"
              :aria-label="(visual.device.deviceName || visual.device.deviceId) + '，' + statusLabel(visual.device.status)"
              @click.stop="chooseDevice(visual.device)"
              @keydown.enter.stop="chooseDevice(visual.device)"
              @keydown.space.stop.prevent="chooseDevice(visual.device)"
            >
              <line :x1="visual.point.x" :y1="visual.point.y" :x2="visual.point.x" :y2="visual.point.y + 20" />
              <circle :cx="visual.point.x" :cy="visual.point.y" r="9" />
              <circle class="map3d-device__pulse" :cx="visual.point.x" :cy="visual.point.y" r="15" />
              <g class="map3d-device__label" :transform="'translate(' + (visual.point.x + 13) + ' ' + (visual.point.y - 24) + ')'">
                <rect x="0" y="0" :width="visual.labelWidth" height="22" rx="6" />
                <text x="9" y="15">{{ visual.device.roomLabel || '第' + formatChineseNumber(visual.device.floorNo ?? 0) + '层' }}</text>
              </g>
            </g>
          </g>
        </svg>

        <div class="map3d-legend">
          <span><i class="legend-dot legend-dot--online"></i>在线</span>
          <span><i class="legend-dot legend-dot--alarm"></i>告警</span>
          <span><i class="legend-dot legend-dot--offline"></i>离线</span>
        </div>
        <div class="map3d-guide">点击楼栋立面选择楼层</div>
      </div>

      <aside class="map3d-detail panel">
        <template v-if="inspectedBuilding">
          <div class="map3d-detail__head">
            <div>
              <span class="role-workspace__eyebrow">楼层巡查与视觉复核</span>
              <h3>{{ formatBuildingName(inspectedBuilding) }} · 第{{ formatChineseNumber(inspectedFloorNo) }}层</h3>
            </div>
            <span class="map3d-state" :class="'map3d-state--' + selectedFloorStatus.toLowerCase()">
              {{ statusLabel(selectedFloorStatus) }}
            </span>
          </div>

          <section class="map3d-floor-browser" aria-label="楼层选择">
            <div class="map3d-section-title">
              <strong>选择楼层</strong>
              <small>地图立面和按钮可联动</small>
            </div>
            <div class="map3d-floor-grid">
              <button
                v-for="floor in inspectedFloorSummaries"
                :key="floor.floorNo"
                type="button"
                class="map3d-floor-button"
                :class="[
                  'map3d-floor-button--' + floor.status.toLowerCase(),
                  { active: inspectedFloorNo === floor.floorNo },
                ]"
                :aria-pressed="inspectedFloorNo === floor.floorNo"
                :title="floor.devices.length + ' 台设备 · ' + statusLabel(floor.status)"
                @click="inspectFloor(inspectedBuilding.buildingCode, floor.floorNo)"
              >
                <i></i>{{ floor.floorNo }}层
              </button>
            </div>
          </section>

          <section class="map3d-camera">
            <div class="map3d-section-title">
              <strong>楼层监控画面</strong>
              <small>{{ selectedCamera?.code }}</small>
            </div>
            <div class="map3d-camera-tabs" aria-label="模拟摄像头">
              <button
                v-for="camera in cameraFeeds"
                :key="camera.key"
                type="button"
                :aria-pressed="selectedCameraKey === camera.key"
                :class="{ active: selectedCameraKey === camera.key }"
                @click="selectCamera(camera.key)"
              >
                {{ camera.label }}
              </button>
            </div>
            <figure
              v-if="selectedCamera"
              class="map3d-camera-frame"
              :class="{ 'map3d-camera-frame--warning': selectedCamera.key === 'warning' }"
            >
              <img
                :key="selectedCamera.code + ':' + selectedCamera.image"
                :src="selectedCamera.image"
                :alt="formatBuildingName(inspectedBuilding) + '第' + formatChineseNumber(inspectedFloorNo) + '层' + selectedCamera.label + '模拟画面'"
                decoding="async"
              />
              <div class="map3d-camera-frame__top">
                <span><i></i>模拟画面</span>
                <time>{{ cameraTimestamp }}</time>
              </div>
              <div v-if="selectedCamera.key === 'warning'" class="map3d-ai-detection">
                <span>AI 识别演示</span>
                <b>疑似烟雾区域</b>
              </div>
              <figcaption>
                <span>{{ formatBuildingName(inspectedBuilding) }} · {{ inspectedFloorNo }}层 · {{ selectedCamera.label }}</span>
                <span>{{ floorDevices.length }} 台感知设备</span>
              </figcaption>
            </figure>
            <p class="map3d-camera-note">当前 19 个楼层的常规机位均使用独立 AI 生成图片；仅用于交互演示，尚未接入真实摄像头视频流。</p>
          </section>

          <section class="map3d-floor-devices">
            <div class="map3d-section-title">
              <strong>本层设备</strong>
              <small>{{ floorDevices.length }} 台</small>
            </div>
            <div v-if="floorDevices.length" class="map3d-floor-device-list">
              <button
                v-for="device in floorDevices"
                :key="device.id"
                type="button"
                :class="{ active: selectedDeviceId === device.id }"
                @click="chooseDevice(device)"
              >
                <i :class="'map3d-floor-device-dot--' + device.status.toLowerCase()"></i>
                <span>
                  <strong>{{ device.deviceName || device.deviceId }}</strong>
                  <small>{{ device.roomLabel || '未设置房间' }}</small>
                </span>
                <em>{{ statusLabel(device.status) }}</em>
              </button>
            </div>
            <div v-else class="map3d-floor-empty">本层暂无烟感设备，可继续查看演示监控画面。</div>
          </section>

          <section v-if="selectedFloorDevice" class="map3d-inspector">
            <div class="map3d-inspector__head">
              <div>
                <small>设备空间详情</small>
                <h4>{{ selectedFloorDevice.deviceName || selectedFloorDevice.deviceId }}</h4>
              </div>
              <span class="map3d-state" :class="'map3d-state--' + selectedFloorDevice.status.toLowerCase()">
                {{ statusLabel(selectedFloorDevice.status) }}
              </span>
            </div>
            <dl class="map3d-location">
              <div><dt>设备编号</dt><dd>{{ selectedFloorDevice.deviceId }}</dd></div>
              <div><dt>空间位置</dt><dd>{{ selectedFloorDevice.buildingName }} 第{{ formatChineseNumber(selectedFloorDevice.floorNo ?? 0) }}层 {{ selectedFloorDevice.roomLabel }}</dd></div>
              <div><dt>安装说明</dt><dd>{{ selectedFloorDevice.location || '—' }}</dd></div>
              <div><dt>最新数据</dt><dd>{{ fmtFull(selectedFloorDevice.latestTimestamp) }}</dd></div>
            </dl>
            <div class="map3d-metrics">
              <span><b>{{ conc(selectedFloorDevice.smoke) }}</b> ppm<small>烟雾</small></span>
              <span><b>{{ conc(selectedFloorDevice.temperature) }}</b> ℃<small>温度</small></span>
              <span><b>{{ conc(selectedFloorDevice.coValue) }}</b> ppm<small>CO</small></span>
              <span><b>{{ selectedFloorDevice.battery ?? '—' }}</b> %<small>电量</small></span>
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
                <label>楼层<input v-model.number="editFloor" type="number" min="1" :max="editSelectedBuilding?.floors ?? 1" /></label>
                <label>房间<input v-model="editRoom" maxlength="64" required /></label>
              </div>
              <div class="map3d-form__row">
                <label>楼内 X<input v-model.number="editX" type="number" min="0" :max="Number(editSelectedBuilding?.width ?? 0)" step="0.1" /></label>
                <label>楼内 Z<input v-model.number="editZ" type="number" min="0" :max="Number(editSelectedBuilding?.depth ?? 0)" step="0.1" /></label>
              </div>
              <button class="btn-primary" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存地图位置' }}</button>
            </form>
          </section>
        </template>
        <div v-else class="map3d-empty">暂无可浏览的楼栋</div>
      </aside>
    </div>
  </section>
</template>
