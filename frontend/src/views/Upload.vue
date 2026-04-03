<template>
  <div class="upload-page">
    <div class="upload-container">
      <div class="page-title">
        <span class="title-line"></span>
        <h1>投稿视频</h1>
      </div>

      <form class="upload-form" @submit.prevent="submit">
        <div class="media-section">
          <div
            class="media-card media-card-video"
            @click="videoInput?.click()"
            @dragover.prevent="onDragOver"
            @dragleave.prevent="onDragLeave"
            @drop="onDropVideo"
            :class="{ dragging: isVideoDragging }"
          >
            <input ref="videoInput" type="file" accept="video/*" hidden @change="onVideoChange" />
            <div class="plus-icon" aria-hidden="true">+</div>
            <p class="card-main-text">
              {{ videoFile ? videoFile.name : '点击或拖拽视频文件到此处' }}
            </p>
            <p v-if="videoFile" class="card-sub-text">
              {{ prettySize(videoFile.size) }}
            </p>
          </div>

          <div class="media-card media-card-cover" @click="coverInput?.click()">
            <input ref="coverInput" type="file" accept="image/*" hidden @change="onCoverChange" />
            <p class="cover-title">上传封面图</p>
            <p class="cover-status">{{ coverFile ? '已选择封面' : '建议 16:9，清晰不模糊' }}</p>
          </div>
        </div>

        <div class="form-item">
          <label class="label">视频标题（可选）</label>
          <div class="row">
            <input
              v-model.trim="form.title"
              class="input"
              type="text"
              maxlength="80"
              placeholder="可留空，后续也可以手动使用 AI 助攻补全"
            />
            <button type="button" class="btn-secondary" :disabled="loadingAiAssist" @click="runUploadAssist({ applyGeneratedTitle: true })">
              AI 助攻
            </button>
          </div>
        </div>

        <div class="form-item">
          <label class="label">视频简介</label>
          <div class="row">
            <textarea
              v-model.trim="form.description"
              class="textarea"
              rows="4"
              maxlength="500"
              placeholder="补充视频亮点、信息来源或观看提示（选填）"
            />
            <button type="button" class="btn-secondary" :disabled="loadingSuggest" @click="recommendTags">
              AI 生成建议
            </button>
          </div>
        </div>

        <div class="form-item">
          <label class="label">标签</label>
          <div v-if="tags.length > 0" class="selector-panel">
            <div v-if="selectedTags.length > 0" class="selector-section">
              <div class="section-title-row">
                <span class="section-title">已选标签</span>
                <button type="button" class="link-btn" @click="form.tagIds = []">清空</button>
              </div>
              <div class="tag-list">
                <button
                  v-for="tag in selectedTags"
                  :key="`selected-${tag.id}`"
                  type="button"
                  class="tag-chip active"
                  @click="toggleTag(tag.id)"
                >
                  {{ tag.name }}
                </button>
              </div>
            </div>

            <div v-if="recommendedTags.length > 0" class="selector-section">
              <div class="section-title-row">
                <span class="section-title">AI 推荐</span>
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

            <div class="selector-section">
              <div class="section-title-row">
                <span class="section-title">选择标签</span>
              </div>
              <input
                v-model.trim="tagKeyword"
                class="input tag-search-input"
                type="text"
                maxlength="20"
                placeholder="搜索标签"
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
              <div v-if="filteredAvailableTags.length > defaultVisibleTagCount" class="more-row">
                <button type="button" class="link-btn" @click="showAllTags = !showAllTags">
                  {{ showAllTags ? '收起标签' : '展开更多标签' }}
                </button>
              </div>
            </div>
          </div>
          <p v-else class="tip-text">暂无标签数据，请先在后台维护标签。</p>
        </div>

        <div class="form-item">
          <label class="label">分类选择</label>
          <div v-if="parentCategories.length > 0" class="selector-panel">
            <div class="selector-section">
              <div class="section-title-row">
                <span class="section-title">一级分区</span>
              </div>
              <div class="tag-list">
                <button
                  v-for="parent in parentCategories"
                  :key="parent.id"
                  type="button"
                  class="tag-chip category-chip"
                  :class="{ active: selectedParentCategoryId === parent.id && (!activeChildCategories.length || !form.categoryId || form.categoryId === parent.id || activeChildCategories.some(item => item.id === form.categoryId)) }"
                  @click="selectParentCategory(parent)"
                >
                  {{ parent.name }}
                </button>
              </div>
            </div>

            <div v-if="activeChildCategories.length > 0" class="selector-section">
              <div class="section-title-row">
                <span class="section-title">二级分区</span>
                <span v-if="selectedCategoryLabel" class="section-tip">已选：{{ selectedCategoryLabel }}</span>
              </div>
              <div class="tag-list">
                <button
                  v-for="child in activeChildCategories"
                  :key="child.id"
                  type="button"
                  class="tag-chip subcategory-chip"
                  :class="{ active: form.categoryId === child.id }"
                  @click="selectChildCategory(child)"
                >
                  {{ child.name }}
                </button>
              </div>
            </div>
            <div v-else class="selector-section">
              <div class="section-title-row">
                <span class="section-title">当前分区</span>
                <span v-if="selectedCategoryLabel" class="section-tip">已选：{{ selectedCategoryLabel }}</span>
              </div>
              <div class="tag-list">
                <button
                  type="button"
                  class="tag-chip subcategory-chip"
                  :class="{ active: form.categoryId === selectedParentCategoryId }"
                  @click="selectCurrentParentAsCategory"
                >
                  {{ activeParentCategory?.name || '请选择一级分区' }}
                </button>
              </div>
            </div>
          </div>
          <p v-else class="tip-text">暂无分类数据，请先在后台维护分类。</p>
        </div>

        <p v-if="error" class="error-text">{{ error }}</p>

        <div class="submit-row">
          <button class="btn-main" type="submit" :disabled="loading">
            {{ loading ? '上传中...' : '立即投稿' }}
          </button>
        </div>
      </form>
    </div>
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
  coverFile.value = e.target.files?.[0] || null
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
.upload-page {
  min-height: 100vh;
  background: #f4f5f7;
  padding: 20px 16px 28px;
}

.upload-container {
  max-width: 920px;
  margin: 0 auto;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #ebedf0;
  padding: 20px 24px 24px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}

.title-line {
  width: 3px;
  height: 18px;
  border-radius: 3px;
  background: #fb7299;
}

.page-title h1 {
  margin: 0;
  font-size: 24px;
  line-height: 1;
  font-weight: 700;
  color: #18191c;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.media-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}

.media-card {
  border-radius: 8px;
  min-height: 136px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.media-card-video {
  border: 2px dashed #00aeec;
  background: #f4fbff;
}

.media-card-video:hover,
.media-card-video.dragging {
  border-color: #00a1d6;
  background: #e8f8ff;
}

.media-card-cover {
  border: 1px dashed #d7d9dd;
  background: #fafafa;
}

.media-card-cover:hover {
  border-color: #00aeec;
  background: #f8fcff;
}

.plus-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: #00aeec;
  color: #fff;
  font-size: 34px;
  line-height: 38px;
  text-align: center;
  margin-bottom: 10px;
  user-select: none;
}

.card-main-text {
  margin: 0;
  color: #18191c;
  font-size: 16px;
  font-weight: 600;
}

.card-sub-text {
  margin: 6px 0 0;
  color: #61666d;
  font-size: 12px;
}

.cover-title {
  margin: 0 0 6px;
  font-size: 16px;
  color: #18191c;
  font-weight: 600;
}

.cover-status {
  margin: 0;
  color: #9499a0;
  font-size: 12px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  font-size: 18px;
  color: #18191c;
  font-weight: 700;
}

.row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.input,
.textarea,
.select {
  width: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  color: #18191c;
  font-size: 14px;
  padding: 0 12px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input {
  height: 44px;
}

.textarea {
  min-height: 92px;
  padding: 10px 12px;
  resize: vertical;
}

.select {
  height: 44px;
  max-width: 260px;
}

.input:focus,
.textarea:focus,
.select:focus {
  outline: none;
  border-color: #00aeec;
  box-shadow: 0 0 0 2px rgba(0, 174, 236, 0.16);
}

.btn-secondary {
  flex: 0 0 auto;
  min-width: 118px;
  height: 44px;
  border: 1px solid #00aeec;
  border-radius: 8px;
  background: #f4fbff;
  color: #00a1d6;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-secondary:hover:not(:disabled) {
  background: #00aeec;
  color: #fff;
}

.btn-secondary:disabled {
  opacity: 0.56;
  cursor: not-allowed;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-chip {
  border: 1px solid #e3e5e7;
  border-radius: 999px;
  background: #f7f8fa;
  color: #61666d;
  font-size: 13px;
  height: 32px;
  padding: 0 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tag-chip:hover {
  border-color: #00aeec;
  color: #00a1d6;
  background: #f4fbff;
}

.tag-chip.active {
  color: #fff;
  border-color: #00aeec;
  background: #00aeec;
}

.selector-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  border: 1px solid #ebedf0;
  border-radius: 10px;
  background: #fafbfc;
}

.selector-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #18191c;
}

.section-tip {
  font-size: 12px;
  color: #9499a0;
}

.link-btn {
  border: 0;
  background: transparent;
  color: #00a1d6;
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.link-btn:hover {
  color: #008ac5;
}

.tag-search-input {
  max-width: 280px;
}

.tag-chip.suggested {
  border-color: #8fd8ff;
  color: #00a1d6;
  background: #eef9ff;
}

.category-chip {
  font-weight: 700;
}

.subcategory-chip {
  min-width: 96px;
}

.more-row {
  display: flex;
  justify-content: flex-start;
}

.tip-text {
  margin: 0;
  color: #9499a0;
  font-size: 12px;
}

.error-text {
  margin: 0;
  color: #f56c6c;
  font-size: 12px;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.btn-main {
  width: 132px;
  height: 44px;
  border: 0;
  border-radius: 8px;
  background: #fb7299;
  color: #fff;
  font-size: 18px;
  font-weight: 800;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn-main:hover:not(:disabled) {
  background: #fc5f8f;
}

.btn-main:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 960px) {
  .media-section {
    grid-template-columns: 1fr;
  }

  .row {
    flex-direction: column;
  }

  .btn-secondary {
    width: 100%;
  }

  .select {
    max-width: 100%;
  }

  .submit-row {
    justify-content: stretch;
  }

  .btn-main {
    width: 100%;
  }
}
</style>
