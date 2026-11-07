<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import Icon from './Icon.vue'
const props = defineProps({ title: String, eyebrow: String, busy: Boolean })
const emit = defineEmits(['close'])
const panel = ref(null)
let previousFocus
function close() {
  if (!props.busy) emit('close')
}
function onKey(event) {
  if (event.key === 'Escape') close()
  if (event.key !== 'Tab') return
  const elements = [...panel.value.querySelectorAll('button, input, select, a[href]')].filter(
    (element) => !element.disabled,
  )
  const first = elements[0],
    last = elements.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
onMounted(() => {
  previousFocus = document.activeElement
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', onKey)
  panel.value.querySelector('input, button')?.focus()
})
onUnmounted(() => {
  document.body.style.overflow = ''
  document.removeEventListener('keydown', onKey)
  previousFocus?.focus()
})
</script>
<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click.self="close">
      <section ref="panel" class="modal-panel" role="dialog" aria-modal="true" :aria-label="title">
        <button
          class="icon-button modal-close"
          :disabled="busy"
          aria-label="关闭弹窗"
          @click="close"
        >
          <Icon name="close" />
        </button>
        <p class="eyebrow">{{ eyebrow || 'PARKING, MADE SIMPLE' }}</p>
        <h2>{{ title }}</h2>
        <slot />
      </section>
    </div>
  </Teleport>
</template>
