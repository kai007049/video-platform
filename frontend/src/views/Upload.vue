<template>
  <div class="bili-upload-page">
    <div class="bili-upload-container">
      <!-- 标题 -->
      <div class="bili-header">
        <span class="header-decor"></span>
        <h1>投稿视频</h1>
      </div>

      <form @submit.prevent="submit" class="bili-form-layout">
        <!-- 视频和封面上传 -->
        <div class="bili-upload-section">
          <div 
            class="upload-box upload-box-video"
            @click="videoInput?.click()"
            @dragover="onDragOver"
            @drop="onDropVideo"
          >
            <input
              ref="videoInput"
              type="file"
              accept="video/*"
              @change="onVideoChange"
              style="display: none"
            />
            <div class="upload-icon">
              <svg viewBox="0 0 24 24" width="48" height="48">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z" fill="#00AEEC"/>
              </svg>
            </div>
            <p class="upload-hint">{{ videoFile ? videoFile.name : '点击或拖拽视频文件到此处' }}</p>
          </div>

          <div 
            class="upload-box upload-box-cover"
            @click="coverInput?.click()"
          >
            <input
              ref="coverInput"
              type="file"
              accept="image/*"
              @change="onCoverChange"
              style="display: none"
            />
            <p class="upload-hint-small">{{ coverFile ? '✓ 已上传' : '上传封面图' }}</p>
          </div>
        </div>

        <!-- 视频标题 -->
        <div class="bili-form-item">
          <label class="form-label">视频标题</label>
          <div class="title-input-wrapper">
            <input 
              v-model="form.title" 
              type="text" 
              class="form-input"
              placeholder="给视频起个响亮的标题吧！"
              required 
            />
            <button 
              type="button" 
              class="ai-assist-btn"
              @click="runUploadAssist" 
              :disabled="loadingAiAssist"
            >
              <span class="ai-icon">✨</span> AI 助攻
            </button>
          </div>
        </div>

        <!-- 标签选择 -->
        <div class="bili-form-item">
          <label class="form-label">摄序</label>
          <div class="title-input-wrapper">
            <input 
              v-model="form.description" 
              type="text" 
              class="form-input"
              placeholder="给视频起个响亮的标题吧！"
            />
            <button 
              type="button" 
              class="ai-suggest-btn"
              @click="recommendTags" 
              :disabled="loadingSuggest"
            >
              AI 生成建议
            </button>
          </div>
          
          <div class="preset-tags">
            <button 
              type="button" 
              v-for="tag in presetTags" 
              :key="tag"
              class="preset-tag-btn"
              :class="{ active: selectedPresetTags.includes(tag) }"
              @click="togglePresetTag(tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <!-- 分类选择 -->
        <div class="bili-form-item">
          <label class="form-label">分类选择</label>
          <div class="category-select-wrapper">
            <select 
              v-model.number="form.categoryId" 
              class="form-select"
            >
              <option value="">分类选择</option>
              <option v-for="c in allCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
        </div>

        <!-- 错误提示 -->
        <p v-if="error" class="hint error-hint">{{ error }}</p>

        <!-- 提交按钮 -->
        <div class="submit-section">
          <button type="submit" class="bili-btn-main" :disabled="loading">
            {{ loading ? '上传中...' : '立即投稿' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
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
const videoFile = ref(null)
const coverFile = ref(null)
const categories = ref([])
const tags = ref([])
const form = reactive({
  title: '',
  description: '',
  categoryId: '',
  tagIds: []
})

// 预设标签
const presetTags = ref([
  '创作灵感',
  '生活日常',
  'ACG',
  '游戏视频',
  '数码科技'
])

const selectedPresetTags = ref([])

// 所有分类（扁平化处理）
const allCategories = computed(() => {
  const flatCategories = []
  categories.value.forEach(parent => {
    flatCategories.push(parent)
    if (parent.children) {
      parent.children.forEach(child => {
        flatCategories.push(child)
      })
    }
  })
  return flatCategories
})

function collectTagIds() {
  const presetTagIds = selectedPresetTags.value
    .map(tagName => tags.value.find(tag => tag.name === tagName)?.id)
    .filter(id => id != null)

  return [...new Set([...(form.tagIds || []), ...presetTagIds])]
}

function onVideoChange(e) {
  videoFile.value = e.target.files?.[0] || null
}

function onCoverChange(e) {
  coverFile.value = e.target.files?.[0] || null
}

function onDragOver(e) {
  e.preventDefault()
  e.stopPropagation()
  e.currentTarget.style.borderColor = 'var(--bili-blue)'
  e.currentTarget.style.background = 'rgba(0, 174, 236, 0.05)'
}

function onDropVideo(e) {
  e.preventDefault()
  e.stopPropagation()
  e.currentTarget.style.borderColor = 'var(--border-dashed)'
  e.currentTarget.style.background = 'transparent'
  
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    const file = files[0]
    if (file.type.startsWith('video/')) {
      videoFile.value = file
    } else {
      error.value = '请拖拽视频文件'
    }
  }
}

// 切换预设标签
function togglePresetTag(tag) {
  const index = selectedPresetTags.value.indexOf(tag)
  if (index > -1) {
    selectedPresetTags.value.splice(index, 1)
  } else {
    selectedPresetTags.value.push(tag)
  }
}

async function loadCategories() {
  try {
    categories.value = await getCategoryTree()
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
  loadingSuggest.value = true
  try {
    // 模拟 AI 生成建议
    form.description = '这是一个关于 ' + form.title + ' 的视频，内容丰富精彩，欢迎观看！'
  } catch (e) {
    error.value = e.message || '智能推荐失败'
  } finally {
    loadingSuggest.value = false
  }
}

async function runUploadAssist() {
  if (!form.title.trim()) {
    error.value = '请先填写标题，再使用 AI 助手'
    return
  }
  loadingAiAssist.value = true
  try {
    // 模拟 AI 助攻
    form.title = form.title + ' - 精彩内容'
  } catch (e) {
    error.value = e.message || 'AI 助手执行失败'
  } finally {
    loadingAiAssist.value = false
  }
}

async function submit() {
  if (!videoFile.value || !form.title.trim()) {
    error.value = '请选择视频并填写标题'
    return
  }
  if (!form.categoryId) {
    error.value = '请选择视频分类'
    return
  }
  loading.value = true
  try {
    const fd = new FormData()
    fd.append('video', videoFile.value)
    if (coverFile.value) fd.append('cover', coverFile.value)
    fd.append('title', form.title.trim())
    if (form.description.trim()) fd.append('description', form.description.trim())
    fd.append('categoryId', String(form.categoryId))
    const tagIds = collectTagIds()
    tagIds.forEach(tagId => {
      fd.append('tagIds', String(tagId))
    })
    const res = await uploadVideo(fd)
    alert('投稿成功')
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
/* ============ 页面背景 ============ */
.bili-upload-page {
  background: linear-gradient(135deg, #F1F2F3 0%, rgba(0, 174, 236, 0.08) 50%, rgba(251, 114, 153, 0.08) 100%);
  padding: 40px 20px;
  min-height: 100vh;
  position: relative;
}

/* 漂浮背景光效 */
.bili-upload-page::before {
  content: '';
  position: fixed;
  top: -50%;
  right: -50%;
  width: 800px;
  height: 800px;
  background: radial-gradient(circle, rgba(0, 174, 236, 0.1) 0%, transparent 70%);
  pointer-events: none;
  z-index: -1;
  filter: blur(40px);
}

.bili-upload-page::after {
  content: '';
  position: fixed;
  bottom: -30%;
  left: -30%;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(251, 114, 153, 0.1) 0%, transparent 70%);
  pointer-events: none;
  z-index: -1;
  filter: blur(40px);
}

/* ============ 卡片容器 ============ */
.bili-upload-container {
  max-width: 800px;
  margin: 0 auto;
  background: #FFFFFF;
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  position: relative;
}

/* ============ 标题 ============ */
.bili-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
}

.header-decor {
  display: block;
  width: 3px;
  height: 24px;
  background: var(--bili-pink);
  border-radius: 2px;
  box-shadow: 0 0 12px rgba(251, 114, 153, 0.5), 0 0 24px rgba(251, 114, 153, 0.3);
  animation: neonGlowPink 2s ease-in-out infinite;
}

@keyframes neonGlowPink {
  0%, 100% { box-shadow: 0 0 12px rgba(251, 114, 153, 0.5), 0 0 24px rgba(251, 114, 153, 0.3); }
  50% { box-shadow: 0 0 16px rgba(251, 114, 153, 0.7), 0 0 32px rgba(251, 114, 153, 0.4); }
}

.bili-header h1 {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.5px;
}

/* ============ 表单 ============ */
.bili-form-layout {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ============ 上传区域 ============ */
.bili-upload-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 0;
}

.upload-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  border: 1.5px dashed #00AEEC;
  border-radius: 8px;
  background: linear-gradient(135deg, #E6F7FB 0%, #F0F9FC 100%);
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: center;
  position: relative;
}

.upload-box:hover {
  border-color: #0099d9;
  background: linear-gradient(135deg, #D1F0F8 0%, #E6F7FB 100%);
  transform: translateY(-2px);
}

.upload-box-video {
  min-height: 160px;
}

.upload-box-cover {
  min-height: 160px;
  background: #F8F8F8;
  border-color: #DDD;
}

.upload-icon {
  margin-bottom: 12px;
}

.upload-icon svg {
  width: 48px;
  height: 48px;
  fill: #00AEEC;
}

.upload-hint {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin: 0;
}

.upload-hint-small {
  font-size: 14px;
  font-weight: 500;
  color: #666;
  margin: 0;
}

/* ============ 表单项 ============ */
.bili-form-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

/* ============ 输入框 ============ */
.form-input,
.form-select {
  font-family: inherit;
  font-size: 14px;
  color: #333;
  border: 1px solid #E0E0E0;
  border-radius: 6px;
  padding: 10px 16px;
  background: linear-gradient(135deg, #FAFAFA 0%, #FFFFFF 100%);
  transition: all 0.3s ease;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.05);
}

.form-input {
  height: 44px;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: #00AEEC;
  box-shadow: 0 0 0 3px rgba(0, 174, 236, 0.1), inset 0 1px 2px rgba(0, 174, 236, 0.1);
  background: linear-gradient(135deg, #F0F9FC 0%, #FFFFFF 100%);
}

.form-select {
  height: 44px;
  cursor: pointer;
  appearance: none;
  background: linear-gradient(135deg, #FAFAFA 0%, #FFFFFF 100%);
  padding-right: 40px;
  position: relative;
  transition: all 0.3s ease;
}

.form-select:hover {
  border-color: #00AEEC;
  background: linear-gradient(135deg, #F0F9FC 0%, #FFFFFF 100%);
}

.form-select::after {
  content: '';
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23999' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E") no-repeat center;
  pointer-events: none;
  transition: all 0.3s ease;
}

.form-select:hover::after {
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2300AEEC' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E") no-repeat center;
}

.form-select:focus::after {
  background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2300AEEC' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E") no-repeat center;
  transform: translateY(-50%) rotate(180deg);
}

/* ============ 标题输入框 ============ */
.title-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-input-wrapper .form-input {
  flex: 1;
}

/* ============ AI 按钮 ============ */
.ai-assist-btn {
  padding: 8px 16px;
  background: #E6F7FB;
  color: #00AEEC;
  border: 1px solid #00AEEC;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-assist-btn:hover {
  background: #00AEEC;
  color: #FFF;
  transform: translateY(-1px);
}

.ai-icon {
  font-size: 16px;
}

.ai-suggest-btn {
  padding: 8px 16px;
  background: #FFF;
  color: #00AEEC;
  border: 1px solid #00AEEC;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.ai-suggest-btn:hover {
  background: #00AEEC;
  color: #FFF;
  transform: translateY(-1px);
}

/* ============ 预设标签 ============ */
.preset-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.preset-tag-btn {
  padding: 6px 16px;
  background: #F0F0F0;
  color: #333;
  border: 1px solid #DDD;
  border-radius: 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.preset-tag-btn:hover {
  background: #E6F7FB;
  border-color: #00AEEC;
  color: #00AEEC;
  transform: translateY(-1px);
}

.preset-tag-btn.active {
  background: #00AEEC;
  border-color: #00AEEC;
  color: #FFF;
}

/* ============ 分类选择 ============ */
.category-select-wrapper {
  width: 100%;
}

/* ============ 提交区域 ============ */
.submit-section {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ============ 主要按钮 ============ */
.bili-btn-main {
  height: 44px;
  padding: 0 32px;
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E8E 100%);
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.3);
}

.bili-btn-main:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(255, 107, 107, 0.4);
  background: linear-gradient(135deg, #FF5252 0%, #FF7B7B 100%);
}

.bili-btn-main:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.2);
}

.bili-btn-main:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

/* ============ 提示文本 ============ */
.hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.error-hint {
  color: #E74C3C;
}

/* ============ 响应式 ============ */
@media (max-width: 768px) {
  .bili-upload-section {
    grid-template-columns: 1fr;
  }

  .upload-box-cover {
    min-height: 120px;
  }

  .title-input-wrapper {
    flex-direction: column;
    align-items: stretch;
  }

  .ai-assist-btn,
  .ai-suggest-btn {
    align-self: flex-start;
  }

  .preset-tags {
    justify-content: center;
  }

  .submit-section {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .bili-upload-container {
    padding: 20px;
  }

  .bili-upload-section {
    gap: 12px;
  }

  .upload-box {
    padding: 24px 12px;
  }

  .preset-tag-btn {
    padding: 4px 12px;
    font-size: 12px;
  }

  .bili-btn-main {
    width: 100%;
  }
}
</style>
