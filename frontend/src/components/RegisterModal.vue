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
          <button class="tab" @click="switchToLogin">登录</button>
          <button class="tab active" @click="switchToRegister">注册</button>
        </div>
        <button class="btn-close" @click="close">×</button>
      </div>
      <div class="modal-content">
        <h2 class="modal-title">开启你的旅程</h2>
        <p class="modal-subtitle">加入VisionPlay，探索无限可能</p>
        <form class="form" @submit.prevent="handleSubmit">
          <div class="field">
            <div class="input-wrapper">
              <span class="input-icon">👤</span>
              <input v-model="form.username" type="text" placeholder="用户名" required minlength="2" />
            </div>
          </div>
          <div class="field">
            <div class="input-wrapper">
              <span class="input-icon">✉</span>
              <input v-model="form.email" type="email" placeholder="邮箱地址" required />
            </div>
          </div>
          <div class="field">
            <div class="input-wrapper">
              <span class="input-icon">🔑</span>
              <input v-model="form.password" type="password" placeholder="密码" required minlength="6" />
            </div>
          </div>
          <div class="terms">
            <input type="checkbox" id="terms" v-model="agreedToTerms" required />
            <label for="terms">我已阅读并同意 <a href="#" @click.prevent>服务条款</a></label>
          </div>
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit" class="btn-submit" :disabled="loading">创建账号 →</button>
          <div class="third-party-login">
            <p class="third-party-title">第三方登录</p>
            <div class="third-party-buttons">
              <button type="button" class="third-party-btn">
                <span class="btn-icon">🔒</span>
                <span>GitHub</span>
              </button>
              <button type="button" class="third-party-btn">
                <span class="btn-icon">📱</span>
                <span>手机号</span>
              </button>
            </div>
          </div>
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
const error = ref('')
const agreedToTerms = ref(false)
const toast = reactive({ show: false, message: '', type: 'success', timer: null })
const form = reactive({ username: '', email: '', password: '' })

watch(() => props.modelValue, (v) => {
  if (v) {
    error.value = ''
    form.username = ''
    form.email = ''
    form.password = ''
    agreedToTerms.value = false
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

function switchToRegister() {
  // 已在注册页面，无需操作
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
  if (!agreedToTerms.value) {
    error.value = '请阅读并同意服务条款'
    return
  }
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

.terms {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  color: #64748b;
}

.terms input[type="checkbox"] {
  margin-top: 2px;
  accent-color: #4f46e5;
}

.terms a {
  color: #4f46e5;
  text-decoration: none;
}

.terms a:hover {
  text-decoration: underline;
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
