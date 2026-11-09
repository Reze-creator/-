<script setup>
import Icon from './Icon.vue'
import { money } from './format'
defineProps({ resource: Object, hours: Number, loading: Boolean })
defineEmits(['reserve'])
</script>
<template>
  <article
    class="resource-card"
    :class="{ unavailable: !resource.available, charging: resource.type === 'CHARGING' }"
  >
    <div class="resource-top">
      <span class="resource-icon">
        <Icon :name="resource.type === 'CHARGING' ? 'bolt' : 'car'" :size="25" />
      </span>
      <span class="availability" :class="{ busy: !resource.available }">
        <i />
        {{ resource.available ? '所选时段可约' : '所选时段已占用' }}
      </span>
    </div>
    <div class="resource-title">
      <h3>{{ resource.code }}</h3>
      <span>{{ resource.type === 'CHARGING' ? '充电车位' : '普通车位' }}</span>
    </div>
    <p class="resource-park">
      <Icon name="pin" :size="14" />
      {{ resource.parkName }}
      <span v-if="resource.distanceKm != null">
        · 距指定位置 {{ resource.distanceKm.toFixed(2) }} km
      </span>
    </p>
    <div class="resource-tags">
      <span>
        {{
          resource.type === 'CHARGING'
            ? `${resource.powerKw} kW · 直流快充`
            : '标准车位 · 地下停车场'
        }}
      </span>
      <span>可提前预约</span>
    </div>
    <div class="resource-bottom">
      <div>
        <strong>
          <small>¥</small>
          {{ money(resource.hourlyRate).replace('.00', '') }}
        </strong>
        <span class="rate-unit">/ 小时</span>
        <p>所选 {{ hours }} 小时约 ¥{{ money(resource.hourlyRate * hours) }}</p>
      </div>
      <button
        class="reserve-button"
        :disabled="!resource.available || loading"
        :aria-label="`预约 ${resource.code}`"
        @click="$emit('reserve', resource)"
      >
        {{ resource.available ? '预约' : '已占用' }}
        <Icon v-if="resource.available" name="arrow" :size="16" />
      </button>
    </div>
    <p v-if="resource.type === 'CHARGING'" class="charging-note">
      预约先预付停车费，离场按实际停车时长和电量结算
    </p>
  </article>
</template>
