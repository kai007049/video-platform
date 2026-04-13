<template>
  <div class="upload-page">
    <!-- 页面标题区 -->
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon">➕</div>
        <div>
          <h1 class="page-title">发布投稿</h1>
          <p class="page-subtitle">上传你的视频，分享给更多人</p>
        </div>
      </div>
      <div class="header-right">
        <div v-if="videoFile" class="file-badge">
          <span class="badge-dot"></span>
          已选择视频
        </div>
      </div>
    </div>

    <form class="upload-layout" @submit.prevent="submit">
      <!-- 左侧：媒体上传区 -->
      <div class="left-panel">
        <!-- 视频上传卡片 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">🎬</span>
            <span class="card-title">视频文件</span>
          </div>
          <div
            class="video-drop-zone"
            :class="{ dragging: isVideoDragging, 'has-file': !!videoFile }"
            @click="videoInput?.click()"
            @dragover.prevent="onDragOver"
            @dragleave.prevent="onDragLeave"
            @drop="onDropVideo"
          >
            <input ref="videoInput" type="file" accept="video/*" hidden @change="onVideoChange" />
            <div v-if="!videoFile" class="drop-idle">
              <div class="drop-circle">
                <span class="drop-plus">+</span>
              </div>
              <p class="drop-main">点击或拖拽视频到此处</p>
              <p class="drop-sub">支持 MP4、AVI、MOV 等常见格式</p>
            </div>
            <div v-else class="drop-selected">
              <div class="file-icon-wrap">🎥</div>
              <div class="file-info">
                <p class="file-name">{{ videoFile.name }}</p>
                <p class="file-size">{{ prettySize(videoFile.size) }}</p>
              </div>
              <button type="button" class="file-remove" @click.stop="videoFile = null">✕</button>
            </div>
          </div>
        </div>

        <!-- 封面上传卡片 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">🖼️</span>
            <span class="card-title">封面图片</span>
            <span class="card-tip">建议 16:9</span>
          </div>
          <div
            class="cover-drop-zone"
            :class="{ 'has-cover': !!coverFile }"
            @click="coverInput?.click()"
          >
            <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverChange" />
            <div v-if="!coverFile" class="cover-idle">
              <div class="cover-icon">📷</div>
              <p class="cover-main">点击上传封面</p>
              <p class="cover-sub">清晰不模糊，吸引更多点击</p>
            </div>
            <div v-else class="cover-selected">
              <img :src="coverPreviewUrl" class="cover-preview" alt="封面预览" />
              <div class="cover-overlay">
                <span>更换封面</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 上传进度提示 -->
        <div v-if="loading" class="upload-progress-card">
          <div class="progress-spinner"></div>
          <span class="progress-text">正在上传，请稍候...</span>
        </div>
      </div>

      <!-- 右侧：表单信息区 -->
      <div class="right-panel">
        <!-- 标题 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">✏️</span>
            <span class="card-title">视频标题</span>
            <span class="card-tip">选填</span>
          </div>
          <div class="input-with-btn">
            <input
              v-model.trim="form.title"
              class="form-input"
              type="text"
              maxlength="80"
              placeholder="可留空，后续也可以手动使用 AI 助攻补全"
            />
            <button
              type="button"
              class="ai-btn"
              :disabled="loadingAiAssist"
              @click="runUploadAssist({ applyGeneratedTitle: true })"
            >
              <span>🤖</span>
              <span>{{ loadingAiAssist ? 'AI 生成中...' : 'AI 助攻' }}</span>
            </button>
          </div>
        </div>

        <!-- 简介 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">📝</span>
            <span class="card-title">视频简介</span>
          </div>
          <div class="textarea-group">
            <textarea
              v-model.trim="form.description"
              class="form-textarea"
              rows="4"
              maxlength="500"
              placeholder="补充视频亮点、信息来源或观看提示（选填）"
            />
            <button
              type="button"
              class="ai-btn ai-btn-sm"
              :disabled="loadingSuggest"
              @click="recommendTags"
            >
              <span>✨</span>
              <span>{{ loadingSuggest ? '生成中...' : 'AI 生成建议' }}</span>
            </button>
          </div>
        </div>

        <!-- 标签 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">🏷️</span>
            <span class="card-title">标签</span>
            <span v-if="selectedTags.length > 0" class="card-count">已选 {{ selectedTags.length }}</span>
          </div>

          <div v-if="tags.length > 0" class="tag-section">
            <!-- 已选标签 -->
            <div v-if="selectedTags.length > 0" class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label">已选标签</span>
                <button type="button" class="clear-btn" @click="form.tagIds = []">清空</button>
              </div>
              <div class="tag-list">
                <button
                  v-for="tag in selectedTags"
                  :key="`selected-${tag.id}`"
                  type="button"
                  class="tag-chip active"
                  @click="toggleTag(tag.id)"
                >
                  {{ tag.name }} ✕
                </button>
              </div>
            </div>

            <!-- AI 推荐标签 -->
            <div v-if="recommendedTags.length > 0" class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label ai-label">✨ AI 推荐</span>
              </div>
              <div class="tag-list">
                <button
                  v-for="tag in recommendedTags"
                  :key="`recommended-${tag.id}`"
                  type="button"
                  class="tag-chip suggested"
                  @click="toggleTag(tag.id)"
                >
                  {{ tag.name }}
                </button>
              </div>
            </div>

            <!-- 搜索 + 全部标签 -->
            <div class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label">选择标签</span>
              </div>
              <input
                v-model.trim="tagKeyword"
                class="form-input tag-search"
                type="text"
                maxlength="20"
                placeholder="🔍 搜索标签..."
              />
              <div class="tag-list">
                <button
                  v-for="tag in visibleAvailableTags"
                  :key="tag.id"
                  type="button"
                  class="tag-chip"
                  :class="{
                    active: form.tagIds.includes(tag.id),
                    suggested: aiSuggestedTagIds.includes(tag.id) && !form.tagIds.includes(tag.id)
                  }"
                  @click="toggleTag(tag.id)"
                >
                  {{ tag.name }}
                </button>
              </div>
              <div v-if="filteredAvailableTags.length > defaultVisibleTagCount" class="expand-row">
                <button type="button" class="expand-btn" @click="showAllTags = !showAllTags">
                  {{ showAllTags ? '▲ 收起标签' : '▼ 展开更多标签' }}
                </button>
              </div>
            </div>
          </div>
          <p v-else class="empty-tip">暂无标签数据，请先在后台维护标签。</p>
        </div>

        <!-- 分类 -->
        <div class="panel-card">
          <div class="card-header">
            <span class="card-icon">📂</span>
            <span class="card-title">分类选择</span>
            <span v-if="selectedCategoryLabel" class="card-selected-label">{{ selectedCategoryLabel }}</span>
          </div>

          <div v-if="parentCategories.length > 0" class="category-section">
            <!-- 一级分区 -->
            <div class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label">一级分区</span>
              </div>
              <div class="tag-list">
                <button
                  v-for="parent in parentCategories"
                  :key="parent.id"
                  type="button"
                  class="tag-chip category-chip"
                  :class="{
                    active: selectedParentCategoryId === parent.id &&
                      (!activeChildCategories.length || !form.categoryId ||
                       form.categoryId === parent.id ||
                       activeChildCategories.some(item => item.id === form.categoryId))
                  }"
                  @click="selectParentCategory(parent)"
                >
                  {{ parent.name }}
                </button>
              </div>
            </div>

            <!-- 二级分区 -->
            <div v-if="activeChildCategories.length > 0" class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label">二级分区</span>
              </div>
              <div class="tag-list">
                <button
                  v-for="child in activeChildCategories"
                  :key="child.id"
                  type="button"
                  class="tag-chip"
                  :class="{ active: form.categoryId === child.id }"
                  @click="selectChildCategory(child)"
                >
                  {{ child.name }}
                </button>
              </div>
            </div>
            <div v-else-if="selectedParentCategoryId" class="tag-group">
              <div class="tag-group-header">
                <span class="tag-group-label">当前分区</span>
              </div>
              <div class="tag-list">
                <button
                  type="button"
                  class="tag-chip"
                  :class="{ active: form.categoryId === selectedParentCategoryId }"
                  @click="selectCurrentParentAsCategory"
                >
                  {{ activeParentCategory?.name || '请选择一级分区' }}
                </button>
              </div>
            </div>
          </div>
          <p v-else class="empty-tip">暂无分类数据，请先在后台维护分类。</p>
        </div>

        <!-- 错误提示 + 提交 -->
        <div class="submit-section">
          <p v-if="error" class="error-msg">
            <span>⚠️</span>
            {{ error }}
          </p>
          <div class="submit-row">
            <button type="button" class="cancel-btn" @click="$router.back()">取消</button>
            <button class="submit-btn" type="submit" :disabled="loading">
              <span v-if="loading" class="btn-spinner"></span>
              <span>{{ loading ? '上传中...' : '立即投稿 🚀' }}</span>
            </button>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { uploadVideo } from '../api/video'
import { getCategoryTree } from '../api/category'
import { getTagList, recommendTags as recommendTagsApi } from '../api/tag'
import { createUploadAssistTask, pollAgentTask } from '../api/agent'

const router = useRouter()
const videoInput = ref(null)
const coverInput = ref(null)
const loading = ref(false)
const error = ref('')
const loadingSuggest = ref(false)
const loadingAiAssist = ref(false)
const isVideoDragging = ref(false)
const videoFile = ref(null)
const coverFile = ref(null)
const coverPreviewUrl = ref('')
const categories = ref([])
const tags = ref([])
const selectedParentCategoryId = ref(null)
const tagKeyword = ref('')
const showAllTags = ref(false)
const aiSuggestedTagIds = ref([])
const defaultVisibleTagCount = 18

const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  tagIds: []
})

const parentCategories = computed(() => categories.value || [])

const activeParentCategory = computed(() =>
  parentCategories.value.find(item => item.id === selectedParentCategoryId.value) || null
)

const activeChildCategories = computed(() =>
  Array.isArray(activeParentCategory.value?.children) ? activeParentCategory.value.children : []
)

const selectedTags = computed(() => tags.value.filter(tag => form.tagIds.includes(tag.id)))

const recommendedTags = computed(() =>
  tags.value.filter(tag => aiSuggestedTagIds.value.includes(tag.id) && !form.tagIds.includes(tag.id))
)

const filteredAvailableTags = computed(() => {
  const keyword = tagKeyword.value.trim().toLowerCase()
  let available = tags.value.filter(tag => !form.tagIds.includes(tag.id))
  if (keyword) {
    available = available.filter(tag => tag.name.toLowerCase().includes(keyword))
  }
  const recommendedIds = new Set(aiSuggestedTagIds.value)
  return [...available].sort((a, b) => {
    const aPriority = recommendedIds.has(a.id) ? 0 : 1
    const bPriority = recommendedIds.has(b.id) ? 0 : 1
    if (aPriority !== bPriority) return aPriority - bPriority
    return a.id - b.id
  })
})

const visibleAvailableTags = computed(() =>
  showAllTags.value ? filteredAvailableTags.value : filteredAvailableTags.value.slice(0, defaultVisibleTagCount)
)

const flattenedCategories = computed(() => {
  const flat = []
  parentCategories.value.forEach(parent => {
    flat.push({ id: parent.id, name: parent.name })
    ;(parent.children || []).forEach(child => {
      flat.push({ id: child.id, name: child.name })
    })
  })
  return flat
})

const selectedCategoryLabel = computed(() => {
  if (!form.categoryId) return ''
  for (const parent of parentCategories.value) {
    if (parent.id === form.categoryId) return parent.name
    const child = (parent.children || []).find(item => item.id === form.categoryId)
    if (child) return `${parent.name} / ${child.name}`
  }
  return ''
})

function syncSelectedCategory(categoryId) {
  if (!categoryId) {
    selectedParentCategoryId.value = null
    form.categoryId = ''
    return
  }
  for (const parent of parentCategories.value) {
    if (parent.id === categoryId) {
      selectedParentCategoryId.value = parent.id
      form.categoryId = parent.id
      return
    }
    const child = (parent.children || []).find(item => item.id === categoryId)
    if (child) {
      selectedParentCategoryId.value = parent.id
      form.categoryId = child.id
      return
    }
  }
}

function selectParentCategory(parent) {
  selectedParentCategoryId.value = parent.id
  if (!Array.isArray(parent.children) || parent.children.length === 0) {
    form.categoryId = parent.id
    return
  }
  const stillSelected = parent.children.some(item => item.id === form.categoryId)
  if (!stillSelected) {
    form.categoryId = ''
  }
}

function selectChildCategory(child) {
  form.categoryId = child.id
}

function selectCurrentParentAsCategory() {
  if (selectedParentCategoryId.value) {
    form.categoryId = selectedParentCategoryId.value
  }
}

function prettySize(bytes = 0) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}

function toggleTag(tagId) {
  const idx = form.tagIds.indexOf(tagId)
  if (idx >= 0) form.tagIds.splice(idx, 1)
  else form.tagIds.push(tagId)
}

function onVideoChange(e) {
  videoFile.value = e.target.files?.[0] || null
}

function onCoverChange(e) {
  const file = e.target.files?.[0] || null
  coverFile.value = file
  if (file) {
    coverPreviewUrl.value = URL.createObjectURL(file)
  } else {
    coverPreviewUrl.value = ''
  }
}

function onDragOver() {
  isVideoDragging.value = true
}

function onDragLeave() {
  isVideoDragging.value = false
}

function onDropVideo(e) {
  e.preventDefault()
  isVideoDragging.value = false
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  const file = files[0]
  if (!file.type.startsWith('video/')) {
    error.value = '请拖拽视频文件'
    return
  }
  videoFile.value = file
}

async function loadCategories() {
  try {
    categories.value = await getCategoryTree()
    syncSelectedCategory(form.categoryId)
    if (!selectedParentCategoryId.value && parentCategories.value.length > 0) {
      selectedParentCategoryId.value = parentCategories.value[0].id
    }
  } catch (e) {
    console.error(e)
  }
}

async function loadTags() {
  try {
    tags.value = await getTagList()
  } catch (e) {
    console.error(e)
  }
}

async function recommendTags() {
  if (!form.title.trim() && !form.description.trim()) {
    error.value = '请先填写标题或简介，再使用 AI 建议'
    return
  }
  loadingSuggest.value = true
  error.value = ''
  try {
    const ids = await recommendTagsApi({
      title: form.title || '',
      description: form.description || ''
    })
    if (Array.isArray(ids) && ids.length > 0) {
      aiSuggestedTagIds.value = [...new Set(ids)]
      form.tagIds = [...new Set([...form.tagIds, ...ids])]
    }
  } catch (e) {
    error.value = e.message || '智能推荐失败'
  } finally {
    loadingSuggest.value = false
  }
}

async function runUploadAssist(options = {}) {
  const { silent = false, applyGeneratedTitle = false } = options
  loadingAiAssist.value = true
  if (!silent) error.value = ''
  try {
    const createRes = await createUploadAssistTask({
      title: form.title || '',
      description: form.description || '',
      candidate_tags: tags.value.map(i => i.name),
      candidate_categories: flattenedCategories.value.map(i => ({ id: i.id, name: i.name }))
    })

    const taskId = createRes?.data?.task_id
    if (!taskId) throw new Error('AI 任务创建失败')

    const task = await pollAgentTask(taskId)
    const result = task?.result || {}

    if (Array.isArray(result.suggested_tags) && result.suggested_tags.length > 0) {
      const suggestedIds = result.suggested_tags
        .map(name => tags.value.find(i => i.name === name)?.id)
        .filter(Boolean)
      aiSuggestedTagIds.value = [...new Set(suggestedIds)]
      form.tagIds = [...new Set([...form.tagIds, ...suggestedIds])]
    }

    if (result.suggested_category_id) syncSelectedCategory(Number(result.suggested_category_id))
    if (typeof result.generated_summary === 'string' && result.generated_summary.trim() && !form.description.trim()) {
      form.description = result.generated_summary.trim()
    }
    if (applyGeneratedTitle && typeof result.generated_title === 'string' && result.generated_title.trim() && !form.title.trim()) {
      form.title = result.generated_title.trim()
    }
    return result
  } catch (e) {
    if (!silent) {
      error.value = e.message || 'AI 助攻失败'
    }
    return null
  } finally {
    loadingAiAssist.value = false
  }
}

async function submit() {
  error.value = ''
  if (!videoFile.value) {
    error.value = '请选择视频文件'
    return
  }

  loading.value = true
  try {
    const fd = new FormData()
    fd.append('video', videoFile.value)
    if (coverFile.value) fd.append('cover', coverFile.value)
    fd.append('title', form.title.trim())
    if (form.description.trim()) fd.append('description', form.description.trim())

    if (form.categoryId) fd.append('categoryId', String(form.categoryId))
    form.tagIds.forEach(tagId => fd.append('tagIds', String(tagId)))

    const res = await uploadVideo(fd)
    alert('上传成功')
    router.push(`/video/${res.id}`)
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCategories()
  loadTags()
})
</script>

<style scoped>
/* ===== 页面容器 ===== */
.upload-page {
  min-height: 100vh;
  background: #f8fafc;
  padding: 32px 40px 48px;
}

/* ===== 页面标题 ===== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.2);
  flex-shrink: 0;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: #1f2937;
  line-height: 1.2;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #94a3b8;
  font-weight: 400;
}

.file-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: #16a34a;
}

.badge-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse-dot 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.85); }
}

/* ===== 双栏布局 ===== */
.upload-layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 24px;
  align-items: start;
}

.left-panel,
.right-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ===== 通用卡片 ===== */
.panel-card {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.25s;
}

.panel-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.07);
}

/* ===== 卡片头部 ===== */
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}

.card-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  flex: 1;
}

.card-tip {
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 999px;
}

.card-count {
  font-size: 12px;
  color: #4f46e5;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 600;
}

.card-selected-label {
  font-size: 12px;
  color: #16a34a;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 600;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 视频拖拽区 ===== */
.video-drop-zone {
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.25s;
  padding: 20px;
}

.video-drop-zone:hover,
.video-drop-zone.dragging {
  border-color: #374151;
  background: #f1f5f9;
  box-shadow: 0 0 0 3px rgba(55, 65, 81, 0.08);
}

.video-drop-zone.has-file {
  border-style: solid;
  border-color: #d1d5db;
  background: #ffffff;
}

.drop-idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.drop-circle {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.25);
}

.drop-plus {
  color: #ffffff;
  font-size: 28px;
  line-height: 1;
  font-weight: 300;
}

.drop-main {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.drop-sub {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}

.drop-selected {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.file-icon-wrap {
  font-size: 32px;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  margin: 4px 0 0;
  font-size: 12px;
  color: #94a3b8;
}

.file-remove {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.file-remove:hover {
  background: #fee2e2;
  border-color: #fca5a5;
  color: #dc2626;
}

.cover-drop-zone {
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  background: #f8fafc;
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.25s;
  overflow: hidden;
  position: relative;
}

.cover-drop-zone:hover {
  border-color: #374151;
  background: #f1f5f9;
}

.cover-drop-zone.has-cover {
  border-style: solid;
  border-color: #d1d5db;
  min-height: 160px;
}

.cover-idle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
  padding: 20px;
}

.cover-icon {
  font-size: 28px;
}

.cover-main {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
}

.cover-sub {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
}

.cover-selected {
  width: 100%;
  height: 100%;
  position: relative;
  min-height: 160px;
}

.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  min-height: 160px;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
  color: #ffffff;
  font-size: 14px;
  font-weight: 600;
}

.cover-drop-zone:hover .cover-overlay {
  opacity: 1;
}

.upload-progress-card {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.progress-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e2e8f0;
  border-top-color: #374151;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.progress-text {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}

.form-input {
  width: 100%;
  height: 42px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  padding: 0 14px;
  transition: all 0.2s;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #374151;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(55, 65, 81, 0.08);
}

.form-textarea {
  width: 100%;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
  color: #1f2937;
  font-size: 14px;
  padding: 12px 14px;
  transition: all 0.2s;
  resize: vertical;
  min-height: 96px;
  box-sizing: border-box;
  font-family: inherit;
}

.form-textarea:focus {
  outline: none;
  border-color: #374151;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(55, 65, 81, 0.08);
}

.input-with-btn {
  display: flex;
  gap: 10px;
  align-items: center;
}

.input-with-btn .form-input {
  flex: 1;
}

.textarea-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  height: 42px;
  border-radius: 10px;
  border: 1.5px solid #d1d5db;
  background: #ffffff;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s;
  white-space: nowrap;
  flex-shrink: 0;
}

.ai-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.2);
  transform: translateY(-1px);
}

.ai-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-btn-sm {
  align-self: flex-end;
  height: 38px;
  padding: 0 14px;
  font-size: 12px;
}

.tag-section,
.category-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.tag-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.tag-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tag-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.tag-group-label.ai-label {
  color: #7c3aed;
}

.clear-btn {
  border: none;
  background: transparent;
  color: #94a3b8;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
  transition: color 0.2s;
}

.clear-btn:hover {
  color: #dc2626;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-search {
  height: 36px;
  font-size: 13px;
}

.tag-chip {
  border: 1.5px solid #e2e8f0;
  border-radius: 999px;
  background: #f8fafc;
  color: #475569;
  font-size: 13px;
  height: 30px;
  padding: 0 12px;
  cursor: pointer;
  transition: all 0.2s;
  font-weight: 500;
}

.tag-chip:hover {
  border-color: #374151;
  color: #1f2937;
  background: #f1f5f9;
}

.tag-chip.active {
  background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
  border-color: transparent;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(31, 41, 55, 0.2);
}

.tag-chip.suggested {
  border-color: #c4b5fd;
  color: #7c3aed;
  background: #f5f3ff;
}

.tag-chip.suggested:hover {
  background: #ede9fe;
  border-color: #a78bfa;
}

.category-chip {
  font-weight: 700;
}

.expand-row {
  display: flex;
  justify-content: flex-start;
}

.expand-btn {
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 0;
  transition: color 0.2s;
  font-weight: 500;
}

.expand-btn:hover {
  color: #1f2937;
}

.submit-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.error-msg {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 12px 16px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 10px;
  color: #dc2626;
  font-size: 13px;
  font-weight: 500;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cancel-btn {
  height: 44px;
  padding: 0 24px;
  border-radius: 10px;
  border: 1.5px solid #e2e8f0;
  background: #ffffff;
  color: #475569;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.cancel-btn:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
  color: #1f2937;
}

.submit-btn {
  height: 44px;
  padding: 0 32px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #1f2937 0%, #374151 100%);
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.25s;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 4px 12px rgba(31, 41, 55, 0.2);
}

.submit-btn:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(31, 41, 55, 0.3);
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  flex-shrink: 0;
}

.empty-tip {
  margin: 0;
  color: #94a3b8;
  font-size: 13px;
}

@media (max-width: 900px) {
  .upload-page {
    padding: 20px 16px 32px;
  }

  .upload-layout {
    grid-template-columns: 1fr;
  }

  .input-with-btn {
    flex-direction: column;
    align-items: stretch;
  }

  .ai-btn {
    width: 100%;
    justify-content: center;
  }

  .ai-btn-sm {
    align-self: stretch;
  }

  .submit-row {
    flex-direction: column-reverse;
  }

  .cancel-btn,
  .submit-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
