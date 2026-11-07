<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { api } from './api'
import { dateTime } from './format'
const devices = ref([])
const bookings = ref([])
const selectedId = ref('')
const selected = computed(() => bookings.value.find((b) => b.id === selectedId.value))
const events = ref([])
const busy = ref(false)
const message = ref('')
const error = ref('')
const scenario = ref([])
const draft = reactive({
  eventId: crypto.randomUUID(),
  sequenceNo: 1,
  type: 'ENTER',
  occurredAt: '',
  energyKwh: 0,
})
const eventLabels = {
  ENTER: '入场',
  CHARGE_STARTED: '开始充电',
  METER_REPORTED: '累计电表',
  CHARGE_STOPPED: '停止充电',
  EXIT: '离场',
}
const eventStates = {
  WAITING: '等待前序事件',
  APPLIED: '已应用',
  FAILED: '处理失败',
  SKIPPED: '人工跳过',
}
function localInput(iso) {
  return new Date(new Date(iso).getTime() + 8 * 3600000).toISOString().slice(0, 19)
}
async function refresh() {
  const [deviceList, bookingList] = await Promise.all([
    api('/admin/devices'),
    api('/admin/bookings?size=100'),
  ])
  devices.value = deviceList
  bookings.value = bookingList.items
  if (selectedId.value) await loadEvents()
}
async function loadEvents() {
  const result = await api(`/admin/device-events?bookingId=${selectedId.value}&size=100`)
  events.value = result.items.sort((a, b) => a.sequenceNo - b.sequenceNo)
}
async function chooseBooking() {
  scenario.value = []
  if (!selected.value) {
    events.value = []
    return
  }
  await perform(async () => {
    await loadEvents()
    prepareNext()
  })
}
function prepareNext() {
  draft.eventId = crypto.randomUUID()
  const applied = events.value.filter((e) => ['APPLIED', 'SKIPPED'].includes(e.status))
  draft.sequenceNo = Math.max(0, ...applied.map((e) => e.sequenceNo)) + 1
  draft.occurredAt = localInput(selected.value.startAt)
}
async function perform(action) {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (failure) {
    error.value = `${failure.message}${failure.requestId ? `（请求 ${failure.requestId}）` : ''}`
  } finally {
    busy.value = false
  }
}
async function heartbeat(device, fault) {
  await perform(async () => {
    await api(`/admin/devices/${device.id}/heartbeat`, {
      method: 'POST',
      body: { fault, message: fault ? '演示设备故障' : '' },
    })
    await refresh()
    message.value = fault ? '故障心跳已记录，请到运营中心查看工单' : '正常心跳已更新'
  })
}
function generateScenario() {
  const b = selected.value
  if (!b) return
  const start = new Date(b.startAt).getTime()
  const duration = Math.min(3600000, new Date(b.endAt).getTime() - start)
  const rows =
    b.type === 'CHARGING'
      ? [
          [1, 'ENTER', 0, 0],
          [2, 'CHARGE_STARTED', 1000, 0],
          [3, 'METER_REPORTED', duration / 2, 5],
          [4, 'CHARGE_STOPPED', duration - 1000, 10],
          [5, 'EXIT', duration, 10],
        ]
      : [
          [1, 'ENTER', 0, 0],
          [2, 'EXIT', duration, 0],
        ]
  // 演示批次只生成一次事件号。网络失败后重新发送仍使用同一组事件，避免重复制单。
  scenario.value = rows.map(([sequenceNo, type, offset, energyKwh]) => ({
    eventId: crypto.randomUUID(),
    bookingId: b.id,
    sequenceNo,
    type,
    occurredAt: new Date(start + offset).toISOString(),
    energyKwh,
  }))
  message.value = '已生成固定事件号的模拟批次；可按序、倒序或重复发送'
}
async function runScenario(order) {
  await perform(async () => {
    const batch = order === 'reverse' ? [...scenario.value].reverse() : scenario.value
    for (const event of batch) {
      await api('/admin/device-events', { method: 'POST', body: event })
      if (order === 'duplicate') await api('/admin/device-events', { method: 'POST', body: event })
    }
    await refresh()
    message.value = '批次已接收，请查看每条事件状态及会话账单'
  })
}
async function submitEvent() {
  await perform(async () => {
    const result = await api('/admin/device-events', {
      method: 'POST',
      body: {
        ...draft,
        bookingId: selectedId.value,
        occurredAt: new Date(`${draft.occurredAt}+08:00`).toISOString(),
        energyKwh: Number(draft.energyKwh),
      },
    })
    await refresh()
    message.value = `事件${eventStates[result.status]}${result.errorMessage ? `：${result.errorMessage}` : ''}`
  })
}
const reason = ref('')
async function eventAction(event, action) {
  if (!reason.value.trim()) {
    error.value = '请先填写处理原因'
    return
  }
  await perform(async () => {
    const result = await api(`/admin/device-events/${event.id}/action`, {
      method: 'POST',
      body: { action, reason: reason.value },
    })
    await refresh()
    message.value = `事件${eventStates[result.status]}`
  })
}
onMounted(() => perform(refresh))
</script>
<template>
  <section class="phase-page">
    <div class="phase-title">
      <div>
        <h1>设备模拟</h1>
        <p>使用预约时间作为模拟事件时间，无需等待真实入场。所有设备与支付均为模拟。</p>
      </div>
      <button class="button quiet" :disabled="busy" @click="perform(refresh)">刷新</button>
    </div>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="phase-success" role="status">{{ message }}</p>
    <article class="phase-card">
      <h2>心跳与故障</h2>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>资源</th>
              <th>设备状态</th>
              <th>最近心跳</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="device in devices" :key="device.id">
              <td>
                {{ device.resourceCode }} ·
                {{ device.type === 'CHARGING' ? '充电桩' : '车位传感器' }}
              </td>
              <td>
                {{
                  { UNKNOWN: '未连接', ONLINE: '在线', OFFLINE: '离线', FAULT: '故障' }[
                    device.status
                  ]
                }}
              </td>
              <td>{{ device.lastSeenAt ? dateTime(device.lastSeenAt) : '暂无' }}</td>
              <td>
                <div class="phase-inline">
                  <button
                    class="button quiet small"
                    :disabled="busy"
                    @click="heartbeat(device, false)"
                  >
                    正常心跳
                  </button>
                  <button
                    class="button quiet small"
                    :disabled="busy"
                    @click="heartbeat(device, true)"
                  >
                    模拟故障
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
    <article class="phase-card phase-form">
      <h2>选择预约</h2>
      <p class="help-text">显示最近 100 条预约。先完成模拟支付再入场。已入场不能取消预约。</p>
      <label>
        预约
        <select v-model="selectedId" :disabled="busy" @change="chooseBooking">
          <option value="">请选择已支付预约</option>
          <option
            v-for="b in bookings.filter((b) =>
              ['CONFIRMED', 'IN_USE', 'COMPLETED'].includes(b.status),
            )"
            :key="b.id"
            :value="b.id"
          >
            {{ b.resourceCode }} · {{ b.plateNumber }} · {{ dateTime(b.startAt) }} · {{ b.status }}
          </option>
        </select>
      </label>
      <template v-if="selected">
        <p class="phase-id">{{ selected.id }} · {{ selected.parkName }}</p>
        <div class="phase-inline">
          <button
            class="button quiet"
            :disabled="busy || selected.status !== 'CONFIRMED' || events.length > 0"
            @click="generateScenario"
          >
            生成演示批次
          </button>
          <button
            class="button primary"
            :disabled="busy || !scenario.length"
            @click="runScenario('normal')"
          >
            按序发送
          </button>
          <button
            class="button quiet"
            :disabled="busy || !scenario.length"
            @click="runScenario('reverse')"
          >
            倒序发送
          </button>
          <button
            class="button quiet"
            :disabled="busy || !scenario.length"
            @click="runScenario('duplicate')"
          >
            重复发送
          </button>
        </div>
        <p class="help-text">
          倒序批次会先保存离场事件，补齐入场后再按序应用；重复批次复用原事件号。
        </p>
      </template>
    </article>
    <article v-if="selected" class="phase-card phase-form">
      <div class="phase-card-heading">
        <h2>手动事件</h2>
        <button class="button quiet small" :disabled="busy" @click="prepareNext">
          准备下一个事件
        </button>
      </div>
      <p class="help-text">
        重试复用原事件号；修改内容前应生成新事件。时间按上海时区输入，电量为会话累计值。
      </p>
      <form @submit.prevent="submitEvent">
        <div class="phase-fields">
          <label>
            事件号
            <input v-model="draft.eventId" required maxlength="64" />
          </label>
          <label>
            序号
            <input v-model.number="draft.sequenceNo" type="number" min="1" max="10000" required />
          </label>
          <label>
            类型
            <select v-model="draft.type">
              <option v-for="(label, type) in eventLabels" :key="type" :value="type">
                {{ label }}
              </option>
            </select>
          </label>
          <label>
            模拟发生时间
            <input v-model="draft.occurredAt" type="datetime-local" step="1" required />
          </label>
          <label>
            累计电量 kWh
            <input
              v-model.number="draft.energyKwh"
              type="number"
              min="0"
              max="10000"
              step="0.001"
              required
            />
          </label>
        </div>
        <button class="button primary" :disabled="busy">发送模拟事件</button>
      </form>
      <label>
        重试或跳过的处理原因
        <input v-model="reason" maxlength="500" placeholder="例如：前车已离场，可重试入场" />
      </label>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>序号</th>
              <th>事件</th>
              <th>状态</th>
              <th>说明</th>
              <th>处理</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="event in events" :key="event.id">
              <td>{{ event.sequenceNo }}</td>
              <td>
                {{ eventLabels[event.type] }}
                <small class="phase-id">{{ event.id }}</small>
              </td>
              <td>{{ eventStates[event.status] }}</td>
              <td>{{ event.errorMessage || '—' }}</td>
              <td>
                <div v-if="['WAITING', 'FAILED'].includes(event.status)" class="phase-inline">
                  <button
                    class="button quiet small"
                    :disabled="busy"
                    @click="eventAction(event, 'RETRY')"
                  >
                    重试
                  </button>
                  <button
                    class="button quiet small"
                    :disabled="busy"
                    @click="eventAction(event, 'SKIP')"
                  >
                    跳过
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
