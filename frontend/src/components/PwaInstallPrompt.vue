<template>
  <div v-if="showInstallPrompt" class="pwa-install-prompt">
    <div class="prompt-content">
      <div class="prompt-icon">📱</div>
      <div class="prompt-text">
        <h3>安装 VisionPlay</h3>
        <p>将应用添加到主屏幕，获得更好的体验</p>
      </div>
      <div class="prompt-actions">
        <button class="btn-secondary" @click="dismissPrompt">稍后再说</button>
        <button class="btn-primary" @click="installPWA">立即安装</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const showInstallPrompt = ref(false)
let deferredPrompt = null

function handleBeforeInstallPrompt(event) {
  event.preventDefault()
  deferredPrompt = event
  showInstallPrompt.value = true
}

function installPWA() {
  if (!deferredPrompt) return
  
  deferredPrompt.prompt()
  
  deferredPrompt.userChoice.then((choiceResult) => {
    if (choiceResult.outcome === 'accepted') {
      console.log('用户接受了安装提示')
    } else {
      console.log('用户拒绝了安装提示')
    }
    deferredPrompt = null
    showInstallPrompt.value = false
  })
}

function dismissPrompt() {
  showInstallPrompt.value = false
  deferredPrompt = null
}

onMounted(() => {
  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})

onUnmounted(() => {
  window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})
</script>

<style scoped>
.pwa-install-prompt {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  padding: 20px;
  max-width: 400px;
  width: 90%;
  z-index: 1000;
  animation: slideUp 0.3s ease-out;
}

.prompt-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.prompt-icon {
  font-size: 32px;
  flex-shrink: 0;
}

.prompt-text {
  flex: 1;
}

.prompt-text h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.prompt-text p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.4;
}

.prompt-actions {
  display: flex;
  gap: 12px;
  margin-left: 12px;
}

.btn-primary,
.btn-secondary {
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.btn-secondary {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid #e5e7eb;
}

.btn-secondary:hover {
  background: #e5e7eb;
  transform: translateY(-1px);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@media (max-width: 768px) {
  .pwa-install-prompt {
    width: 95%;
    padding: 16px;
  }
  
  .prompt-content {
    flex-direction: column;
    text-align: center;
  }
  
  .prompt-actions {
    margin-left: 0;
    width: 100%;
    justify-content: center;
  }
}
</style>