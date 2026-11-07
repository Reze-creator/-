<script setup>
import Icon from './Icon.vue'
</script>
<template>
  <section class="page-heading">
    <p class="eyebrow">A LITTLE HELP, A BETTER ARRIVAL</p>
    <h1>每一步，都清楚一点。</h1>
    <p>从选位到抵达，这里是你需要知道的预约规则。</p>
  </section>
  <div class="guide-grid">
    <article class="guide-card">
      <span>01</span>
      <Icon name="search" :size="28" />
      <h3>选择园区与时间</h3>
      <p>
        按整点或半点选择开始和结束时间，可提前 30 天预约，单次时长最长 24 小时。界面支持同日选择。
      </p>
    </article>
    <article class="guide-card">
      <span>02</span>
      <Icon name="car" :size="28" />
      <h3>找到适合的位置</h3>
      <p>
        普通车位适合停车；充电车位支持模拟充电。先预付预约停车费，离场再按实际停车和累计电量模拟补退。
      </p>
    </article>
    <article class="guide-card">
      <span>03</span>
      <Icon name="wallet" :size="28" />
      <h3>五分钟内完成支付</h3>
      <p>
        提交后车位短暂保留，未支付自动过期。支付完全模拟，不会从任何账户扣款，也不需要填写银行卡。
      </p>
    </article>
    <article class="guide-card">
      <span>04</span>
      <Icon name="refresh" :size="28" />
      <h3>行程变化，提前取消</h3>
      <p>
        开始前且未入场可取消，已支付则模拟全额退款。入场后到“会话与账单”查看履约情况，离场生成最终账单。
      </p>
    </article>
  </div>
  <div class="guide-banner">
    <Icon name="info" :size="24" />
    <div>
      <h3>预约、模拟履约与运营都可以本地体验</h3>
      <p>
        管理员可发送正常、重复、倒序设备事件，查看会话、账单和异常工单。全部设备和资金操作都是模拟，AI
        模块暂未实现。
      </p>
    </div>
  </div>
</template>
