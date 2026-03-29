<template>
  <div class="creator-dashboard">
    <!-- 顶部横幅 -->
    <div class="banner">
      <div class="banner-bg"></div>
    </div>
    
    <!-- 用户信息区域 -->
    <div class="user-section" v-if="userInfo">
      <div class="user-wrapper">
        <div class="avatar-box">
          <img :src="resolveAvatar(userInfo.avatar)" class="avatar" alt="用户头像" @error="onAvatarError" />
        </div>
        <div class="user-main">
          <div class="user-top">
            <div class="name-box">
              <h2 class="username">{{ userInfo.username }}</h2>
              <span class="level-badge">Lv.6</span>
              <span class="verify-badge">大会员</span>
            </div>
          </div>
          <div class="user-coins">
            <span>B币: 0</span>
            <span>硬币: 93</span>
          </div>
          <div class="user-stats">
            <div class="stat-item">
              <span class="stat-value">1</span>
              <span class="stat-label">动态</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-value">10</span>
              <span class="stat-label">关注</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item">
              <span class="stat-value">0</span>
              <span class="stat-label">粉丝</span>
            </div>
            <div class="stat-divider"></div>
            <div class="stat-item space-btn">
              <span class="stat-label">空间 &gt;</span>
            </div>
          </div>
          <div class="user-sign">
            <span>{{ userSign || '这个人很神秘，什么都没有写' }}</span>
            <button class="btn-modify" @click="openModifySign">修改</button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 选项卡区域 -->
    <div class="tabs-section">
      <div class="tabs-wrapper">
        <button
          v-for="item in tabs"
          :key="item.key"
          :class="['tab-item', { active: activeTab === item.key }]"
          @click="switchTab(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
    
    <!-- 内容区域 -->
    <div class="content-section">
      <div v-if="loading" class="loading">加载中...</div>
      <div v-else class="tab-content">
        <div v-if="currentList.length === 0" class="empty-state">
          <div class="empty-illustration">
            <img src="https://s1.hdslb.com/bfs/static/jinkela/space/assets/nodata.png" alt="empty" />
          </div>
          <div class="empty-text">今天真寂寞，如往常~</div>
        </div>

        <!-- 视频相关列表 -->
        <div v-else-if="['works', 'liked', 'favorite', 'history'].includes(activeTab)" class="video-grid">
          <div v-for="video in currentList" :key="video.id" class="video-card">
            <div class="cover-wrap" @click="goVideo(video.id)">
              <img :src="resolveCover(video)" :alt="video.title" class="cover" @error="onCoverError" />
              <span class="duration">{{ formatDuration(video.durationSeconds) }}</span>
            </div>
            <div class="card-info">
              <div class="title" :title="video.title" @click="goVideo(video.id)">{{ video.title }}</div>
              <div class="meta">
                <span>▶ {{ formatCount(video.playCount) }}</span>
                <span>💬 {{ formatCount(video.commentCount) }}</span>
                <span>👍 {{ formatCount(video.likeCount) }}</span>
              </div>
              <div class="actions" v-if="activeTab === 'works'">
                <div class="more-options" @click.stop="showVideoOptions(video.id, $event)">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <div v-if="videoOptions.id === video.id" class="options-menu">
                    <button @click.stop="goVideo(video.id)">查看详情</button>
                    <button @click.stop="confirmDelete(video.id)" class="delete-btn">删除记录</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 关注/粉丝列表 -->
        <div v-else class="user-list">
          <div v-for="user in currentList" :key="user.id" class="user-card">
            <img :src="resolveAvatar(user.avatar)" class="user-avatar" alt="" @error="onAvatarError" />
            <div class="user-info">
              <div class="user-name">{{ user.username }}</div>
              <div class="user-meta">
                <span v-if="user.followed">已关注</span>
                <span v-else>未关注</span>
              </div>
            </div>
            <div class="user-actions">
              <button class="btn-message" @click="goMessage(user)">私信</button>
              <button class="btn-profile" @click="router.push(`/user/${user.id}`)">主页</button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 修改名言对话框 -->
    <div v-if="showModifySignDialog" class="sign-dialog-overlay" @click="showModifySignDialog = false">
      <div class="sign-dialog" @click.stop>
        <div class="dialog-header">
          <h3>修改名言</h3>
          <button class="dialog-close" @click="showModifySignDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <textarea v-model="newSign" placeholder="请输入你的名言" rows="3"></textarea>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showModifySignDialog = false">取消</button>
          <button class="btn-save" @click="saveModifySign">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCreatorStats } from '../api/user'
import {
  getCreatorVideos,
  getLikedVideos,
  getFavoriteVideos,
  getHistoryVideos,
  deleteVideo
} from '../api/video'
import { getFollowingList, getFanList } from '../api/follow'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const stats = ref(null)
const loading = ref(true)
const activeTab = ref('works')
// 模拟视频数据
const mockVideos = [
  {
    id: 1,
    title: '从夯到锐！锐评2026年计算机学生就业形势',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 2265,
    playCount: 123456,
    commentCount: 1234,
    likeCount: 5678
  },
  {
    id: 2,
    title: '26年计算机学生就业形势分析',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 1800,
    playCount: 98765,
    commentCount: 987,
    likeCount: 4321
  },
  {
    id: 3,
    title: '当前前程的岗位千万別碰！不然就即失...',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 1500,
    playCount: 65432,
    commentCount: 765,
    likeCount: 3210
  },
  {
    id: 4,
    title: '26年计算机专业就业前景分析',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 2000,
    playCount: 43210,
    commentCount: 543,
    likeCount: 2109
  },
  {
    id: 5,
    title: '计算机专业学生如何提高就业竞争力',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 1800,
    playCount: 32109,
    commentCount: 432,
    likeCount: 1098
  },
  {
    id: 6,
    title: '2026年IT行业招聘趋势分析',
    cover: 'https://i0.hdslb.com/bfs/archive/8a2718d1c7081c990c436b02d357a3704684751e.jpg',
    durationSeconds: 2100,
    playCount: 21098,
    commentCount: 321,
    likeCount: 987
  }
]

const listMap = ref({
  works: mockVideos,
  liked: mockVideos.slice(0, 4),
  favorite: mockVideos.slice(0, 3),
  history: mockVideos.slice(0, 5),
  following: [],
  fans: []
})
const followingCount = ref(0)

const tabs = [
  { key: 'works', label: '我的作品' },
  { key: 'liked', label: '点赞记录' },
  { key: 'favorite', label: '收藏记录' },
  { key: 'history', label: '历史观看' },
  { key: 'following', label: '关注列表' },
  { key: 'fans', label: '粉丝列表' }
]

const defaultCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href

// 名言相关
const userSign = ref(userStore.userInfo?.sign || '')
const showModifySignDialog = ref(false)
const newSign = ref('')

// 视频选项菜单
const videoOptions = ref({ id: null, style: {} })

const currentList = computed(() => listMap.value[activeTab.value] || [])
const userInfo = computed(() => userStore.userInfo)

// 打开修改名言对话框
function openModifySign() {
  newSign.value = userSign.value || ''
  showModifySignDialog.value = true
}

// 保存修改的名言
function saveModifySign() {
  userSign.value = newSign.value
  showModifySignDialog.value = false
  // 这里可以添加保存到服务器的逻辑
  console.log('修改后的名言:', userSign.value)
}

// 显示视频选项菜单
function showVideoOptions(videoId, event) {
  console.log('showVideoOptions called with videoId:', videoId)
  // 计算菜单位置
  const actionsElement = event.currentTarget.closest('.actions')
  const cardRect = event.currentTarget.closest('.video-card').getBoundingClientRect()
  
  videoOptions.value = {
    id: videoId,
    style: {
      position: 'absolute',
      top: '100%',
      right: '0',
      marginTop: '5px'
    }
  }
  console.log('videoOptions:', videoOptions.value)
}

// 点击页面其他地方关闭选项菜单
function closeVideoOptions(e) {
  // 检查点击的目标是否在选项菜单或小圆点按钮内
  const moreOptionsElements = document.querySelectorAll('.more-options')
  const optionsMenuElements = document.querySelectorAll('.options-menu')
  let isClickInside = false
  
  moreOptionsElements.forEach(element => {
    if (element.contains(e.target)) {
      isClickInside = true
    }
  })
  
  optionsMenuElements.forEach(element => {
    if (element.contains(e.target)) {
      isClickInside = true
    }
  })
  
  // 如果点击的是外部，关闭选项菜单
  if (!isClickInside) {
    videoOptions.value.id = null
  }
}

async function loadStats() {
  try {
    const data = await getCreatorStats()
    stats.value = data || {
      totalPlayCount: 1234567,
      totalLikeCount: 123456,
      videoCount: 123,
      fanCount: 12345
    }
  } catch (e) {
    console.error(e)
    stats.value = {
      totalPlayCount: 1234567,
      totalLikeCount: 123456,
      videoCount: 123,
      fanCount: 12345
    }
  }
}

async function loadFollowingCount() {
  try {
    const uid = userStore.userInfo?.id
    if (!uid) {
      followingCount.value = 1234
      return
    }
    const followingList = await getFollowingList(uid)
    followingCount.value = followingList?.length || 1234
  } catch (e) {
    console.error(e)
    followingCount.value = 1234
  }
}

async function loadList(key) {
  if (key === 'works') {
    listMap.value.works = (await getCreatorVideos()).records || []
  } else if (key === 'liked') {
    listMap.value.liked = (await getLikedVideos()).records || []
  } else if (key === 'favorite') {
    listMap.value.favorite = (await getFavoriteVideos()).records || []
  } else if (key === 'history') {
    listMap.value.history = (await getHistoryVideos()).records || []
  } else if (key === 'following') {
    listMap.value.following = await getFollowingListUser()
  } else if (key === 'fans') {
    listMap.value.fans = await getFanListUser()
  }
}

async function getFollowingListUser() {
  const uid = userStore.userInfo?.id
  if (!uid) return []
  return await getFollowingList(uid)
}

async function getFanListUser() {
  const uid = userStore.userInfo?.id
  if (!uid) return []
  return await getFanList(uid)
}

async function switchTab(key) {
  activeTab.value = key
  await loadList(key)
}

async function confirmDelete(id) {
  if (!confirm('确认删除该视频吗？')) return
  await deleteVideo(id)
  await loadList('works')
}

async function load() {
  try {
    // 暂时注释掉loadList，使用模拟数据
    await Promise.all([loadStats(), loadFollowingCount()])
  } catch (e) {
    console.error(e)
    // 模拟数据，确保页面能正常显示
    stats.value = {
      totalPlayCount: 1234567,
      totalLikeCount: 123456,
      videoCount: 123,
      fanCount: 12345
    }
    followingCount.value = 1234
  } finally {
    loading.value = false
  }
}

function goMessage(user) {
  router.push({
    path: '/message',
    query: {
      targetId: user.id,
      targetName: user.username
    }
  })
}

function goVideo(id) {
  router.push(`/video/${id}`)
}

function formatCount(n) {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return String(n)
}

function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function resolveCover(video) {
  if (video.previewUrl) return video.previewUrl
  if (video.coverUrl) {
    return `/api/file/cover?url=${encodeURIComponent(video.coverUrl)}`
  }
  return defaultCover
}

function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}

function onAvatarError(event) {
  event.target.src = avatarPlaceholder
}

function onCoverError(event) {
  event.target.src = defaultCover
}

onMounted(async () => {
  // 强制模拟登录状态，确保用户信息存在
  userStore.mockLogin()
  // 加载页面数据
  await load()
  // 监听点击事件，关闭选项菜单
  document.addEventListener('click', closeVideoOptions)
})

onUnmounted(() => {
  document.removeEventListener('click', closeVideoOptions)
})
</script>

<style scoped>
.creator-dashboard {
  min-height: calc(100vh - 60px);
  background: #f4f4f4;
}

/* 顶部横幅 */
.banner {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.banner-bg {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #ff9a9e 0%, #fecfef 50%, #fecfef 100%);
  position: relative;
}

.banner-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url('https://s1.hdslb.com/bfs/static/jinkela/space/assets/top-banner.jpg');
  background-size: cover;
  background-position: center;
  opacity: 0.8;
}

/* 用户信息区域 */
.user-section {
  background: #fff;
  padding-bottom: 20px;
}

.user-wrapper {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  align-items: flex-start;
  position: relative;
  top: -40px;
}

.avatar-box {
  flex-shrink: 0;
  margin-right: 20px;
}

.avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid #fff;
  background: #f4f4f4;
}

.user-main {
  flex: 1;
  padding-top: 40px;
}

.user-top {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.name-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  font-size: 20px;
  font-weight: 600;
  color: #18191c;
  margin: 0;
}

.level-badge {
  padding: 2px 6px;
  background: linear-gradient(135deg, #fb7299, #ff92af);
  color: #fff;
  border-radius: 4px;
  font-size: 11px;
}

.verify-badge {
  padding: 2px 6px;
  background: #fb7299;
  color: #fff;
  border-radius: 4px;
  font-size: 11px;
}

.user-coins {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #9499a0;
  margin-bottom: 8px;
}

.user-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #18191c;
}

.stat-label {
  font-size: 12px;
  color: #9499a0;
}

.stat-divider {
  width: 1px;
  height: 20px;
  background: #e3e5e7;
}

.space-btn {
  cursor: pointer;
}

.space-btn .stat-label {
  color: #18191c;
  font-weight: 500;
}

.user-sign {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #61666d;
  margin-bottom: 16px;
}

.btn-modify {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  background: #f0f0f0;
  border: 1px solid #e0e0e0;
  color: #333;
  transition: all 0.2s;
}

.btn-modify:hover {
  background: #e0e0e0;
}

/* 修改名言对话框样式 */
.sign-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.sign-dialog {
  background: #fff;
  border-radius: 8px;
  width: 400px;
  max-width: 90vw;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.dialog-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #999;
}

.dialog-close:hover {
  color: #333;
}

.dialog-body {
  padding: 16px;
}

.dialog-body textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 14px;
  resize: none;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid #e0e0e0;
}

.btn-cancel, .btn-save {
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.btn-cancel {
  background: #f0f0f0;
  color: #333;
}

.btn-save {
  background: #fb7299;
  color: #fff;
}

.btn-cancel:hover {
  background: #e0e0e0;
}

.btn-save:hover {
  background: #ff85ad;
}

/* 选项卡区域 */
.tabs-section {
  background: #fff;
  border-bottom: 1px solid #e3e5e7;
  margin-top: 12px;
}

.tabs-wrapper {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  gap: 24px;
  padding: 0 20px;
}

.tab-item {
  padding: 14px 0;
  background: none;
  font-size: 15px;
  color: #61666d;
  border: none;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}

.tab-item:hover {
  color: #18191c;
}

.tab-item.active {
  color: #fb7299;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: #fb7299;
}

/* 内容区域 */
.content-section {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px;
}

.loading {
  text-align: center;
  padding: 60px 0;
  color: #9499a0;
}

.empty-state {
  text-align: center;
  padding: 80px 0;
}

.empty-illustration {
  margin-bottom: 20px;
}

.empty-illustration img {
  width: 160px;
  height: auto;
}

.empty-text {
  color: #9499a0;
  font-size: 14px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.video-card {
  cursor: pointer;
  border-radius: 4px;
  overflow: visible;
  background: #fff;
  transition: all 0.2s;
  transform-origin: center;
  position: relative;
}

.video-card:hover {
  transform: translateY(-2px) scale(1.03);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 16/9;
  background: #f4f5f7;
  overflow: hidden;
}

.cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.duration {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 2px 6px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 12px;
  border-radius: 2px;
}

.card-info {
  padding: 8px;
}

.title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  height: 36px;
  overflow: hidden;
  margin-bottom: 8px;
  color: #18191c;
}

.meta {
  display: flex;
  gap: 8px;
  color: #9499a0;
  font-size: 11px;
  margin-bottom: 6px;
}

.actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
  position: relative;
  width: 100%;
  padding-right: 8px;
}

.more-options {
  display: flex;
  flex-direction: column;
  gap: 2px;
  cursor: pointer;
  padding: 4px;
  border-radius: 2px;
  transition: background 0.2s;
  position: relative;
  align-self: flex-end;
}

.more-options:hover {
  background: rgba(0, 0, 0, 0.05);
}

.dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #9499a0;
}

.options-menu {
  position: absolute;
  bottom: 100%;
  right: 0;
  margin-bottom: 5px;
  background: #fff;
  border: 1px solid #e3e5e7;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 10000;
  min-width: 120px;
  padding: 8px 0;
  display: block !important;
}

.options-menu button {
  display: block;
  width: 100%;
  padding: 8px 16px;
  text-align: left;
  border: none;
  background: none;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.options-menu button:hover {
  background: #ffb7c5;
}

.options-menu .delete-btn {
  color: #000 !important;
}

.options-menu button:first-child {
  border-bottom: 1px solid #e3e5e7;
  border-radius: 4px 4px 0 0;
}

.options-menu button:last-child {
  border-radius: 0 0 4px 4px;
}

.options-menu button:last-child:hover {
  background: #fff0f4;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #e3e5e7;
  background: #fff;
  transition: all 0.2s;
}

.user-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  margin-right: 16px;
}

.user-info {
  flex: 1;
}

.user-name {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 4px;
  color: #18191c;
}

.user-meta {
  font-size: 14px;
  color: #9499a0;
}

.user-actions {
  display: flex;
  gap: 8px;
}

.btn-message,
.btn-profile {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 14px;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-message {
  background: #fb7299;
  color: #fff;
}

.btn-message:hover {
  background: #ff85ad;
}

.btn-profile {
  background: #fff;
  border: 1px solid #e3e5e7;
  color: #61666d;
}

.btn-profile:hover {
  background: #f5f5f5;
}

@media (max-width: 1024px) {
  .stats-wrapper {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .video-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
  
  .tabs-wrapper {
    gap: 16px;
  }
  
  .tab-item {
    padding: 10px 16px;
    font-size: 14px;
  }
}

@media (max-width: 768px) {
  .user-wrapper {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
  
  .avatar-box {
    margin-right: 0;
    margin-bottom: 16px;
  }
  
  .user-main {
    padding-top: 0;
  }
  
  .user-top {
    justify-content: center;
  }
  
  .user-stats {
    justify-content: center;
  }
  
  .video-grid {
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  }
  
  .tabs-wrapper {
    gap: 12px;
  }
}
</style>
