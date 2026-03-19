<template>
  <div class="modal-overlay" @click.self="close">
    <transition name="bili-toast">
      <div v-if="toast.show" class="bili-toast" :class="toast.type">
        <span class="toast-icon">✓</span>
        <span class="toast-text">{{ toast.message }}</span>
      </div>
    </transition>
    <div class="modal">
      <div class="modal-header">
        <h2>注册</h2>
        <button class="btn-close" @click="close">×</button>
      </div>
      <form class="form" @submit.prevent="handleSubmit">
        <div class="field">
          <input v-model="form.username" type="text" placeholder="用户名" required minlength="2" />
        </div>
        <div class="field">
          <input v-model="form.password" type="password" placeholder="密码" required minlength="6" />
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="btn-submit" :disabled="loading">注册</button>
        <p class="tip">
          已有账号？<a href="#" @click.prevent="switchToLogin">立即登录</a>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const toast = reactive({ show: false, message: '', type: 'success', timer: null })
const form = reactive({ username: '', password: '' })

watch(() => props.modelValue, (v) => {
  if (v) {
    error.value = ''
    form.username = ''
    form.password = ''
    hideToast()
  }
})

function close() {
  const q = { ...router.currentRoute.value.query }
  delete q.register
  router.replace({ query: q })
  emit('update:modelValue', false)
}

function switchToLogin() {
  close()
  const q = { ...router.currentRoute.value.query, login: '1' }
  delete q.register
  router.push({ path: router.currentRoute.value.path, query: q })
}

function showToast(message, type = 'success') {
  toast.message = message
  toast.type = type
  toast.show = true
  if (toast.timer) clearTimeout(toast.timer)
  toast.timer = setTimeout(() => {
    toast.show = false
    toast.timer = null
  }, 1600)
}

function hideToast() {
  if (toast.timer) clearTimeout(toast.timer)
  toast.show = false
  toast.timer = null
}

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    await userStore.register(form)
    showToast('注册成功，请登录', 'success')
    close()
    emit('update:modelValue', false)
    setTimeout(() => {
      router.push({ path: router.currentRoute.value.path, query: { ...router.currentRoute.value.query, login: '1' } })
    }, 500)
  } catch (e) {
    error.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,.5);
}

.bili-toast {
  position: fixed;
  top: 92px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 999px;
  font-size: 14px;
  background: #ffffff;
  color: #18191c;
  box-shadow: 0 8px 24px rgba(0,0,0,.12);
  border: 1px solid rgba(0,0,0,.06);
  z-index: 1200;
}

.bili-toast.success {
  border-color: rgba(251,114,153,.35);
}

.toast-icon {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(251,114,153,.15);
  color: var(--bili-pink-dark);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
}

.bili-toast .toast-text {
  font-weight: 500;
}

.bili-toast-enter-active,
.bili-toast-leave-active {
  transition: all .2s ease;
}

.bili-toast-enter-from,
.bili-toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -8px);
}

.modal {
  width: 400px;
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 16px 48px rgba(0,0,0,.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.modal-header h2 {
  font-size: 22px;
  font-weight: 600;
}

.btn-close {
  width: 32px;
  height: 32px;
  font-size: 24px;
  line-height: 1;
  color: #999;
  background: none;
  border-radius: 6px;
}

.btn-close:hover {
  background: var(--bg-gray);
  color: var(--text-primary);
}

.field {
  margin-bottom: 16px;
}

.field input {
  width: 100%;
  padding: 12px 16px;
  font-size: 15px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  outline: none;
  transition: border-color 0.2s;
}

.field input:focus {
  border-color: var(--bili-pink);
}

.error {
  color: #f56c6c;
  font-size: 14px;
  margin-bottom: 12px;
}

.btn-submit {
  width: 100%;
  padding: 12px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 8px;
  margin-top: 8px;
}

.btn-submit:hover:not(:disabled) {
  background: var(--bili-pink-hover);
}

.btn-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.tip {
  margin-top: 16px;
  font-size: 14px;
  color: var(--text-secondary);
  text-align: center;
}

.tip a {
  color: var(--bili-pink);
}

.tip a:hover {
  text-decoration: underline;
}
</style>
