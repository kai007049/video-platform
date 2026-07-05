<template>
  <div class="upload-page">
    <div class="page-header">
      <div class="header-left">
        <div class="header-icon">➕</div>
        <div>
          <h1 class="page-title">发布投稿</h1>
          <p class="page-subtitle">上传你的视频，分享给更多人</p>
        </div>
      </div>
      <div class="header-right">
        <div v-if="uploadSession" class="session-badge">
          <span class="badge-dot"></span>
          会话 {{ uploadSession.status }} · {{ uploadSession.progress ?? 0 }}%
        </div>
      </div>
    </div>

    <form class="upload-layout" @submit.prevent="submit">
      <div class="left-panel">
        <div class="panel-card">
          <div class="card-header"><span class="card-icon">🎬</span><span class="card-title">视频文件</span></div>
          <div class="video-drop-zone" :class="{ dragging: isVideoDragging, 'has-file': !!videoFile }" @click="videoInput?.click()" @dragover.prevent="onDragOver" @dragleave.prevent="onDragLeave" @drop="onDropVideo">
            <input ref="videoInput" type="file" accept="video/*" hidden @change="onVideoChange" />
            <div v-if="!videoFile" class="drop-idle">
              <div class="drop-circle"><span class="drop-plus">+</span></div>
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

        <div class="panel-card">
          <div class="card-header"><span class="card-icon">🖼️</span><span class="card-title">封面图片</span><span class="card-tip">建议 16:9</span></div>
          <div class="cover-drop-zone" :class="{ 'has-cover': !!coverFile }" @click="coverInput?.click()">
            <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverChange" />
            <div v-if="!coverFile" class="cover-idle">
              <div class="cover-icon">📷</div>
              <p class="cover-main">点击上传封面</p>
              <p class="cover-sub">清晰不模糊，吸引更多点击</p>
            </div>
            <div v-else class="cover-selected">
              <img :src="coverPreviewUrl" class="cover-preview" alt="封面预览" />
              <div class="cover-overlay"><span>更换封面</span></div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="upload-progress-card">
          <div class="progress-spinner"></div>
          <span class="progress-text">正在上传，请稍候...</span>
        </div>
      </div>

      <div class="right-panel">
        <div class="panel-card">
          <div class="card-header"><span class="card-icon">✏️</span><span class="card-title">视频标题</span></div>
          <input v-model.trim="form.title" class="form-input" type="text" maxlength="80" placeholder="请输入清晰准确的视频标题" />
        </div>

        <div class="panel-card">
          <div class="card-header"><span class="card-icon">📝</span><span class="card-title">视频简介</span></div>
          <textarea v-model.trim="form.description" class="form-textarea" rows="4" maxlength="500" placeholder="请填写视频亮点、信息来源或观看提示" />
        </div>

        <div class="panel-card">
          <div class="card-header card-header--stacked">
            <div class="card-header-main"><span class="card-icon">📂</span><span class="card-title">分类选择</span></div>
          </div>
          <div v-if="parentCategories.length > 0" class="category-section">
            <div class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label">一级分区</span></div>
              <div class="tag-list">
                <button v-for="parent in parentCategories" :key="parent.id" type="button" class="tag-chip category-chip" :class="{ active: selectedParentCategoryId === parent.id }" @click="selectParentCategory(parent)">{{ parent.name }}</button>
              </div>
            </div>
            <div v-if="activeChildCategories.length > 0" class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label">二级分区（推荐）</span><button type="button" class="link-btn" @click="selectCurrentParentAsCategory">直接使用一级分类</button></div>
              <div class="tag-list">
                <button v-for="child in activeChildCategories" :key="child.id" type="button" class="tag-chip" :class="{ active: form.categoryId === child.id }" @click="selectChildCategory(child)">{{ child.name }}</button>
              </div>
            </div>
          </div>
          <p v-else class="empty-tip">暂无分类数据，请先在后台维护分类。</p>
        </div>

        <div class="panel-card">
          <div class="card-header"><span class="card-icon">🏷️</span><span class="card-title">标签</span><span v-if="selectedTags.length > 0" class="card-count">已选 {{ selectedTags.length }}</span></div>
          <div v-if="tags.length > 0" class="tag-section">
            <div v-if="selectedTags.length > 0" class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label">已选标签</span><button type="button" class="clear-btn" @click="form.tagIds = []">清空</button></div>
              <div class="tag-list">
                <button v-for="tag in selectedTags" :key="`selected-${tag.id}`" type="button" class="tag-chip active" @click="toggleTag(tag.id)">{{ tag.name }} ✕</button>
              </div>
            </div>
            <div v-if="recommendedTags.length > 0" class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label ai-label">智能推荐</span></div>
              <div class="tag-list">
                <button v-for="tag in recommendedTags" :key="`recommended-${tag.id}`" type="button" class="tag-chip suggested" @click="toggleTag(tag.id)">{{ tag.name }}</button>
              </div>
            </div>
            <div v-if="categoryRelatedTags.length > 0" class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label">当前分类相关标签</span></div>
              <div class="tag-list">
                <button v-for="tag in categoryRelatedTags" :key="`category-${tag.id}`" type="button" class="tag-chip" @click="toggleTag(tag.id)">{{ tag.name }}</button>
              </div>
            </div>
            <div class="tag-group">
              <div class="tag-group-header"><span class="tag-group-label">更多标签</span><button v-if="hasMoreTags" type="button" class="expand-btn" @click="showAllTags = !showAllTags">{{ showAllTags ? '▲ 收起' : '▼ 展开更多' }}</button></div>
              <input v-model.trim="tagKeyword" class="form-input tag-search" type="text" maxlength="20" placeholder="🔍 搜索标签..." />
              <div v-if="groupedMoreTags.length > 0" class="tag-group-list">
                <div v-for="group in groupedMoreTags" :key="group.key" class="nested-tag-group">
                  <p class="nested-tag-group-label">{{ group.label }}</p>
                  <div class="tag-list">
                    <button v-for="tag in group.tags" :key="`${group.key}-${tag.id}`" type="button" class="tag-chip" @click="toggleTag(tag.id)">{{ tag.name }}</button>
                  </div>
                </div>
              </div>
              <p v-else class="empty-tip">没有匹配的更多标签，试试换个关键词。</p>
            </div>
          </div>
          <p v-else class="empty-tip">暂无标签数据，请先在后台维护标签。</p>
        </div>

        <div class="submit-section">
          <p v-if="error" class="error-msg"><span>⚠️</span>{{ error }}</p>
          <div class="submit-row">
            <button type="button" class="cancel-btn" @click="$router.back()">取消</button>
            <button class="submit-btn" type="submit" :disabled="loading"><span v-if="loading" class="btn-spinner"></span><span>{{ loading ? '上传中...' : '立即投稿 🚀' }}</span></button>
          </div>
        </div>
      </div>
    </form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createUploadSession, uploadVideo, updateUploadSessionProgress } from '../api/video'
import { getCategoryTree } from '../api/category'
import { getTagList } from '../api/tag'
import { validateUploadForm } from './uploadValidation'
import { buildUploadTagSections, resolveCategorySelectionState } from './uploadTagCategoryState.js'

const router = useRouter()
const videoInput = ref(null)
const coverInput = ref(null)
const loading = ref(false)
const error = ref('')
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
const uploadSession = ref(null)

const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  tagIds: []
})

const parentCategories = computed(() => categories.value || [])
const categoryState = computed(() => resolveCategorySelectionState({
  categories: parentCategories.value,
  selectedParentCategoryId: selectedParentCategoryId.value,
  categoryId: form.categoryId
}))
const activeChildCategories = computed(() => categoryState.value.activeChildCategories)
const selectedCategoryLabel = computed(() => categoryState.value.selectedCategoryLabel)

const tagSections = computed(() => buildUploadTagSections({
  tags: tags.value,
  selectedTagIds: form.tagIds,
  suggestedTagIds: aiSuggestedTagIds.value,
  categoryLabel: selectedCategoryLabel.value,
  keyword: tagKeyword.value,
  showAllTags: showAllTags.value,
  defaultVisibleCount: defaultVisibleTagCount
}))

const selectedTags = computed(() => tagSections.value.selectedTags)
const recommendedTags = computed(() => tagSections.value.recommendedTags)
const categoryRelatedTags = computed(() => tagSections.value.categoryTags)
const groupedMoreTags = computed(() => tagSections.value.moreTagGroups)
const hasMoreTags = computed(() => tagSections.value.hasMoreTags)

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

function selectParentCategory(parent) {
  selectedParentCategoryId.value = parent.id
  form.categoryId = parent.id
}

function selectChildCategory(child) {
  form.categoryId = child.id
}

function selectCurrentParentAsCategory() {
  if (selectedParentCategoryId.value) {
    form.categoryId = selectedParentCategoryId.value
  }
}

function onVideoChange(e) {
  videoFile.value = e.target.files?.[0] || null
}

function onCoverChange(e) {
  const file = e.target.files?.[0] || null
  coverFile.value = file
  coverPreviewUrl.value = file ? URL.createObjectURL(file) : ''
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

watch(() => form.categoryId, (newCategoryId, oldCategoryId) => {
  if (!oldCategoryId || newCategoryId === oldCategoryId) return
  aiSuggestedTagIds.value = []
  tagKeyword.value = ''
  showAllTags.value = false
})

async function submit() {
  error.value = ''
  const validationError = validateUploadForm({
    videoFile: videoFile.value,
    title: form.title,
    description: form.description,
    categoryId: form.categoryId,
    tagIds: form.tagIds
  })
  if (validationError) {
    error.value = validationError
    return
  }

  loading.value = true
  try {
    uploadSession.value = await createUploadSession({
      fileName: videoFile.value.name,
      fileSize: videoFile.value.size,
      fileHash: `${videoFile.value.name}:${videoFile.value.size}`
    })

    const fd = new FormData()
    fd.append('uploadSessionId', uploadSession.value.sessionId)
    fd.append('video', videoFile.value)
    if (coverFile.value) fd.append('cover', coverFile.value)
    fd.append('title', form.title.trim())
    fd.append('description', form.description.trim())
    fd.append('categoryId', String(form.categoryId))
    form.tagIds.forEach(tagId => fd.append('tagIds', String(tagId)))

    await updateUploadSessionProgress(uploadSession.value.sessionId, {
      uploadedBytes: videoFile.value.size,
      progress: 100
    })

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
.upload-page { min-height: 100vh; background: #f8fafc; padding: 32px 40px 48px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; }
.header-left { display: flex; align-items: center; gap: 16px; }
.header-icon { width: 48px; height: 48px; border-radius: 14px; background: linear-gradient(135deg, #1f2937 0%, #374151 100%); display: flex; align-items: center; justify-content: center; font-size: 22px; box-shadow: 0 4px 12px rgba(31, 41, 55, 0.2); flex-shrink: 0; }
.page-title { margin: 0; font-size: 24px; font-weight: 800; color: #1f2937; line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #94a3b8; font-weight: 400; }
.session-badge { display: flex; align-items: center; gap: 8px; padding: 8px 16px; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 999px; font-size: 13px; font-weight: 600; color: #2563eb; }
.badge-dot { width: 8px; height: 8px; border-radius: 50%; background: #3b82f6; animation: pulse-dot 1.5s ease-in-out infinite; }
@keyframes pulse-dot { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.6; transform: scale(0.85); } }
.upload-layout { display: grid; grid-template-columns: 360px 1fr; gap: 24px; align-items: start; }
.left-panel, .right-panel { display: flex; flex-direction: column; gap: 16px; }
.panel-card { background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; padding: 20px; box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04); }
.card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.card-header--stacked { flex-direction: column; align-items: stretch; gap: 8px; }
.card-header-main { display: flex; align-items: center; gap: 8px; }
.card-title { font-weight: 600; }
.card-tip, .card-count { font-size: 12px; color: #64748b; }
.form-input, .form-textarea { width: 100%; border: 1px solid #d1d5db; border-radius: 10px; padding: 12px 14px; font-size: 14px; background: #fff; }
.form-textarea { resize: vertical; }
.video-drop-zone, .cover-drop-zone { border: 1px dashed #cbd5e1; border-radius: 16px; padding: 28px 18px; cursor: pointer; background: #f8fafc; }
.drop-idle, .cover-idle, .drop-selected, .cover-selected { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 10px; text-align: center; }
.drop-circle, .cover-icon, .file-icon-wrap { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; background: #fff; border: 1px solid #e2e8f0; }
.drop-main, .cover-main, .file-name { margin: 0; font-weight: 600; }
.drop-sub, .cover-sub, .file-size { margin: 0; color: #64748b; font-size: 13px; }
.file-remove { border: none; background: transparent; cursor: pointer; }
.cover-preview { width: 100%; max-height: 180px; object-fit: cover; border-radius: 12px; }
.tag-group { margin-bottom: 16px; }
.tag-group-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 8px; }
.tag-chip, .category-chip, .clear-btn, .expand-btn, .link-btn { border-radius: 999px; padding: 6px 12px; border: 1px solid #d1d5db; background: #fff; cursor: pointer; }
.tag-chip.active, .category-chip.active, .tag-chip.suggested { background: var(--bili-pink); color: #fff; border-color: var(--bili-pink); }
.tag-search { margin-bottom: 12px; }
.tag-group-list { display: flex; flex-direction: column; gap: 12px; }
.nested-tag-group-label, .empty-tip { margin: 0 0 10px; color: #64748b; font-size: 13px; }
.submit-section { background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; padding: 20px; }
.error-msg { margin: 0 0 12px; color: #ef4444; }
.submit-row { display: flex; justify-content: flex-end; gap: 12px; }
.cancel-btn, .submit-btn { padding: 10px 18px; border-radius: 10px; border: none; cursor: pointer; }
.cancel-btn { background: #e2e8f0; }
.submit-btn { background: var(--bili-pink); color: #fff; }
.upload-progress-card { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border-radius: 12px; background: #eff6ff; color: #2563eb; }
.progress-spinner, .btn-spinner { width: 14px; height: 14px; border: 2px solid rgba(37, 99, 235, 0.25); border-top-color: #2563eb; border-radius: 50%; animation: spin 0.8s linear infinite; }
.btn-spinner { border-color: rgba(255, 255, 255, 0.35); border-top-color: #fff; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) { .upload-layout { grid-template-columns: 1fr; } }
</style>
