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
        <div class="tabs">
          <button class="tab active" @click="switchToLogin">登录</button>
          <button class="tab" @click="switchToRegister">注册</button>
        </div>
        <button class="btn-close" @click="close">×</button>
      </div>
      <div class="modal-content">
        <h2 class="modal-title">欢迎回来</h2>
        <p class="modal-subtitle">在这里发现属于你的 AI 视频新体验</p>
        <form class="form" @submit.prevent="handleSubmit">
          <div class="field">
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input v-model="form.account" type="text" placeholder="用户名" required />
            </div>
          </div>
          <div class="field">
            <div class="input-wrapper">
              <span class="input-icon">🔑</span>
              <input v-model="form.password" type="password" placeholder="密码" required />
            </div>
          </div>
          <div class="field captcha-field">
            <div class="input-wrapper captcha-input-wrap">
              <span class="input-icon">🔐</span>
              <input v-model="form.captchaValue" type="text" placeholder="验证码" required />
            </div>
            <button type="button" class="captcha-image-btn" @click="refreshCaptcha" :disabled="captchaLoading">
              <img v-if="captchaImage" :src="captchaImage" alt="captcha" class="captcha-image" />
              <span v-else>{{ captchaLoading ? '加载中...' : '获取验证码' }}</span>
            </button>
          </div>
          <div class="forgot-password">
            <a href="#" @click.prevent>忘记密码？</a>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit" class="btn-submit" :disabled="loading || captchaLoading">立即登录 →</button>
        </form>
      </div>
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
const captchaImage = ref('')
const error = ref('')
const toast = reactive({ show: false, message: '', type: 'success', timer: null })
const form = reactive({ account: '', password: '', captchaKey: '', captchaValue: '' })

watch(() => props.modelValue, async (v) => {
  if (v) {
    error.value = ''
    form.account = ''
    form.password = ''
    form.captchaKey = ''
    form.captchaValue = ''
    captchaImage.value = ''
    hideToast()
    await refreshCaptcha()
  }
})

function close() {
  const q = { ...router.currentRoute.value.query }
  delete q.login
  router.replace({ query: q })
  emit('update:modelValue', false)
}

function switchToLogin() {
  // 已在登录页面，无需操作
}

function switchToRegister() {
  close()
  const q = { ...router.currentRoute.value.query, register: '1' }
  delete q.login
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

async function refreshCaptcha() {
  captchaLoading.value = true
  try {
    const data = await userStore.fetchCaptcha()
    form.captchaKey = data?.captchaKey || ''
    form.captchaValue = ''
    captchaImage.value = data?.imageBase64 ? `data:image/png;base64,${data.imageBase64}` : ''
  } catch (e) {
    captchaImage.value = ''
    form.captchaKey = ''
    error.value = e.message || '验证码加载失败'
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
  border-radius: 16px;
  box-shadow: 0 16px 48px rgba(0,0,0,.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.tabs {
  display: flex;
  gap: 8px;
  background: #f8fafc;
  padding: 4px;
  border-radius: 12px;
}

.tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab.active {
  background: #fff;
  color: #1f2937;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.btn-close {
  width: 32px;
  height: 32px;
  font-size: 24px;
  line-height: 1;
  color: #999;
  background: none;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.btn-close:hover {
  background: #f1f5f9;
  color: #1f2937;
}

.modal-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.modal-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0;
}

.modal-subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  width: 100%;
}

.captcha-field {
  display: grid;
  grid-template-columns: 1fr 124px;
  gap: 12px;
  align-items: stretch;
}

.captcha-input-wrap {
  height: 100%;
}

.captcha-image-btn {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
  cursor: pointer;
  overflow: hidden;
  min-height: 52px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 13px;
}

.captcha-image-btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.input-wrapper {
  position: relative;
  width: 100%;
}

.input-icon {
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  font-size: 16px;
}

.field input {
  width: 100%;
  padding: 14px 16px 14px 44px;
  font-size: 15px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  outline: none;
  transition: all 0.3s ease;
  background: #f8fafc;
}

.field input:focus {
  border-color: #4f46e5;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.1);
}

.forgot-password {
  align-self: flex-end;
}

.forgot-password a {
  font-size: 14px;
  color: #64748b;
  text-decoration: none;
  transition: color 0.3s ease;
}

.forgot-password a:hover {
  color: #4f46e5;
}

.error {
  color: #f56c6c;
  font-size: 14px;
  margin: 0;
}

.btn-submit {
  width: 100%;
  padding: 16px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: #1f2937;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.btn-submit:hover:not(:disabled) {
  background: #4f46e5;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.btn-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.third-party-login {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 8px;
}

.third-party-title {
  font-size: 14px;
  color: #94a3b8;
  text-align: center;
  margin: 0;
}

.third-party-buttons {
  display: flex;
  gap: 12px;
}

.third-party-btn {
  flex: 1;
  padding: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #475569;
}

.third-party-btn:hover {
  border-color: #4f46e5;
  box-shadow: 0 2px 8px rgba(79, 70, 229, 0.1);
}

.btn-icon {
  font-size: 16px;
}
</style>
