<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { api, getToken, saveToken } from './api'
import { bookingLabels, dateTime, localDate, money, timeOptions, windowFromInputs } from './format'
import Icon from './Icon.vue'
import AuthDialog from './AuthDialog.vue'
import BookingDialog from './BookingDialog.vue'
import BookingCard from './BookingCard.vue'
import DialogShell from './DialogShell.vue'
import ResourceCard from './ResourceCard.vue'
import GuideView from './GuideView.vue'
import UsageView from './UsageView.vue'
import SimulatorView from './SimulatorView.vue'
import OperationsView from './OperationsView.vue'

const user = ref(null)
const view = ref('explore')
const parks = ref([])
const resources = ref([])
const adminResources = ref([])
const adminTab = ref('resources')
const loading = ref(false)
const listLoading = ref(false)
const pageError = ref('')
const authOpen = ref(false)
const selectedResource = ref(null)
const pendingResource = ref(null)
const selectedWindow = ref(null)
const actionDialog = ref(null)
const actionBusy = ref(false)
const dialogError = ref('')
const toast = ref(null)
const now = ref(Date.now())
const tomorrow = localDate(new Date(Date.now() + 86400000))
const filters = reactive({
  parkCode: '',
  type: '',
  date: tomorrow,
  start: '09:00',
  end: '12:00',
  availableOnly: false,
})
const bookingStatus = ref('')
const bookingPage = ref(1)
const bookingResult = ref({ items: [], total: 0, page: 1, size: 8 })
const currentWindow = ref(null)
let resourceSequence = 0
let listSequence = 0
let searchTimer
let toastTimer
let clockTimer

const isAdmin = computed(() => user.value?.role === 'ADMIN')
const visibleResources = computed(() =>
  resources.value.filter((item) => !filters.availableOnly || item.available),
)
const availableCount = computed(() => resources.value.filter((item) => item.available).length)
const chargingCount = computed(
  () => resources.value.filter((item) => item.type === 'CHARGING').length,
)
const hours = computed(() => currentWindow.value?.hours || 0)
const navigation = computed(() => [
  { id: 'explore', label: '找位预约', icon: 'grid' },
  { id: 'bookings', label: '我的预约', icon: 'calendar' },
  ...(user.value ? [{ id: 'usage', label: '会话与账单', icon: 'bolt' }] : []),
  ...(isAdmin.value ? [{ id: 'admin', label: '运营管理', icon: 'settings' }] : []),
  ...(isAdmin.value
    ? [
        { id: 'simulator', label: '设备模拟', icon: 'car' },
        { id: 'operations', label: '运营中心', icon: 'grid' },
        { id: 'all-usage', label: '平台会话账单', icon: 'calendar' },
      ]
    : []),
  { id: 'guide', label: '使用指南', icon: 'info' },
])
const pageTitle = computed(
  () => navigation.value.find((item) => item.id === view.value)?.label || '找位预约',
)
const maxDate = localDate(new Date(Date.now() + 30 * 86400000))

function notify(message, type = 'success') {
  clearTimeout(toastTimer)
  toast.value = { message, type }
  toastTimer = setTimeout(() => {
    toast.value = null
  }, 4500)
}

async function loadResources() {
  const sequence = ++resourceSequence
  loading.value = true
  pageError.value = ''
  try {
    const window = windowFromInputs(filters.date, filters.start, filters.end)
    const query = new URLSearchParams({ startAt: window.startAt, endAt: window.endAt })
    if (filters.parkCode) query.set('parkCode', filters.parkCode)
    if (filters.type) query.set('type', filters.type)
    const result = await api(`/resources?${query}`)
    if (sequence !== resourceSequence) return
    resources.value = result
    currentWindow.value = window
  } catch (error) {
    if (sequence !== resourceSequence) return
    pageError.value = error.message
    resources.value = []
    currentWindow.value = null
  } finally {
    if (sequence === resourceSequence) loading.value = false
  }
}

async function loadBookings() {
  if (!user.value) return
  const sequence = ++listSequence
  listLoading.value = true
  pageError.value = ''
  try {
    const query = new URLSearchParams({ page: bookingPage.value, size: 8 })
    if (bookingStatus.value) query.set('status', bookingStatus.value)
    const prefix = view.value === 'admin' ? '/admin/bookings' : '/bookings'
    const result = await api(`${prefix}?${query}`)
    if (sequence === listSequence) bookingResult.value = result
  } catch (error) {
    if (sequence === listSequence) pageError.value = error.message
  } finally {
    if (sequence === listSequence) listLoading.value = false
  }
}

async function loadAdminResources() {
  listLoading.value = true
  pageError.value = ''
  try {
    adminResources.value = await api('/admin/resources')
  } catch (error) {
    pageError.value = error.message
  } finally {
    listLoading.value = false
  }
}

function navigate(target) {
  view.value = target
  pageError.value = ''
  bookingPage.value = 1
  bookingStatus.value = ''
  if (target === 'explore') loadResources()
  if (target === 'bookings' && user.value) loadBookings()
  if (target === 'admin' && isAdmin.value) {
    if (adminTab.value === 'resources') loadAdminResources()
    else loadBookings()
  }
}

function reserve(resource) {
  if (loading.value || !currentWindow.value) return
  selectedWindow.value = { ...currentWindow.value }
  if (!user.value) {
    pendingResource.value = resource
    authOpen.value = true
  } else selectedResource.value = resource
}

function authenticated(currentUser) {
  user.value = currentUser
  authOpen.value = false
  notify(`欢迎回来，${currentUser.displayName}`)
  if (pendingResource.value) {
    selectedResource.value = pendingResource.value
    pendingResource.value = null
  }
  if (view.value === 'bookings') loadBookings()
}

async function logout() {
  try {
    await api('/auth/logout', { method: 'POST' })
  } catch (error) {
    if (error.status !== 401) {
      notify(error.message, 'error')
      return
    }
  }
  saveToken(null)
  user.value = null
  bookingResult.value = { items: [], total: 0, page: 1, size: 8 }
  navigate('explore')
  notify('已退出登录')
}

function sessionExpired() {
  user.value = null
  selectedResource.value = null
  actionDialog.value = null
  bookingResult.value = { items: [], total: 0, page: 1, size: 8 }
  if (['admin', 'simulator', 'operations', 'all-usage', 'usage'].includes(view.value))
    view.value = 'explore'
  notify('登录已失效，请重新登录', 'error')
}

function bookingCreated() {
  selectedResource.value = null
  navigate('bookings')
  notify('预约已提交，请在保留时间内完成模拟支付')
}

function askAction(kind, item) {
  dialogError.value = ''
  actionDialog.value = { kind, item }
}

async function confirmAction() {
  const { kind, item } = actionDialog.value
  actionBusy.value = true
  dialogError.value = ''
  try {
    if (kind === 'toggle') {
      await api(`/admin/resources/${item.id}/status`, {
        method: 'PATCH',
        body: { enabled: !item.enabled },
      })
      await loadAdminResources()
      notify(item.enabled ? '资源已停用，已有预约保持有效' : '资源已启用')
    } else {
      const result = await api(`/bookings/${item.id}/${kind}`, { method: 'POST' })
      await loadBookings()
      notify(
        result.status === 'EXPIRED'
          ? '预约已过期，请重新选择车位'
          : kind === 'pay'
            ? '模拟支付成功，车位已为你预留'
            : result.paymentStatus === 'REFUNDED'
              ? '预约已取消，已模拟全额退款'
              : '预约已取消',
        result.status === 'EXPIRED' ? 'error' : 'success',
      )
    }
    actionDialog.value = null
  } catch (error) {
    dialogError.value = error.message
  } finally {
    actionBusy.value = false
  }
}

function changeBookingFilter(status) {
  bookingStatus.value = status
  bookingPage.value = 1
  loadBookings()
}

function changePage(offset) {
  bookingPage.value += offset
  loadBookings()
}

function selectAdminTab(tab) {
  adminTab.value = tab
  bookingPage.value = 1
  if (tab === 'resources') loadAdminResources()
  else loadBookings()
}

function closeAuthDialog() {
  authOpen.value = false
  pendingResource.value = null
}

function closeBookingDialog() {
  selectedResource.value = null
  loadResources()
}

watch(
  () => [filters.parkCode, filters.type, filters.date, filters.start, filters.end],
  () => {
    // Invalidate old availability immediately while inputs change.
    ++resourceSequence
    loading.value = true
    clearTimeout(searchTimer)
    searchTimer = setTimeout(loadResources, 250)
  },
)

onMounted(async () => {
  window.addEventListener('parking-session-expired', sessionExpired)
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, 1000)
  if (getToken()) {
    try {
      user.value = await api('/auth/me')
    } catch (error) {
      saveToken(null)
      notify(error.message, 'error')
    }
  }
  try {
    parks.value = await api('/parks')
  } catch (error) {
    pageError.value = error.message
  }
  await loadResources()
})

onUnmounted(() => {
  clearInterval(clockTimer)
  clearTimeout(searchTimer)
  clearTimeout(toastTimer)
  window.removeEventListener('parking-session-expired', sessionExpired)
})
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <button class="brand" aria-label="泊序首页" @click="navigate('explore')">
        <span class="brand-symbol">
          P
          <span />
        </span>
        <span class="brand-name">
          泊序
          <small>PARK & RECHARGE</small>
        </span>
      </button>
      <p class="nav-label">你的出行空间</p>
      <nav aria-label="主导航">
        <button
          v-for="item in navigation"
          :key="item.id"
          :class="{ active: view === item.id }"
          @click="navigate(item.id)"
        >
          <Icon :name="item.icon" :size="20" />
          <span>{{ item.label }}</span>
          <span v-if="view === item.id" class="nav-active-dot" />
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="eco-note">
          <span class="eco-symbol"><Icon name="leaf" :size="22" /></span>
          <h3>
            少一点寻找，
            <br />
            多一点从容。
          </h3>
          <p>
            提前留好位置
            <br />
            把时间留给更重要的事。
          </p>
          <span class="eco-caption">EVERY ARRIVAL MATTERS</span>
        </div>
        <div class="version-label">
          <i />
          本地演示 · 预约与履约
          <span>V1.0</span>
        </div>
      </div>
    </aside>

    <div class="main-shell">
      <header class="topbar">
        <div class="breadcrumb">
          工作台
          <Icon name="chevron" :size="13" />
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="topbar-right">
          <span class="local-label">
            <i />
            园区预约服务
          </span>
          <template v-if="user">
            <span class="user-avatar">{{ user.displayName.slice(0, 1) }}</span>
            <span class="user-name">
              {{ user.displayName }}
              <small v-if="isAdmin">管理员</small>
            </span>
            <button class="icon-button" aria-label="退出登录" @click="logout">
              <Icon name="logout" :size="18" />
            </button>
          </template>
          <button v-else class="button primary small" @click="authOpen = true">
            <Icon name="user" :size="16" />
            登录 / 注册
          </button>
        </div>
      </header>

      <main>
        <template v-if="view === 'explore'">
          <section class="hero">
            <div class="hero-copy">
              <span class="hero-kicker">
                <i />
                预约你的下一次抵达
              </span>
              <h1>
                好位置，
                <br />
                <span>提前一步。</span>
              </h1>
              <p>
                停车与充电车位，一处轻松预约。
                <br />
                从找位到抵达，让行程更有把握。
              </p>
              <div class="hero-benefits">
                <span>
                  <Icon name="check" :size="14" />
                  按时段预留
                </span>
                <span>
                  <Icon name="check" :size="14" />
                  费用透明
                </span>
                <span>
                  <Icon name="check" :size="14" />
                  开始前可取消
                </span>
              </div>
            </div>
            <div class="hero-visual" aria-hidden="true">
              <div class="visual-ring ring-one" />
              <div class="visual-ring ring-two" />
              <div class="mini-location">
                <span>
                  <Icon name="pin" :size="14" />
                  云栖 · 科创园
                </span>
                <i />
              </div>
              <div class="parking-scene">
                <div class="scene-road">
                  <i />
                  <i />
                  <i />
                  <i />
                </div>
                <div class="scene-bay bay-one"><span>P</span></div>
                <div class="scene-bay bay-two">
                  <div class="drawn-car">
                    <div class="car-windshield" />
                    <div class="car-roof" />
                    <div class="car-back" />
                    <i class="wheel wheel-one" />
                    <i class="wheel wheel-two" />
                    <i class="wheel wheel-three" />
                    <i class="wheel wheel-four" />
                  </div>
                </div>
                <div class="scene-bay bay-three">
                  <Icon name="bolt" :size="37" />
                  <div class="charging-pillar"><i /></div>
                </div>
              </div>
              <div class="floating-confirm">
                <span><Icon name="check" :size="16" /></span>
                <div>
                  位置已为你留好
                  <small>YOUR SPACE IS READY</small>
                </div>
              </div>
              <span class="visual-caption">PARK SMART. ARRIVE CALM.</span>
            </div>
          </section>

          <section class="search-panel" aria-label="选择预约条件">
            <div class="search-field park-field">
              <label for="park-filter">
                <Icon name="pin" :size="15" />
                目的园区
              </label>
              <select id="park-filter" v-model="filters.parkCode">
                <option value="">全部园区</option>
                <option v-for="park in parks" :key="park.code" :value="park.code">
                  {{ park.name }}
                </option>
              </select>
            </div>
            <div class="search-field">
              <label for="date-filter">
                <Icon name="calendar" :size="15" />
                预约日期
              </label>
              <input
                id="date-filter"
                v-model="filters.date"
                type="date"
                :min="localDate()"
                :max="maxDate"
              />
            </div>
            <div class="search-field time-field">
              <label for="start-filter">
                <Icon name="clock" :size="15" />
                预约时段
              </label>
              <div>
                <select id="start-filter" v-model="filters.start" aria-label="开始时间">
                  <option v-for="time in timeOptions" :key="time">{{ time }}</option>
                </select>
                <span>—</span>
                <select v-model="filters.end" aria-label="结束时间">
                  <option v-for="time in timeOptions" :key="time">{{ time }}</option>
                </select>
              </div>
            </div>
            <button class="button primary search-button" :disabled="loading" @click="loadResources">
              <Icon :name="loading ? 'refresh' : 'search'" :size="18" />
              {{ loading ? '查询中' : '查找车位' }}
            </button>
          </section>

          <section class="resource-section">
            <div class="section-heading">
              <div>
                <h2>
                  找到你的专属位置
                  <span class="heading-dot">.</span>
                </h2>
                <p v-if="currentWindow">
                  {{ dateTime(currentWindow.startAt) }} — {{ dateTime(currentWindow.endAt) }}
                  <span class="dot-divider">·</span>
                  共 {{ availableCount }} 个可约
                </p>
                <p v-else>选择时间，查看真实可用状态</p>
              </div>
              <div class="resource-count">
                <Icon name="bolt" :size="15" />
                <span>{{ chargingCount }} 个充电车位</span>
              </div>
            </div>
            <div class="filter-row">
              <div class="segmented">
                <button :class="{ selected: !filters.type }" @click="filters.type = ''">
                  全部车位
                </button>
                <button
                  :class="{ selected: filters.type === 'PARKING' }"
                  @click="filters.type = 'PARKING'"
                >
                  <Icon name="car" :size="16" />
                  普通停车
                </button>
                <button
                  :class="{ selected: filters.type === 'CHARGING' }"
                  @click="filters.type = 'CHARGING'"
                >
                  <Icon name="bolt" :size="16" />
                  充电车位
                </button>
              </div>
              <label class="available-toggle">
                <input v-model="filters.availableOnly" type="checkbox" />
                <span />
                仅看可预约
              </label>
            </div>
            <div v-if="pageError" class="error-banner" role="alert">
              <Icon name="info" :size="18" />
              {{ pageError }}
              <button @click="loadResources">重新查询</button>
            </div>
            <div v-if="loading" class="resource-grid" aria-label="正在加载车位">
              <div v-for="i in 6" :key="i" class="skeleton-card">
                <span />
                <span />
                <span />
              </div>
            </div>
            <div v-else-if="visibleResources.length" class="resource-grid">
              <ResourceCard
                v-for="resource in visibleResources"
                :key="resource.id"
                :resource="resource"
                :hours="hours"
                :loading="loading"
                @reserve="reserve"
              />
            </div>
            <div v-else-if="!pageError" class="empty-state">
              <Icon name="car" :size="40" />
              <h3>这个时段暂时没有合适的车位</h3>
              <p>试试更换园区、时间，或关闭“仅看可预约”。</p>
            </div>
          </section>
          <div class="bottom-tip">
            <Icon name="shield" :size="18" />
            <p>提交预约后短暂预留，完成模拟支付即确认位置。离场后按实际停车时长和电量模拟结算。</p>
            <button @click="navigate('guide')">
              了解预约规则
              <Icon name="arrow" :size="14" />
            </button>
          </div>
        </template>

        <template v-else-if="view === 'bookings' || view === 'admin'">
          <section class="page-heading">
            <p class="eyebrow">
              {{ view === 'admin' ? 'OPERATIONS, IN ONE PLACE' : 'YOUR PLANS, ALL TOGETHER' }}
            </p>
            <h1>{{ view === 'admin' ? '让每一次预约井然有序。' : '下一段行程，已安排。' }}</h1>
            <p>
              {{
                view === 'admin'
                  ? '查看资源状态与平台预约。停用车位不改变已有订单。'
                  : '查看预约、完成模拟支付，或在行程变化时提前取消。'
              }}
            </p>
          </section>
          <template v-if="user">
            <div v-if="view === 'admin'" class="admin-tabs">
              <button
                :class="{ active: adminTab === 'resources' }"
                @click="selectAdminTab('resources')"
              >
                资源管理
              </button>
              <button
                :class="{ active: adminTab === 'bookings' }"
                @click="selectAdminTab('bookings')"
              >
                全部预约
              </button>
            </div>
            <div v-if="pageError" class="error-banner" role="alert">
              <Icon name="info" :size="18" />
              {{ pageError }}
            </div>
            <template v-if="view === 'admin' && adminTab === 'resources'">
              <div class="table-panel">
                <div class="table-title">
                  <h2>
                    园区资源
                    <span>{{ adminResources.length }}</span>
                  </h2>
                  <button
                    class="button quiet small"
                    :disabled="listLoading"
                    @click="loadAdminResources"
                  >
                    <Icon name="refresh" :size="15" />
                    刷新
                  </button>
                </div>
                <div class="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>车位编号</th>
                        <th>所属园区</th>
                        <th>类型</th>
                        <th>停车单价</th>
                        <th>状态</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr v-for="item in adminResources" :key="item.id">
                        <td>
                          <strong>{{ item.code }}</strong>
                        </td>
                        <td>{{ item.parkName }}</td>
                        <td>
                          {{ item.type === 'CHARGING' ? `充电 · ${item.powerKw} kW` : '普通停车' }}
                        </td>
                        <td>¥{{ money(item.hourlyRate) }} / 时</td>
                        <td>
                          <span
                            class="status-tag"
                            :class="item.enabled ? 'confirmed' : 'cancelled'"
                          >
                            {{ item.enabled ? '已启用' : '已停用' }}
                          </span>
                        </td>
                        <td>
                          <button class="table-action" @click="askAction('toggle', item)">
                            {{ item.enabled ? '停用' : '启用' }}
                          </button>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="booking-toolbar">
                <div class="status-filters">
                  <button :class="{ active: !bookingStatus }" @click="changeBookingFilter('')">
                    全部预约
                  </button>
                  <button
                    v-for="(label, status) in bookingLabels"
                    :key="status"
                    :class="{ active: bookingStatus === status }"
                    @click="changeBookingFilter(status)"
                  >
                    {{ label }}
                  </button>
                </div>
                <button class="button quiet small" :disabled="listLoading" @click="loadBookings">
                  <Icon name="refresh" :size="15" />
                  刷新
                </button>
              </div>
              <div v-if="listLoading" class="list-loading">正在获取预约记录…</div>
              <div v-else-if="bookingResult.items.length" class="booking-list">
                <BookingCard
                  v-for="booking in bookingResult.items"
                  :key="booking.id"
                  :booking="booking"
                  :now="now"
                  :readonly="view === 'admin'"
                  :busy="actionBusy"
                  @pay="askAction('pay', $event)"
                  @cancel="askAction('cancel', $event)"
                />
              </div>
              <div v-else class="empty-state">
                <Icon name="calendar" :size="44" />
                <h3>这里还没有预约</h3>
                <p>下一次出发，先为自己留一个位置。</p>
                <button class="button primary" @click="navigate('explore')">
                  去找车位
                  <Icon name="arrow" :size="16" />
                </button>
              </div>
              <div v-if="bookingResult.total > bookingResult.size" class="pagination">
                <span>共 {{ bookingResult.total }} 条 · 第 {{ bookingPage }} 页</span>
                <button
                  class="button quiet small"
                  :disabled="bookingPage === 1 || listLoading"
                  @click="changePage(-1)"
                >
                  上一页
                </button>
                <button
                  class="button quiet small"
                  :disabled="bookingPage * bookingResult.size >= bookingResult.total || listLoading"
                  @click="changePage(1)"
                >
                  下一页
                </button>
              </div>
            </template>
          </template>
          <div v-else class="empty-state">
            <Icon name="user" :size="44" />
            <h3>登录，查看你的行程</h3>
            <p>所有预约和模拟支付记录，都在这里。</p>
            <button class="button primary" @click="authOpen = true">
              登录 / 注册
              <Icon name="arrow" :size="16" />
            </button>
          </div>
        </template>

        <UsageView v-else-if="view === 'usage' && user" :key="user.id" />
        <UsageView v-else-if="view === 'all-usage' && isAdmin" :key="`all-${user.id}`" all />
        <SimulatorView v-else-if="view === 'simulator' && isAdmin" />
        <OperationsView v-else-if="view === 'operations' && isAdmin" />
        <GuideView v-else-if="view === 'guide'" />
        <footer class="page-footer">
          <span>泊序 PARK & RECHARGE</span>
          <span>
            让每一次抵达，从容有序。
            <i>© 2026</i>
          </span>
        </footer>
      </main>
    </div>

    <AuthDialog v-if="authOpen" @close="closeAuthDialog" @authenticated="authenticated" />
    <BookingDialog
      v-if="selectedResource"
      :resource="selectedResource"
      :window="selectedWindow"
      @close="closeBookingDialog"
      @created="bookingCreated"
    />
    <DialogShell
      v-if="actionDialog"
      :title="
        actionDialog.kind === 'pay'
          ? '确认模拟支付'
          : actionDialog.kind === 'cancel'
            ? '确认取消这次预约？'
            : actionDialog.item.enabled
              ? '停用这个资源？'
              : '重新启用这个资源？'
      "
      :busy="actionBusy"
      eyebrow="ONE MORE THING"
      @close="actionDialog = null"
    >
      <template v-if="actionDialog.kind === 'toggle'">
        <p class="modal-description">
          {{ actionDialog.item.parkName }} · {{ actionDialog.item.code }}
        </p>
        <p class="notice-box">
          {{
            actionDialog.item.enabled
              ? '停用后将不接受新预约。已有预约不变，仍可支付和取消。'
              : '启用后，该资源将重新出现在可预约列表中。'
          }}
        </p>
      </template>
      <template v-else>
        <p class="modal-description">
          {{ actionDialog.item.parkName }} · {{ actionDialog.item.resourceCode }}
          <br />
          {{ dateTime(actionDialog.item.startAt) }} — {{ dateTime(actionDialog.item.endAt) }}
        </p>
        <div class="booking-total">
          <span>
            {{
              actionDialog.kind === 'pay'
                ? '模拟支付金额'
                : actionDialog.item.paymentStatus === 'SUCCESS'
                  ? '模拟退回金额'
                  : '取消金额'
            }}
          </span>
          <strong>¥{{ money(actionDialog.item.amount) }}</strong>
        </div>
        <p class="notice-box">
          <Icon name="info" :size="18" />
          <span>
            {{
              actionDialog.kind === 'pay'
                ? '仅为本地模拟，不连接任何支付渠道，不会扣款。'
                : actionDialog.item.paymentStatus === 'SUCCESS'
                  ? '预约取消后释放时段，并在本地模拟全额退款。'
                  : '取消后释放预留时段，你可以重新选择其他位置。'
            }}
          </span>
        </p>
      </template>
      <p v-if="dialogError" class="inline-error" role="alert">{{ dialogError }}</p>
      <div class="dialog-actions">
        <button class="button quiet" :disabled="actionBusy" @click="actionDialog = null">
          再想想
        </button>
        <button class="button primary" :disabled="actionBusy" @click="confirmAction">
          {{
            actionBusy
              ? '正在处理…'
              : actionDialog.kind === 'pay'
                ? '确认模拟支付'
                : actionDialog.kind === 'cancel'
                  ? '确认取消'
                  : '确认操作'
          }}
        </button>
      </div>
    </DialogShell>
    <Transition name="toast">
      <div v-if="toast" class="toast-message" :class="toast.type" role="status">
        <Icon :name="toast.type === 'error' ? 'info' : 'check'" :size="19" />
        {{ toast.message }}
        <button aria-label="关闭提示" @click="toast = null">
          <Icon name="close" :size="15" />
        </button>
      </div>
    </Transition>
  </div>
</template>
