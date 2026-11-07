<script setup>
import { reactive, ref } from 'vue'
import { api, saveToken } from './api'
import DialogShell from './DialogShell.vue'
import Icon from './Icon.vue'
const emit = defineEmits(['close', 'authenticated'])
const registering = ref(false)
const busy = ref(false)
const error = ref('')
const form = reactive({ username: '', password: '', displayName: '' })
function useDemo(admin = false) {
  form.username = admin ? 'admin' : 'demo'
  form.password = admin ? 'Admin@123' : 'Demo@123'
}
function toggleMode() {
  registering.value = !registering.value
  error.value = ''
}
async function submit() {
  busy.value = true
  error.value = ''
  try {
    const body = { username: form.username, password: form.password }
    if (registering.value) body.displayName = form.displayName
    const result = await api(registering.value ? '/auth/register' : '/auth/login', {
      method: 'POST',
      body,
    })
    saveToken(result.token)
    emit('authenticated', result.user)
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <DialogShell
    :title="registering ? '创建你的泊序账号' : '欢迎回来，从容出发'"
    eyebrow="YOUR NEXT ARRIVAL"
    :busy="busy"
    @close="emit('close')"
  >
    <p class="modal-description">
      {{
        registering
          ? '注册后即可预约园区内的停车与充电车位。'
          : '登录后管理预约，让下一次抵达更有把握。'
      }}
    </p>
    <form class="stack-form" @submit.prevent="submit">
      <label v-if="registering">
        昵称
        <input
          v-model="form.displayName"
          autocomplete="nickname"
          required
          maxlength="40"
          placeholder="怎么称呼你"
        />
      </label>
      <label>
        用户名
        <input
          v-model="form.username"
          autocomplete="username"
          required
          minlength="3"
          maxlength="32"
          pattern="[a-zA-Z0-9_]{3,32}"
          placeholder="3–32 位字母、数字或下划线"
        />
      </label>
      <label>
        密码
        <input
          v-model="form.password"
          type="password"
          :autocomplete="registering ? 'new-password' : 'current-password'"
          required
          minlength="8"
          maxlength="64"
          placeholder="至少 8 个字符"
        />
      </label>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
      <button class="button primary full" :disabled="busy">
        {{ busy ? '正在处理…' : registering ? '注册并登录' : '登录泊序' }}
        <Icon name="arrow" :size="18" />
      </button>
    </form>
    <div v-if="!registering" class="demo-accounts">
      <span>本地演示账号</span>
      <button @click="useDemo(false)">填入车主账号</button>
      <button @click="useDemo(true)">填入管理员</button>
    </div>
    <p class="auth-switch">
      {{ registering ? '已有账号？' : '第一次来？' }}
      <button @click="toggleMode">
        {{ registering ? '直接登录' : '创建账号' }}
      </button>
    </p>
  </DialogShell>
</template>
