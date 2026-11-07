<script setup>
import { computed, ref } from 'vue'
import { api } from './api'
import { dateTime, money } from './format'
import DialogShell from './DialogShell.vue'
import Icon from './Icon.vue'
const props = defineProps({ resource: Object, window: Object })
const emit = defineEmits(['close', 'created'])
const plate = ref('')
const busy = ref(false)
const error = ref('')
// Keep the same id across network retries of this form submission.
const requestId = crypto.randomUUID()
const estimated = computed(() => money(props.resource.hourlyRate * props.window.hours))
async function submit() {
  busy.value = true
  error.value = ''
  try {
    const result = await api('/bookings', {
      method: 'POST',
      body: {
        requestId,
        resourceId: props.resource.id,
        plateNumber: plate.value.trim().toUpperCase(),
        startAt: props.window.startAt,
        endAt: props.window.endAt,
      },
    })
    emit('created', result)
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <DialogShell
    title="为这次抵达留个位置"
    eyebrow="CONFIRM YOUR RESERVATION"
    :busy="busy"
    @close="emit('close')"
  >
    <div class="reservation-summary">
      <div class="summary-resource">
        <span class="resource-icon">
          <Icon :name="resource.type === 'CHARGING' ? 'bolt' : 'car'" :size="26" />
        </span>
        <div>
          <strong>
            {{ resource.code }} · {{ resource.type === 'CHARGING' ? '充电车位' : '普通车位' }}
          </strong>
          <p>{{ resource.parkName }}</p>
        </div>
      </div>
      <div class="summary-line">
        <span>预约时间</span>
        <strong>{{ dateTime(window.startAt) }} — {{ dateTime(window.endAt) }}</strong>
      </div>
      <div class="summary-line">
        <span>预约时长</span>
        <strong>{{ window.hours }} 小时</strong>
      </div>
      <div class="summary-line">
        <span>停车单价</span>
        <strong>¥{{ money(resource.hourlyRate) }} / 小时</strong>
      </div>
    </div>
    <form class="stack-form" @submit.prevent="submit">
      <label>
        车牌号码
        <input
          v-model="plate"
          required
          minlength="5"
          maxlength="10"
          pattern="[\u4e00-\u9fffA-Za-z0-9]{5,10}"
          placeholder="例如：浙A12345"
          autocomplete="off"
        />
      </label>
      <p class="help-text">请填写模拟车牌；当前为本地演示，不连接真实车辆。</p>
      <div class="booking-total">
        <span>预约停车费</span>
        <strong>¥{{ estimated }}</strong>
      </div>
      <p class="notice-box">
        <Icon name="clock" :size="18" />
        <span>
          提交后最多保留 5
          分钟，请及时模拟支付。此处预付预约停车费，实际使用结束后按停车时长和用电量模拟补退差额。
        </span>
      </p>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="button primary full" :disabled="busy">
        {{ busy ? '正在预占车位…' : '提交预约' }}
        <Icon name="arrow" :size="18" />
      </button>
    </form>
  </DialogShell>
</template>
