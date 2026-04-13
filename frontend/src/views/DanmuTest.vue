<template>
  <div class="danmu-test">
    <div class="test-header">
      <h1>弹幕系统性能测试</h1>
      <div class="test-controls">
        <button @click="startTest" :disabled="isTesting" class="btn">开始测试</button>
        <button @click="stopTest" :disabled="!isTesting" class="btn btn-secondary">停止测试</button>
        <button @click="clearDanmus" class="btn btn-secondary">清屏</button>
      </div>
    </div>
    
    <div class="test-container">
      <div class="video-container">
        <video ref="videoEl" class="test-video" controls>
          <source src="https://samplelib.com/lib/preview/mp4/sample-5s.mp4" type="video/mp4">
        </video>
        <div ref="danmuContainer" class="danmu-container"></div>
      </div>
      
      <div class="test-info">
        <h3>测试数据</h3>
        <div class="info-item">
          <span>弹幕数量:</span>
          <span>{{ danmuCount }}</span>
        </div>
        <div class="info-item">
          <span>FPS:</span>
          <span>{{ fps.toFixed(1) }}</span>
        </div>
        <div class="info-item">
          <span>活跃弹幕:</span>
          <span>{{ activeDanmus }}</span>
        </div>
        <div class="info-item">
          <span>轨道数量:</span>
          <span>{{ trackCount }}</span>
        </div>
        
        <h3>测试配置</h3>
        <div class="config-item">
          <label>弹幕速度:</label>
          <input type="range" v-model.number="testSpeed" min="50" max="300" step="10">
          <span>{{ testSpeed }} px/s</span>
        </div>
        <div class="config-item">
          <label>弹幕密度:</label>
          <input type="range" v-model.number="testDensity" min="1" max="10" step="1">
          <span>{{ testDensity }} 条/秒</span>
        </div>
        <div class="config-item">
          <label>轨道数量:</label>
          <input type="range" v-model.number="trackCount" min="4" max="16" step="1">
          <span>{{ trackCount }} 轨</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import DanmuPlayer from '../utils/danmu-player-optimized.js'

const videoEl = ref(null)
const danmuContainer = ref(null)
const danmuPlayer = ref(null)
const isTesting = ref(false)
const danmuCount = ref(0)
const activeDanmus = ref(0)
const trackCount = ref(8)
const testSpeed = ref(140)
const testDensity = ref(5)
const fps = ref(60)

let testInterval = null
let frameCount = 0
let lastFpsUpdate = Date.now()

function startTest() {
  if (isTesting.value) return
  
  isTesting.value = true
  danmuCount.value = 0
  
  if (danmuPlayer.value) {
    danmuPlayer.value.clearScreen()
    danmuPlayer.value.setSpeed(testSpeed.value)
  }
  
  testInterval = setInterval(() => {
    for (let i = 0; i < testDensity.value; i++) {
      createTestDanmu()
    }
  }, 1000)
  
  startFpsMonitor()
}

function stopTest() {
  if (!isTesting.value) return
  
  isTesting.value = false
  if (testInterval) {
    clearInterval(testInterval)
    testInterval = null
  }
  stopFpsMonitor()
}

function clearDanmus() {
  if (danmuPlayer.value) {
    danmuPlayer.value.clearScreen()
  }
  danmuCount.value = 0
}

function createTestDanmu() {
  if (!danmuPlayer.value) return
  
  const messages = [
    '测试弹幕 123',
    '这是一条测试弹幕',
    '性能测试中...',
    '弹幕系统优化',
    '60FPS 流畅运行',
    '轨道防重叠算法',
    '空闲优先策略',
    '视频同步机制',
    '高性能弹幕系统',
    'Vue 3 + Vite'
  ]
  
  const message = messages[Math.floor(Math.random() * messages.length)]
  const danmu = {
    content: message,
    time: videoEl.value.currentTime,
    type: Math.random() > 0.9 ? 'top' : 'scroll',
    color: `#${Math.floor(Math.random()*16777215).toString(16)}`
  }
  
  danmuPlayer.value.addDanmu(danmu)
  danmuCount.value++
}

function startFpsMonitor() {
  frameCount = 0
  lastFpsUpdate = Date.now()
  
  function monitor() {
    frameCount++
    const now = Date.now()
    if (now - lastFpsUpdate >= 1000) {
      fps.value = frameCount / ((now - lastFpsUpdate) / 1000)
      frameCount = 0
      lastFpsUpdate = now
    }
    if (isTesting.value) {
      requestAnimationFrame(monitor)
    }
  }
  
  requestAnimationFrame(monitor)
}

function stopFpsMonitor() {
  // 自动停止
}

function updateActiveDanmus() {
  if (danmuPlayer.value) {
    activeDanmus.value = danmuPlayer.value.activeDanmus.size
  }
  requestAnimationFrame(updateActiveDanmus)
}

onMounted(() => {
  danmuPlayer.value = new DanmuPlayer({
    video: videoEl.value,
    container: danmuContainer.value,
    speed: testSpeed.value,
    trackCount: trackCount.value
  })
  updateActiveDanmus()
})

onUnmounted(() => {
  if (danmuPlayer.value) {
    danmuPlayer.value.destroy()
  }
  if (testInterval) {
    clearInterval(testInterval)
  }
})
</script>

<style scoped>
.danmu-test {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.test-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.test-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #333;
}

.test-controls {
  display: flex;
  gap: 10px;
}

.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  background: #00a1d6;
  color: white;
  font-size: 14px;
  cursor: pointer;
  font-weight: 500;
}

.btn:hover {
  background: #0086b3;
}

.btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  background: #666;
}

.btn-secondary:hover {
  background: #555;
}

.test-container {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
}

.video-container {
  position: relative;
  aspect-ratio: 16/9;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.test-video {
  width: 100%;
  height: 100%;
  display: block;
}

.danmu-container {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.test-info {
  background: #f5f5f5;
  padding: 20px;
  border-radius: 8px;
}

.test-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #333;
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.config-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  font-size: 14px;
}

.config-item label {
  width: 80px;
}

.config-item input {
  flex: 1;
  margin: 0 10px;
}

@media (max-width: 768px) {
  .test-container {
    grid-template-columns: 1fr;
  }
}
</style>