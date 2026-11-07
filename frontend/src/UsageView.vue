<script setup>
import { ref, onMounted } from 'vue'
import { api } from './api'
import { dateTime, money } from './format'
import PagedNavigation from './PagedNavigation.vue'
const props = defineProps({ all: Boolean })
const tab = ref('sessions')
const result = ref({ items: [], page: 1, size: 10, total: 0 })
const busy = ref(false)
const error = ref('')
const labels = { PARKED: '停车中', CHARGING: '充电中', FINISHED: '已离场' }
let querySequence = 0
async function load(page = 1) {
  const sequence = ++querySequence
  busy.value = true
  error.value = ''
  try {
    const prefix = props.all ? '/admin' : ''
    const data = await api(`${prefix}/${tab.value}?page=${page}&size=10`)
    if (sequence === querySequence) result.value = data
  } catch (failure) {
    if (sequence === querySequence) error.value = failure.message
  } finally {
    if (sequence === querySequence) busy.value = false
  }
}
function selectTab(value) {
  tab.value = value
  load()
}
onMounted(() => load())
</script>
<template>
  <section class="phase-page">
    <div class="phase-title">
      <div>
        <h1>{{ all ? '平台会话与账单' : '我的会话与账单' }}</h1>
        <p>入场、充电与离场来自模拟设备事件。结算均为本地模拟。</p>
      </div>
      <button class="button quiet" :disabled="busy" @click="load(result.page)">刷新</button>
    </div>
    <div class="admin-tabs">
      <button :class="{ active: tab === 'sessions' }" @click="selectTab('sessions')">
        停车充电会话
      </button>
      <button :class="{ active: tab === 'bills' }" @click="selectTab('bills')">实际费用账单</button>
    </div>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <p v-if="busy" class="list-loading">正在查询…</p>
    <template v-else-if="result.items.length">
      <div v-if="tab === 'sessions'" class="phase-cards">
        <article v-for="session in result.items" :key="session.id" class="phase-card">
          <div class="phase-card-heading">
            <h3>{{ session.resourceCode }} · {{ session.parkName }}</h3>
            <span
              class="status-tag"
              :class="session.status === 'FINISHED' ? 'completed' : 'confirmed'"
            >
              {{ labels[session.status] }}
            </span>
          </div>
          <p>
            {{ session.plateNumber }} · {{ session.type === 'CHARGING' ? '充电车位' : '普通车位' }}
          </p>
          <dl class="phase-facts">
            <div>
              <dt>入场</dt>
              <dd>{{ dateTime(session.enteredAt) }}</dd>
            </div>
            <div>
              <dt>离场</dt>
              <dd>{{ session.exitedAt ? dateTime(session.exitedAt) : '尚未离场' }}</dd>
            </div>
            <div>
              <dt>累计电量</dt>
              <dd>{{ session.energyKwh }} kWh</dd>
            </div>
            <div>
              <dt>电价快照</dt>
              <dd>¥{{ money(session.energyRate) }} / kWh</dd>
            </div>
          </dl>
          <p class="phase-id">
            预约 ID：{{ session.bookingId }} · 已处理序号 {{ session.lastSequence }}
          </p>
        </article>
      </div>
      <div v-else class="phase-cards">
        <article v-for="bill in result.items" :key="bill.id" class="phase-card">
          <div class="phase-card-heading">
            <h3>{{ bill.resourceCode }} · 实际费用</h3>
            <strong class="phase-total">¥{{ money(bill.totalAmount) }}</strong>
          </div>
          <dl class="phase-facts">
            <div>
              <dt>停车 {{ bill.parkingMinutes }} 分钟</dt>
              <dd>¥{{ money(bill.parkingAmount) }}</dd>
            </div>
            <div>
              <dt>用电 {{ bill.energyKwh }} kWh</dt>
              <dd>¥{{ money(bill.energyAmount) }}</dd>
            </div>
            <div>
              <dt>预约预付款</dt>
              <dd>¥{{ money(bill.prepaidAmount) }}</dd>
            </div>
            <div>
              <dt>模拟补付 / 退回</dt>
              <dd>¥{{ money(bill.additionalAmount) }} / ¥{{ money(bill.refundAmount) }}</dd>
            </div>
          </dl>
          <p class="help-text">
            已模拟结算 · {{ dateTime(bill.createdAt) }} · 不发生真实扣款或退款
          </p>
          <p class="phase-id">预约 ID：{{ bill.bookingId }}</p>
        </article>
      </div>
    </template>
    <div v-else class="empty-state">
      <h3>暂无{{ tab === 'sessions' ? '会话' : '账单' }}</h3>
      <p>预约支付后，由管理员在设备模拟中发送入场、充电和离场事件。</p>
    </div>
    <PagedNavigation
      :page="result.page"
      :size="result.size"
      :total="result.total"
      :busy="busy"
      @change="load"
    />
  </section>
</template>
