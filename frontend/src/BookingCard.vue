<script setup>
import { computed } from 'vue'
import Icon from './Icon.vue'
import { bookingLabels, dateTime, money } from './format'
const props = defineProps({ booking: Object, now: Number, readonly: Boolean, busy: Boolean })
defineEmits(['pay', 'cancel'])
const remaining = computed(() =>
  Math.max(0, Math.ceil((new Date(props.booking.holdUntil).getTime() - props.now) / 1000)),
)
const timedOut = computed(() => props.booking.status === 'PENDING_PAYMENT' && remaining.value === 0)
const status = computed(() => (timedOut.value ? 'EXPIRED' : props.booking.status))
const countdown = computed(
  () => `${Math.floor(remaining.value / 60)}:${String(remaining.value % 60).padStart(2, '0')}`,
)
</script>
<template>
  <article class="booking-card">
    <div class="booking-head">
      <span class="booking-number">{{ booking.bookingNo }}</span>
      <span class="status-tag" :class="status.toLowerCase()">{{ bookingLabels[status] }}</span>
    </div>
    <div class="booking-content">
      <span class="resource-icon">
        <Icon :name="booking.type === 'CHARGING' ? 'bolt' : 'car'" :size="27" />
      </span>
      <div class="booking-main">
        <h3>
          {{ booking.parkName }}
          <span>{{ booking.resourceCode }}</span>
        </h3>
        <p>
          {{ dateTime(booking.startAt) }} — {{ dateTime(booking.endAt) }}
          <span class="dot-divider">·</span>
          {{ booking.plateNumber }}
        </p>
        <p v-if="booking.paymentStatus === 'REFUNDED'" class="refund-text">
          已模拟全额退款 ¥{{ money(booking.amount) }}
        </p>
        <p v-else-if="booking.paymentStatus === 'SETTLED'" class="refund-text">
          预付款已计入实际账单，请到“会话与账单”查看模拟补退。
        </p>
        <p v-else-if="status === 'PENDING_PAYMENT'" class="countdown">
          <Icon name="clock" :size="13" />
          请在 {{ countdown }} 内支付
        </p>
      </div>
      <strong class="booking-price">¥{{ money(booking.amount) }}</strong>
    </div>
    <div
      v-if="
        !readonly && (status === 'PENDING_PAYMENT' || (booking.canCancel && status === 'CONFIRMED'))
      "
      class="booking-actions"
    >
      <button
        v-if="booking.canCancel"
        class="button quiet small"
        :disabled="busy"
        @click="$emit('cancel', booking)"
      >
        取消预约
      </button>
      <button
        v-if="status === 'PENDING_PAYMENT'"
        class="button primary small"
        :disabled="busy"
        @click="$emit('pay', booking)"
      >
        模拟支付
        <Icon name="arrow" :size="15" />
      </button>
    </div>
  </article>
</template>
