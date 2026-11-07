<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api'
import { dateTime } from './format'
import PagedNavigation from './PagedNavigation.vue'
const overview = ref(null)
const incidents = ref({ items: [], page: 1, size: 10, total: 0 })
const audits = ref({ items: [], page: 1, size: 10, total: 0 })
const state = ref('OPEN')
const tab = ref('incidents')
const busy = ref(false)
const error = ref('')
const message = ref('')
const selected = ref(null)
const resolution = ref('')
async function load(page = 1) {
  overview.value = await api('/admin/operations/overview')
  if (tab.value === 'incidents')
    incidents.value = await api(
      `/admin/operations/incidents?status=${state.value}&page=${page}&size=10`,
    )
  else if (tab.value === 'audits')
    audits.value = await api(`/admin/operations/audits?page=${page}&size=10`)
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
function refresh(page = 1) {
  perform(() => load(page))
}
function selectTab(value) {
  tab.value = value
  refresh()
}
function selectIncident(incident) {
  selected.value = incident
  resolution.value = ''
}
async function scan() {
  await perform(async () => {
    await api('/admin/operations/scan', { method: 'POST' })
    await load()
    message.value = '扫描完成。设备心跳超时、缺失前序事件和超时会话会生成工单。'
  })
}
async function resolve() {
  if (!resolution.value.trim()) return
  await perform(async () => {
    await api(`/admin/operations/incidents/${selected.value.id}/resolve`, {
      method: 'POST',
      body: { resolution: resolution.value },
    })
    selected.value = null
    resolution.value = ''
    await load()
    message.value = '异常工单已关闭并记录审计'
  })
}
onMounted(() => refresh())
</script>
<template>
  <section class="phase-page">
    <div class="phase-title">
      <div>
        <h1>运营中心</h1>
        <p>先恢复业务，再关闭工单。请求标识可用于查询后端日志。</p>
      </div>
      <div class="phase-inline">
        <button class="button quiet" :disabled="busy" @click="refresh()">刷新</button>
        <button class="button primary" :disabled="busy" @click="scan">立即扫描异常</button>
      </div>
    </div>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="message" class="phase-success" role="status">{{ message }}</p>
    <div v-if="overview" class="phase-metrics">
      <article>
        <span>待处理异常</span>
        <strong>{{ overview.openIncidents }}</strong>
      </article>
      <article>
        <span>进行中会话</span>
        <strong>{{ overview.activeSessions }}</strong>
      </article>
      <article>
        <span>等待 / 失败事件</span>
        <strong>{{ overview.waitingEvents }} / {{ overview.failedEvents }}</strong>
      </article>
      <article>
        <span>已模拟结算</span>
        <strong>{{ overview.settledBills }}</strong>
      </article>
    </div>
    <div class="admin-tabs">
      <button
        :disabled="busy"
        :class="{ active: tab === 'incidents' }"
        @click="selectTab('incidents')"
      >
        异常工单
      </button>
      <button :disabled="busy" :class="{ active: tab === 'audits' }" @click="selectTab('audits')">
        操作审计
      </button>
      <button :disabled="busy" :class="{ active: tab === 'metrics' }" @click="selectTab('metrics')">
        接口监控
      </button>
    </div>
    <template v-if="tab === 'incidents'">
      <label class="phase-filter">
        工单状态
        <select v-model="state" :disabled="busy" @change="refresh()">
          <option value="OPEN">待处理</option>
          <option value="RESOLVED">已解决</option>
        </select>
      </label>
      <div v-if="incidents.items.length" class="phase-cards">
        <article v-for="incident in incidents.items" :key="incident.id" class="phase-card">
          <div class="phase-card-heading">
            <h3>{{ incident.title }}</h3>
            <span
              class="status-tag"
              :class="incident.status === 'OPEN' ? 'cancelled' : 'confirmed'"
            >
              {{ incident.status === 'OPEN' ? '待处理' : '已解决' }}
            </span>
          </div>
          <p>{{ incident.detail }}</p>
          <p class="phase-id">来源 {{ incident.sourceType }} / {{ incident.sourceId }}</p>
          <p class="help-text">
            {{ dateTime(incident.createdAt)
            }}{{ incident.resolution ? ` · 处理说明：${incident.resolution}` : '' }}
          </p>
          <button
            v-if="incident.status === 'OPEN'"
            class="button quiet small"
            :disabled="busy"
            @click="selectIncident(incident)"
          >
            填写处理结果
          </button>
        </article>
      </div>
      <div v-else class="empty-state">
        <h3>暂无{{ state === 'OPEN' ? '待处理' : '已解决' }}工单</h3>
        <p>可在设备模拟中报告故障，恢复正常心跳后填写处理结果。</p>
      </div>
      <PagedNavigation
        :page="incidents.page"
        :size="incidents.size"
        :total="incidents.total"
        :busy="busy"
        @change="refresh"
      />
      <form v-if="selected" class="phase-card phase-form" @submit.prevent="resolve">
        <h3>处理 {{ selected.title }}</h3>
        <p class="help-text">
          设备需先恢复正常心跳；事件需补齐、成功重试或跳过；超时会话需先离场。
        </p>
        <label>
          处理说明
          <textarea v-model="resolution" maxlength="500" required rows="3" />
        </label>
        <div class="phase-inline">
          <button type="button" class="button quiet" :disabled="busy" @click="selected = null">
            收起
          </button>
          <button class="button primary" :disabled="busy || !resolution.trim()">
            关闭已恢复的工单
          </button>
        </div>
      </form>
    </template>
    <template v-else-if="tab === 'audits'">
      <div class="phase-card table-scroll">
        <table>
          <thead>
            <tr>
              <th>时间</th>
              <th>操作</th>
              <th>说明</th>
              <th>请求标识</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="audit in audits.items" :key="audit.id">
              <td>{{ dateTime(audit.createdAt) }}</td>
              <td>{{ audit.action }}</td>
              <td>
                {{ audit.detail }}
                <small class="phase-id">{{ audit.targetId }}</small>
              </td>
              <td class="phase-id">{{ audit.requestId }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <PagedNavigation
        :page="audits.page"
        :size="audits.size"
        :total="audits.total"
        :busy="busy"
        @change="refresh"
      />
    </template>
    <article v-else-if="overview" class="phase-card">
      <h2>当前进程接口指标</h2>
      <p class="help-text">
        采样于 {{ dateTime(overview.sampledAt) }} · 堆使用约
        {{ (overview.heapUsedBytes / 1024 / 1024).toFixed(1) }}
        MB。请求计数随进程重启清零；最大延迟为指标时间窗口内最大值。
      </p>
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>路由</th>
              <th>HTTP</th>
              <th>次数</th>
              <th>平均毫秒</th>
              <th>最大毫秒</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="metric in overview.http" :key="`${metric.route}-${metric.status}`">
              <td>{{ metric.route }}</td>
              <td>{{ metric.status }}</td>
              <td>{{ metric.count }}</td>
              <td>{{ (metric.totalMs / Math.max(1, metric.count)).toFixed(2) }}</td>
              <td>{{ metric.maxMs.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>
  </section>
</template>
