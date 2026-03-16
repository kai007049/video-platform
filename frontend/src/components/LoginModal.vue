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
        <h2>登录</h2>
        <button class="btn-close" @click="close">×</button>
      </div>
      <form class="form" @submit.prevent="handleSubmit">
        <div class="field">
          <input v-model="form.account" type="text" placeholder="用户名 / 手机号 / 邮箱" required />
        </div>
        <div class="field">
          <input v-model="form.password" type="password" placeholder="密码" required />
        </div>
        <div class="field captcha-row">
          <input v-model="form.captchaValue" type="text" placeholder="验证码" required />
          <button type="button" class="btn-captcha" @click="refreshCaptcha" :disabled="captchaLoading">
            <img v-if="captcha.imageBase64" :src="captcha.imageBase64" alt="验证码" />
            <span v-else>获取验证码</span>
          </button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="btn-submit" :disabled="loading">登录</button>
        <p class="tip">
          还没有账号？<a href="#" @click.prevent="switchToRegister">立即注册</a>
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
const captchaLoading = ref(false)
const error = ref('')
const toast = reactive({ show: false, message: '', type: 'success', timer: null })
const form = reactive({ account: '', password: '', captchaKey: '', captchaValue: '' })
const captcha = reactive({ imageBase64: '' })

watch(() => props.modelValue, (v) => {
  if (v) {
    error.value = ''
    form.account = ''
    form.password = ''
    form.captchaKey = ''
    form.captchaValue = ''
    captcha.imageBase64 = ''
    hideToast()
    refreshCaptcha()
  }
})

function close() {
  const q = { ...router.currentRoute.value.query }
  delete q.login
  router.replace({ query: q })
  emit('update:modelValue', false)
}

function switchToRegister() {
  close()
  router.push({ path: router.currentRoute.value.path, query: { ...router.currentRoute.value.query, register: '1' } })
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

async function refreshCaptcha() {
  if (captchaLoading.value) return
  captchaLoading.value = true
  try {
    const data = await userStore.fetchCaptcha()
    form.captchaKey = data.captchaKey
    captcha.imageBase64 = `data:image/png;base64,${data.imageBase64}`
  } catch (e) {
    error.value = e.message || '验证码获取失败'
  } finally {
    captchaLoading.value = false
  }
}

async function handleSubmit() {
  error.value = ''
  loading.value = true
  try {
    await userStore.login(form)
    showToast('登录成功', 'success')
    setTimeout(() => {
      close()
      emit('update:modelValue', false)
    }, 600)
  } catch (e) {
    error.value = e.message || '登录失败'
    await refreshCaptcha()
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

.captcha-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-row input {
  flex: 1;
}

.btn-captcha {
  width: 120px;
  height: 44px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 0;
}

.btn-captcha img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.btn-captcha:disabled {
  opacity: 0.7;
  cursor: not-allowed;
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
