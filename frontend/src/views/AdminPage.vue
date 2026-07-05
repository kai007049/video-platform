<template>
  <div class="admin-page">
    <h2>管理后台</h2>
    <div class="tabs">
      <button :class="{ active: tab === 'ops' }" @click="tab = 'ops'">运维总览</button>
      <button :class="{ active: tab === 'videos' }" @click="tab = 'videos'">视频管理</button>
      <button :class="{ active: tab === 'users' }" @click="tab = 'users'">用户管理</button>
    </div>

    <div v-if="tab === 'ops'" class="section">
      <div class="summary-grid">
        <div class="summary-card">
          <h3>总体状态</h3>
          <div class="summary-state" :class="summary?.status?.toLowerCase()">{{ summary?.status || 'N/A' }}</div>
          <p class="muted">生成时间：{{ formatTime(summary?.generatedAt) }}</p>
          <ul class="alert-list">
            <li v-for="item in summary?.alerts || []" :key="item">{{ item }}</li>
          </ul>
        </div>

        <div class="summary-card">
          <h3>缓存</h3>
          <p>总请求：{{ summary?.cache?.totalRequests ?? 0 }}</p>
          <p>整体命中率：{{ percent(summary?.cache?.overallHitRate) }}</p>
          <p>本地/Redis/DB：{{ summary?.cache?.localHit ?? 0 }} / {{ summary?.cache?.redisHit ?? 0 }} / {{ summary?.cache?.dbLoad ?? 0 }}</p>
        </div>

        <div class="summary-card">
          <h3>MQ</h3>
          <p>Outbox 待处理：{{ summary?.mq?.outboxBacklog ?? 0 }}</p>
          <p>生产者死信：{{ summary?.mq?.producerBacklog ?? 0 }}</p>
          <p>消费者失败：{{ summary?.mq?.consumerBacklog ?? 0 }}</p>
        </div>

        <div class="summary-card">
          <h3>接口</h3>
          <p>总请求：{{ summary?.http?.totalRequests ?? 0 }}</p>
          <p>错误率：{{ percent(summary?.http?.errorRate) }}</p>
          <p>P95：{{ summary?.http?.p95LatencyMs ?? 0 }} ms</p>
        </div>
      </div>

      <div class="list-card">
        <div class="list-header">
          <h3>生产者死信</h3>
          <button class="btn-sm" :disabled="replayingProducer || selectedProducerIds.length === 0" @click="replaySelectedProducer">重放选中</button>
        </div>
        <table class="table">
          <thead>
            <tr>
              <th></th>
              <th>事件</th>
              <th>Topic</th>
              <th>状态</th>
              <th>重试</th>
              <th>原因</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in producerDeadLetters" :key="item.recordId || item.eventId">
              <td><input type="checkbox" :value="item.eventId" v-model="selectedProducerIds" /></td>
              <td>{{ item.eventId }}</td>
              <td>{{ item.topic }}</td>
              <td>{{ item.status }}</td>
              <td>{{ item.replayAttempts ?? 0 }}</td>
              <td class="ellipsis">{{ item.reason || item.lastReplayError || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button @click="prevProducer" :disabled="producerPage <= 1">上一页</button>
          <span>{{ producerPage }} / {{ producerPages }}</span>
          <button @click="nextProducer" :disabled="producerPage >= producerPages">下一页</button>
        </div>
      </div>

      <div class="list-card">
        <div class="list-header">
          <h3>消费者失败</h3>
          <button class="btn-sm" :disabled="replayingConsumer || selectedConsumerIds.length === 0" @click="replaySelectedConsumer">重放选中</button>
        </div>
        <table class="table">
          <thead>
            <tr>
              <th></th>
              <th>记录</th>
              <th>Topic</th>
              <th>组</th>
              <th>状态</th>
              <th>错误</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in consumerFailures" :key="item.recordId">
              <td><input type="checkbox" :value="item.recordId" v-model="selectedConsumerIds" /></td>
              <td>{{ item.recordId }}</td>
              <td>{{ item.topic }}</td>
              <td>{{ item.consumerGroup }}</td>
              <td>{{ item.status }}</td>
              <td class="ellipsis">{{ item.errorMessage || item.lastReplayError || '-' }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button @click="prevConsumer" :disabled="consumerPage <= 1">上一页</button>
          <span>{{ consumerPage }} / {{ consumerPages }}</span>
          <button @click="nextConsumer" :disabled="consumerPage >= consumerPages">下一页</button>
        </div>
      </div>
    </div>

    <div v-if="tab === 'videos'" class="section">
      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>播放</th>
            <th>点赞</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in videos" :key="v.id">
            <td>{{ v.id }}</td>
            <td>{{ v.title }}</td>
            <td>{{ v.playCount }}</td>
            <td>{{ v.likeCount }}</td>
            <td>
              <button class="btn-sm" :class="{ active: v.isRecommended }" @click="toggleRecommend(v)">
                {{ v.isRecommended ? '取消推荐' : '推荐' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <button @click="prevVideos" :disabled="videoPage <= 1">上一页</button>
        <span>{{ videoPage }} / {{ videoPages }}</span>
        <button @click="nextVideos" :disabled="videoPage >= videoPages">下一页</button>
      </div>
    </div>

    <div v-if="tab === 'users'" class="section">
      <div class="default-avatar">
        <h3>默认头像</h3>
        <p>上传到 avatar/default/ 目录，用于新用户随机头像。</p>
        <label class="upload-btn" :class="{ disabled: uploadingAvatar }">
          <input type="file" accept="image/*" @change="onDefaultAvatarChange" />
          {{ uploadingAvatar ? '上传中...' : '上传默认头像' }}
        </label>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>用户名</th>
            <th>管理员</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td>{{ u.isAdmin ? '是' : '否' }}</td>
          </tr>
        </tbody>
      </table>
      <div class="pagination">
        <button @click="prevUsers" :disabled="userPage <= 1">上一页</button>
        <span>{{ userPage }} / {{ userPages }}</span>
        <button @click="nextUsers" :disabled="userPage >= userPages">下一页</button>
      </div>
    </div>

    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import {
  getAdminOpsSummary,
  getAdminVideos,
  getAdminUsers,
  listProducerDeadLetters,
  listConsumerFailures,
  replayProducerDeadLetters,
  replayConsumerFailures,
  setVideoRecommend,
  uploadDefaultAvatar
} from '../api/admin'

const tab = ref('ops')
const summary = ref(null)
const producerDeadLetters = ref([])
const consumerFailures = ref([])
const videos = ref([])
const users = ref([])
const producerPage = ref(1)
const consumerPage = ref(1)
const videoPage = ref(1)
const userPage = ref(1)
const producerPages = ref(1)
const consumerPages = ref(1)
const videoPages = ref(1)
const userPages = ref(1)
const error = ref('')
const uploadingAvatar = ref(false)
const replayingProducer = ref(false)
const replayingConsumer = ref(false)
const selectedProducerIds = ref([])
const selectedConsumerIds = ref([])

function percent(value) {
  if (value == null) return '0%'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function loadOps() {
  const [summaryRes, producerRes, consumerRes] = await Promise.all([
    getAdminOpsSummary(),
    listProducerDeadLetters(producerPage.value, 10),
    listConsumerFailures(consumerPage.value, 10)
  ])
  summary.value = summaryRes
  producerDeadLetters.value = producerRes.records || []
  producerPages.value = producerRes.pages || 1
  consumerFailures.value = consumerRes.records || []
  consumerPages.value = consumerRes.pages || 1
}

async function loadVideos() {
  const res = await getAdminVideos(videoPage.value, 10)
  videos.value = res.records || []
  videoPages.value = res.pages || 1
}

async function loadUsers() {
  const res = await getAdminUsers(userPage.value, 10)
  users.value = res.records || []
  userPages.value = res.pages || 1
}

async function toggleRecommend(v) {
  try {
    await setVideoRecommend(v.id, !v.isRecommended)
    v.isRecommended = !v.isRecommended
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

async function onDefaultAvatarChange(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file || uploadingAvatar.value) return
  if (!file.type.startsWith('image/')) {
    error.value = '请选择图片文件'
    return
  }
  try {
    uploadingAvatar.value = true
    const formData = new FormData()
    formData.append('avatar', file)
    await uploadDefaultAvatar(formData)
    error.value = ''
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    uploadingAvatar.value = false
  }
}

async function replaySelectedProducer() {
  try {
    replayingProducer.value = true
    await replayProducerDeadLetters([...selectedProducerIds.value])
    selectedProducerIds.value = []
    await loadOps()
  } catch (e) {
    error.value = e.message || '重放失败'
  } finally {
    replayingProducer.value = false
  }
}

async function replaySelectedConsumer() {
  try {
    replayingConsumer.value = true
    await replayConsumerFailures([...selectedConsumerIds.value])
    selectedConsumerIds.value = []
    await loadOps()
  } catch (e) {
    error.value = e.message || '重放失败'
  } finally {
    replayingConsumer.value = false
  }
}

function prevProducer() {
  if (producerPage.value > 1) {
    producerPage.value--
    loadOps().catch(err => { error.value = err.message || '加载失败' })
  }
}

function nextProducer() {
  if (producerPage.value < producerPages.value) {
    producerPage.value++
    loadOps().catch(err => { error.value = err.message || '加载失败' })
  }
}

function prevConsumer() {
  if (consumerPage.value > 1) {
    consumerPage.value--
    loadOps().catch(err => { error.value = err.message || '加载失败' })
  }
}

function nextConsumer() {
  if (consumerPage.value < consumerPages.value) {
    consumerPage.value++
    loadOps().catch(err => { error.value = err.message || '加载失败' })
  }
}

function prevVideos() {
  if (videoPage.value > 1) {
    videoPage.value--
    loadVideos().catch(err => { error.value = err.message || '加载失败' })
  }
}

function nextVideos() {
  if (videoPage.value < videoPages.value) {
    videoPage.value++
    loadVideos().catch(err => { error.value = err.message || '加载失败' })
  }
}

function prevUsers() {
  if (userPage.value > 1) {
    userPage.value--
    loadUsers().catch(err => { error.value = err.message || '加载失败' })
  }
}

function nextUsers() {
  if (userPage.value < userPages.value) {
    userPage.value++
    loadUsers().catch(err => { error.value = err.message || '加载失败' })
  }
}

watch(tab, async (t) => {
  try {
    error.value = ''
    if (t === 'ops') await loadOps()
    else if (t === 'videos') await loadVideos()
    else await loadUsers()
  } catch (e) {
    error.value = e.message || '加载失败'
  }
})

onMounted(async () => {
  try {
    await loadOps()
  } catch (e) {
    error.value = e.message || '加载失败'
  }
})
</script>

<style scoped>
.admin-page {
  max-width: 1200px;
  margin: 0 auto;
}

h2 {
  font-size: 24px;
  margin-bottom: 24px;
}

.tabs {
  margin-bottom: 24px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card,
.list-card,
.default-avatar {
  background: #fff;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 16px;
}

.summary-state {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  color: #fff;
  font-weight: 600;
  margin: 8px 0;
}

.summary-state.normal { background: #22c55e; }
.summary-state.warning { background: #f59e0b; }
.summary-state.critical { background: #ef4444; }

.alert-list {
  margin: 8px 0 0;
  padding-left: 18px;
  color: #b45309;
  font-size: 13px;
}

.list-card + .list-card {
  margin-top: 16px;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.muted {
  color: #6b7280;
  font-size: 13px;
}

.ellipsis {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.default-avatar {
  background: #fff7f7;
  border-color: #f5c2c7;
  margin-bottom: 18px;
}

.default-avatar h3 {
  margin-bottom: 6px;
  color: #c0392b;
}

.default-avatar p {
  margin-bottom: 12px;
  color: #b35d5d;
  font-size: 13px;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid #e74c3c;
  color: #e74c3c;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
}

.upload-btn input {
  display: none;
}

.upload-btn.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tabs button {
  padding: 8px 20px;
  margin-right: 8px;
  font-size: 14px;
  border: 1px solid var(--border-color);
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
}

.tabs button.active {
  background: var(--bili-pink);
  color: #fff;
  border-color: var(--bili-pink);
}

.table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.table th, .table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid var(--border-color);
}

.table th {
  background: var(--bg-gray);
  font-weight: 600;
}

.btn-sm {
  padding: 4px 12px;
  font-size: 12px;
  border: 1px solid var(--bili-pink);
  color: var(--bili-pink);
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
}

.btn-sm.active {
  background: var(--bili-pink);
  color: #fff;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.pagination button {
  padding: 6px 16px;
  font-size: 14px;
  border: 1px solid var(--border-color);
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.error {
  color: #f56c6c;
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
