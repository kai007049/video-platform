<template>
  <div class="pwa-status" v-if="isOnline !== null">
    <div class="status-indicator" :class="{ online: isOnline, offline: !isOnline }">
      <span class="status-dot"></span>
      <span class="status-text">{{ isOnline ? '在线' : '离线' }}</span>
    </div>
    <div class="cache-info" v-if="cacheStats">
      <span class="cache-size">{{ formatCacheSize(cacheStats.size) }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'

const isOnline = ref(null)
const cacheStats = ref(null)

function updateOnlineStatus() {
  isOnline.value = navigator.onLine
}

async function getCacheStats() {
  if ('caches' in window) {
    try {
      const cacheNames = await caches.keys()
      let totalSize = 0
      let totalEntries = 0

      for (const cacheName of cacheNames) {
        const cache = await caches.open(cacheName)
        const keys = await cache.keys()
        totalEntries += keys.length

        // 估算缓存大小（实际实现可能需要更复杂的逻辑）
        totalSize += keys.length * 1024 * 1024 // 粗略估算每个资源1MB
      }

      cacheStats.value = {
        size: totalSize,
        entries: totalEntries
      }
    } catch (error) {
      console.error('获取缓存统计失败:', error)
    }
  }
}

function formatCacheSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

onMounted(() => {
  updateOnlineStatus()
  getCacheStats()
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
  
  // 定期更新缓存统计
  const interval = setInterval(getCacheStats, 60000)
  onUnmounted(() => clearInterval(interval))
})

onUnmounted(() => {
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})
</script>

<style scoped>
.pwa-status {
  position: fixed;
  top: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 999;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s;
}

.status-indicator.online {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.status-indicator.offline {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse 2s infinite;
}

.status-text {
  white-space: nowrap;
}

.cache-info {
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 999px;
  font-size: 12px;
  color: #6b7280;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(8px);
}

.cache-size {
  font-weight: 500;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

@media (max-width: 768px) {
  .pwa-status {
    top: 10px;
    right: 10px;
    flex-direction: column;
    align-items: flex-end;
  }
  
  .status-indicator,
  .cache-info {
    padding: 4px 8px;
    font-size: 11px;
  }
}
</style>