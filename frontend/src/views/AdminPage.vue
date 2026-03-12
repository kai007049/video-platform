<template>
  <div class="admin-page">
    <h2>管理后台</h2>
    <div class="tabs">
      <button :class="{ active: tab === 'videos' }" @click="tab = 'videos'">视频管理</button>
      <button :class="{ active: tab === 'users' }" @click="tab = 'users'">用户管理</button>
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
              <button
                class="btn-sm"
                :class="{ active: v.isRecommended }"
                @click="toggleRecommend(v)"
              >
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
import { ref, watch, onMounted } from 'vue'
import { getAdminVideos, getAdminUsers, setVideoRecommend } from '../api/admin'

const tab = ref('videos')
const videos = ref([])
const users = ref([])
const videoPage = ref(1)
const userPage = ref(1)
const videoPages = ref(1)
const userPages = ref(1)
const error = ref('')

async function loadVideos() {
  try {
    error.value = ''
    const res = await getAdminVideos(videoPage.value, 10)
    videos.value = res.records || []
    videoPages.value = res.pages || 1
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

async function loadUsers() {
  try {
    error.value = ''
    const res = await getAdminUsers(userPage.value, 10)
    users.value = res.records || []
    userPages.value = res.pages || 1
  } catch (e) {
    error.value = e.message || '加载失败'
  }
}

async function toggleRecommend(v) {
  try {
    await setVideoRecommend(v.id, !v.isRecommended)
    v.isRecommended = !v.isRecommended
  } catch (e) {
    error.value = e.message || '操作失败'
  }
}

function prevVideos() {
  if (videoPage.value > 1) {
    videoPage.value--
    loadVideos()
  }
}

function nextVideos() {
  if (videoPage.value < videoPages.value) {
    videoPage.value++
    loadVideos()
  }
}

function prevUsers() {
  if (userPage.value > 1) {
    userPage.value--
    loadUsers()
  }
}

function nextUsers() {
  if (userPage.value < userPages.value) {
    userPage.value++
    loadUsers()
  }
}

watch(tab, (t) => {
  if (t === 'videos') loadVideos()
  else loadUsers()
})

onMounted(() => loadVideos())
</script>

<style scoped>
.admin-page {
  max-width: 960px;
  margin: 0 auto;
}

h2 {
  font-size: 24px;
  margin-bottom: 24px;
}

.tabs {
  margin-bottom: 24px;
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
</style>
