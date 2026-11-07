<script setup>
defineProps({ page: Number, size: Number, total: Number, busy: Boolean })
defineEmits(['change'])
</script>
<template>
  <div class="pagination">
    <span>共 {{ total }} 条 · 第 {{ page }} 页</span>
    <button
      class="button quiet small"
      :disabled="page <= 1 || busy"
      @click="$emit('change', page - 1)"
    >
      上一页
    </button>
    <button
      class="button quiet small"
      :disabled="page * size >= total || busy"
      @click="$emit('change', page + 1)"
    >
      下一页
    </button>
  </div>
</template>
