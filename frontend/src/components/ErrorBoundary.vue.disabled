<template>
  <div v-if="hasError" class="error-boundary">
    <div class="error-container">
      <div class="error-icon">⚠️</div>
      <h2 class="error-title">应用发生错误</h2>
      <p class="error-description">很抱歉，应用在运行过程中遇到了问题</p>
      
      <div class="error-details" v-if="errorInfo">
        <details class="error-details-toggle">
          <summary class="error-details-summary">查看错误详情</summary>
          <div class="error-details-content">
            <pre class="error-stack">{{ errorInfo.stack }}</pre>
          </div>
        </details>
      </div>
      
      <div class="error-actions">
        <button class="btn-primary" @click="handleReload">
          一键重载
        </button>
        <button class="btn-secondary" @click="handleReset">
          清除错误
        </button>
      </div>
      
      <div class="error-trace" v-if="errorTrace.length > 0">
        <h3 class="error-trace-title">错误追溯</h3>
        <div class="error-trace-list">
          <div 
            v-for="(trace, index) in errorTrace" 
            :key="index"
            class="error-trace-item"
          >
            <span class="trace-time">{{ formatTime(trace.timestamp) }}</span>
            <span class="trace-message">{{ trace.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
  <slot v-else></slot>
</template>

<script setup>
import { ref, onErrorCaptured, onMounted, onUnmounted } from 'vue'

const hasError = ref(false)
const error = ref(null)
const errorInfo = ref(null)
const errorTrace = ref([])

function formatTime(timestamp) {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function handleReload() {
  window.location.reload()
}

function handleReset() {
  hasError.value = false
  error.value = null
  errorInfo.value = null
}

function addErrorTrace(message) {
  errorTrace.value.push({
    timestamp: Date.now(),
    message
  })
  
  // 只保留最近10条错误记录
  if (errorTrace.value.length > 10) {
    errorTrace.value.shift()
  }
  
  // 存储到localStorage，以便刷新后查看
  try {
    localStorage.setItem('errorTrace', JSON.stringify(errorTrace.value))
  } catch (e) {
    console.error('Failed to store error trace:', e)
  }
}

function onError(err, instance, info) {
  hasError.value = true
  error.value = err
  errorInfo.value = {
    message: err.message,
    stack: err.stack,
    componentName: instance?.$options?.name || 'Unknown Component',
    info
  }
  
  // 添加到错误追溯
  addErrorTrace(`${err.message} (${instance?.$options?.name || 'Unknown Component'})`)
  
  // 控制台打印错误信息
  console.error('Error captured by ErrorBoundary:', err)
  console.error('Error info:', info)
  
  // 阻止错误继续向上传播
  return false
}

// 注册错误捕获钩子
onErrorCaptured(onError)

// 监听全局错误
function handleGlobalError(event) {
  addErrorTrace(`Global error: ${event.error?.message || 'Unknown error'}`)
  console.error('Global error:', event.error)
}

// 监听未处理的Promise rejection
function handleUnhandledRejection(event) {
  addErrorTrace(`Unhandled promise rejection: ${event.reason?.message || 'Unknown reason'}`)
  console.error('Unhandled promise rejection:', event.reason)
}

onMounted(() => {
  // 加载之前的错误记录
  try {
    const storedTrace = localStorage.getItem('errorTrace')
    if (storedTrace) {
      errorTrace.value = JSON.parse(storedTrace)
    }
  } catch (e) {
    console.error('Failed to load error trace:', e)
  }
  
  // 添加全局错误监听
  window.addEventListener('error', handleGlobalError)
  window.addEventListener('unhandledrejection', handleUnhandledRejection)
})

onUnmounted(() => {
  // 移除全局错误监听
  window.removeEventListener('error', handleGlobalError)
  window.removeEventListener('unhandledrejection', handleUnhandledRejection)
})
</script>

<style scoped>
.error-boundary {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
}

.error-container {
  background: white;
  border-radius: 24px;
  padding: 40px;
  max-width: 600px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  text-align: center;
  animation: slideIn 0.3s ease-out;
}

.error-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.error-title {
  margin: 0 0 12px 0;
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}

.error-description {
  margin: 0 0 24px 0;
  font-size: 16px;
  color: #6b7280;
  line-height: 1.5;
}

.error-details {
  margin-bottom: 24px;
  text-align: left;
}

.error-details-toggle {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.error-details-summary {
  padding: 12px 16px;
  background: #f8fafc;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  list-style: none;
  transition: background 0.3s;
}

.error-details-summary:hover {
  background: #f1f5f9;
}

.error-details-summary::marker {
  display: none;
}

.error-details-content {
  padding: 16px;
  background: #ffffff;
  border-top: 1px solid #e5e7eb;
}

.error-stack {
  margin: 0;
  font-size: 12px;
  line-height: 1.4;
  color: #4b5563;
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  max-height: 200px;
  overflow-y: auto;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 24px;
}

.btn-primary,
.btn-secondary {
  padding: 12px 24px;
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
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.btn-secondary {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid #e5e7eb;
}

.btn-secondary:hover {
  background: #e5e7eb;
  transform: translateY(-2px);
}

.error-trace {
  border-top: 1px solid #e5e7eb;
  padding-top: 20px;
  text-align: left;
}

.error-trace-title {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: #374151;
}

.error-trace-list {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
  max-height: 150px;
  overflow-y: auto;
}

.error-trace-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #e5e7eb;
  font-size: 12px;
}

.error-trace-item:last-child {
  border-bottom: none;
}

.trace-time {
  color: #94a3b8;
  font-weight: 500;
  margin-right: 12px;
  white-space: nowrap;
}

.trace-message {
  flex: 1;
  color: #4b5563;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 768px) {
  .error-container {
    padding: 32px 24px;
  }
  
  .error-title {
    font-size: 20px;
  }
  
  .error-description {
    font-size: 14px;
  }
  
  .error-actions {
    flex-direction: column;
  }
  
  .btn-primary,
  .btn-secondary {
    width: 100%;
  }
}
</style>