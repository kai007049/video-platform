<template>
  <div class="upload-container">
    <!-- 顶部导航栏 -->
    <div class="upload-header">
      <div class="header-content">
        <div class="header-center">
          <div class="header-title">投稿</div>
        </div>
      </div>
    </div>
    
    <!-- 主内容区 -->
    <div class="upload-content">
      <!-- 左侧：视频与封面上传区 -->
      <div class="upload-left">
        <div class="upload-form-card">
          <div class="form-title">视频上传</div>
          
          <!-- 视频文件上传 -->
          <div class="field">
            <label class="field-label">视频文件 <span class="required">*</span></label>
            <div class="upload-area" @click="videoInput.click()">
              <div class="upload-area-content">
                <i class="icon-cloud-upload"></i>
                <p class="upload-main-text">点击上传视频</p>
                <p class="upload-sub-text">支持 MP4、WebM 等格式</p>
                <p v-if="videoFile" class="upload-file-name">{{ videoFile.name }}</p>
                <p v-else class="upload-file-name">未选择任何文件</p>
              </div>
              <input
                ref="videoInput"
                type="file"
                accept="video/*"
                @change="onVideoChange"
                class="file-input"
              />
            </div>
          </div>
          
          <!-- 视频上传状态 -->
          <div v-if="videoFile" class="upload-status">
            <div class="status-header">
              <span class="status-title">视频信息</span>
              <span class="status-badge" :class="{ success: uploadComplete, uploading: uploading }">
                {{ uploadComplete ? '✓ 已就绪' : uploading ? '上传中...' : '待上传' }}
              </span>
            </div>
            <div class="status-info">
              <div class="info-item">
                <span class="info-label">文件大小：</span>
                <span class="info-value">{{ formatFileSize(videoFile.size) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">文件类型：</span>
                <span class="info-value">{{ videoFile.type || '未知' }}</span>
              </div>
            </div>
            <!-- 上传进度条 -->
            <div v-if="uploading" class="upload-progress">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: uploadProgress + '%' }"></div>
              </div>
              <p class="progress-text">{{ uploadProgress }}%</p>
            </div>
          </div>
          
          <!-- 封面上传 -->
          <div class="field">
            <label class="field-label">封面（可选）</label>
            <div class="cover-upload-container">
              <div class="cover-upload-area" @click="coverInput.click()">
                <div v-if="coverFile" class="cover-preview">
                  <img :src="URL.createObjectURL(coverFile)" alt="封面预览" />
                  <button type="button" class="remove-cover" @click.stop="coverFile = null">×</button>
                </div>
                <div v-else class="cover-upload-content">
                  <i class="icon-upload"></i>
                  <p>上传封面</p>
                  <p class="cover-hint">16:9 比例最佳</p>
                </div>
                <input
                  ref="coverInput"
                  type="file"
                  accept="image/*"
                  @change="onCoverChange"
                  class="file-input"
                />
              </div>
              <p class="cover-hint">未选择任何文件</p>
            </div>
          </div>
        </div>
      </div>
      
      <!-- 右侧：信息编辑区 -->
      <div class="upload-right">
        <div class="upload-form-card">
          <div class="form-title">视频信息</div>
          
          <!-- 标题 -->
          <div class="field">
            <label class="field-label">标题 <span class="required">*</span></label>
            <div class="title-input-wrapper">
              <input 
                v-model="form.title" 
                type="text" 
                placeholder="请输入视频标题" 
                class="title-input" 
                required 
                @input="onTitleInput"
              />
              <span class="title-count">{{ form.title.length }}/50</span>
            </div>
          </div>
          
          <!-- 视频分类 -->
          <div class="field">
            <label class="field-label">视频分类 <span class="required">*</span></label>
            <div class="category-selector">
              <div class="category-level">
                <span class="category-label">大类：</span>
                <select v-model.number="form.parentCategoryId" @change="onParentChange" class="category-select">
                  <option value="">请选择一级分类</option>
                  <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
                <span class="category-label">小类：</span>
                <select v-model.number="form.categoryId" :disabled="!childCategories.length" class="category-select">
                  <option value="">请选择二级分类</option>
                  <option v-for="c in childCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
              </div>
            </div>
            <p v-if="categoryError" class="error-text">{{ categoryError }}</p>
          </div>
          
          <!-- 标签 -->
          <div class="field">
            <label class="field-label">标签 <span class="required">*</span></label>
            <div class="tag-section">
              <!-- 已选标签显示 -->
              <div v-if="form.tagIds.length > 0" class="selected-tags">
                <span v-for="tagId in form.tagIds" :key="tagId" class="selected-tag">
                  {{ getTagName(tagId) }}
                  <button type="button" class="remove-tag" @click="removeTag(tagId)">×</button>
                </span>
              </div>
              <!-- 空状态提示 -->
              <div v-if="form.tagIds.length === 0 && !form.title" class="tag-empty-state">
                <i class="icon-topic"></i>
                <p>输入标题后，AI 将为你精准匹配标签</p>
              </div>
              
              <!-- 双模式推荐系统 -->
              <div class="tag-recommendation">
                <!-- 智能推荐 -->
                <div class="recommendation-section">
                  <div class="section-header">
                    <h4>智能推荐</h4>
                    <div class="section-actions">
                      <button type="button" class="btn-small" @click="selectAllRecommended" :disabled="!recommendedTags.length">
                        全选
                      </button>
                      <button type="button" class="btn-small" @click="refreshRecommendations" :disabled="loadingSuggest || !form.title">
                        刷新
                      </button>
                    </div>
                  </div>
                  <div class="recommended-tags">
                    <div v-if="loadingSuggest" class="loading-state">
                      <p>AI 正在分析...</p>
                    </div>
                    <div v-else-if="recommendedTags.length === 0 && form.title" class="empty-recommendations">
                      <p>点击刷新获取推荐标签</p>
                    </div>
                    <div v-else class="tag-pairs">
                      <div v-for="(pair, index) in recommendedTagPairs" :key="index" class="tag-pair">
                        <label 
                          v-for="tagId in pair" 
                          :key="tagId" 
                          class="tag recommended-tag"
                          :class="{ active: form.tagIds.includes(tagId) }"
                          @click="toggleTag(tagId)"
                        >
                          <span>{{ getTagName(tagId) }}</span>
                          <span class="add-icon">+</span>
                        </label>
                      </div>
                    </div>
                  </div>
                </div>
                
                <!-- 常用/热门推荐 -->
                <div class="recommendation-section">
                  <h4>常用/热门</h4>
                  <div class="popular-tags">
                    <label 
                      v-for="tag in popularTags" 
                      :key="tag.id" 
                      class="tag popular-tag"
                      :class="{ 
                        active: form.tagIds.includes(tag.id),
                        'category-tech': tag.category === 'tech',
                        'category-entertainment': tag.category === 'entertainment',
                        'category-life': tag.category === 'life'
                      }"
                      @click="toggleTag(tag.id)"
                    >
                      <span>{{ tag.name }}</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>
            <p v-if="tagError" class="error-text">{{ tagError }}</p>
          </div>
          
          <!-- 简介 -->
          <div class="field">
            <label class="field-label">简介（可选）</label>
            <div class="description-wrapper">
              <div class="tag-actions">
                <button type="button" class="btn-recommend" @click="runUploadAssist" :disabled="loadingAiAssist">
                  <i class="icon-ai"></i> {{ loadingAiAssist ? 'AI 分析中...' : 'AI 生成标题/标签/分类建议' }}
                </button>
                <button type="button" class="btn-polish" @click="polishDescription" :disabled="!form.description || loadingPolish">
                  <i class="icon-polish"></i> {{ loadingPolish ? '润色中...' : 'AI 润色' }}
                </button>
              </div>
              <div class="description-input-wrapper">
                <textarea 
                  v-model="form.description" 
                  placeholder="介绍一下你的视频吧~" 
                  rows="6" 
                  class="description-input"
                ></textarea>
                <span class="description-count">{{ form.description.length }}/2000</span>
              </div>
              <p v-if="aiAssistHint" class="success-text">{{ aiAssistHint }}</p>
            </div>
          </div>
          
          <!-- 错误信息 -->
          <div v-if="error" class="error-message">{{ error }}</div>
          
          <!-- 提交按钮 -->
          <button type="button" class="btn-submit" @click="submit" :disabled="loading">
            {{ loading ? '上传中...' : '提交投稿' }}
          </button>
        </div>
      </div>
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
const categoryError = ref('')
const tagError = ref('')
const loadingSuggest = ref(false)
const loadingAiAssist = ref(false)
const loadingPolish = ref(false)
const aiAssistHint = ref('')
const videoFile = ref(null)
const coverFile = ref(null)
const categories = ref([])
const tags = ref([])
const uploadProgress = ref(0)
const uploading = ref(false)
const uploadComplete = ref(false)
const aiThinking = ref(false)
const recommendedTags = ref([])
const popularTags = ref([])
const form = reactive({
  title: '',
  description: '',
  parentCategoryId: '',
  categoryId: '',
  tagIds: []
})

const childCategories = computed(() => {
  const parent = categories.value.find(c => c.id === form.parentCategoryId)
  return parent?.children || []
})

const recommendedTagPairs = computed(() => {
  // 将推荐标签按对分组
  const pairs = []
  for (let i = 0; i < recommendedTags.value.length; i += 2) {
    pairs.push(recommendedTags.value.slice(i, i + 2))
  }
  return pairs
})

function onVideoChange(e) {
  videoFile.value = e.target.files?.[0] || null
}

function onCoverChange(e) {
  coverFile.value = e.target.files?.[0] || null
}

function onParentChange() {
  form.categoryId = ''
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
  if (!tags.value.length) return
  loadingSuggest.value = true
  try {
    const ids = await recommendTagsApi({
      title: form.title || '',
      description: form.description || ''
    })
    form.tagIds = Array.isArray(ids) ? ids : []
    tagError.value = ''
  } catch (e) {
    tagError.value = e.message || '智能推荐失败'
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
  aiAssistHint.value = ''
  try {
    const candidateTags = tags.value.map(t => t.name)
    const candidateCategories = []
    categories.value.forEach(parent => {
      ;(parent.children || []).forEach(child => {
        candidateCategories.push({ id: child.id, name: child.name })
      })
    })

    const { data } = await createUploadAssistTask({
      title: form.title,
      description: form.description || '',
      candidate_tags: candidateTags,
      candidate_categories: candidateCategories
    })

    const done = await pollAgentTask(data.task_id)
    if (done.status !== 'success' || !done.result) {
      throw new Error(done.error || 'AI 分析失败')
    }

    const r = done.result
    const suggested = r.suggested_tags || []
    if (suggested.length) {
      form.tagIds = tags.value.filter(t => suggested.includes(t.name)).map(t => t.id)
    }

    if (r.suggested_category_id) {
      let foundParent = null
      for (const p of categories.value) {
        const hit = (p.children || []).find(c => c.id === r.suggested_category_id)
        if (hit) {
          foundParent = p
          break
        }
      }
      if (foundParent) {
        form.parentCategoryId = foundParent.id
        form.categoryId = r.suggested_category_id
      }
    }

    if (!form.description && r.generated_summary) {
      form.description = r.generated_summary
    }

    aiAssistHint.value = 'AI 建议已应用，你可以再手动微调后投稿'
    tagError.value = ''
    categoryError.value = ''
  } catch (e) {
    error.value = e.message || 'AI 助手执行失败'
  } finally {
    loadingAiAssist.value = false
  }
}

async function submit() {
  // 检查是否登录
  const token = sessionStorage.getItem('token')
  if (!token) {
    error.value = '请先登录后再投稿'
    return
  }
  
  if (!videoFile.value || !form.title.trim()) {
    error.value = '请选择视频并填写标题'
    return
  }
  if (!form.categoryId) {
    categoryError.value = '请选择视频分类'
    return
  }
  if (!form.tagIds.length) {
    tagError.value = '请至少选择一个标签'
    return
  }
  error.value = ''
  categoryError.value = ''
  tagError.value = ''
  loading.value = true
  try {
    const fd = new FormData()
    fd.append('video', videoFile.value)
    if (coverFile.value) fd.append('cover', coverFile.value)
    fd.append('title', form.title.trim())
    if (form.description.trim()) fd.append('description', form.description.trim())
    fd.append('categoryId', String(form.categoryId))
    form.tagIds.forEach(id => fd.append('tagIds', String(id)))
    const res = await uploadVideo(fd)
    alert('投稿成功')
    router.push(`/video/${res.id}`)
  } catch (e) {
    error.value = e.message || '上传失败'
  } finally {
    loading.value = false
  }
}

// 标题输入处理
function onTitleInput() {
  if (form.title.length > 0) {
    // 触发AI思考动画
    aiThinking.value = true
    setTimeout(() => {
      aiThinking.value = false
    }, 1000)
  }
}

// 获取标签名称
function getTagName(tagId) {
  let tag = tags.value.find(t => t.id === tagId)
  if (!tag) {
    tag = popularTags.value.find(t => t.id === tagId)
  }
  return tag ? tag.name : ''
}

// 移除标签
function removeTag(tagId) {
  const index = form.tagIds.indexOf(tagId)
  if (index > -1) {
    form.tagIds.splice(index, 1)
  }
}

// 切换标签选择
function toggleTag(tagId) {
  const index = form.tagIds.indexOf(tagId)
  if (index > -1) {
    form.tagIds.splice(index, 1)
  } else {
    form.tagIds.push(tagId)
  }
}

// 标签关联度判断
function isHighRelevance(tagId) {
  // 这里可以根据实际算法判断标签关联度
  return Math.random() > 0.7
}

function isLowRelevance(tagId) {
  // 这里可以根据实际算法判断标签关联度
  return Math.random() < 0.3
}

// AI润色功能
async function polishDescription() {
  if (!form.description) return
  loadingPolish.value = true
  try {
    // 模拟AI润色
    setTimeout(() => {
      form.description = form.description + '\n\n—— AI 润色后'
      loadingPolish.value = false
      aiAssistHint.value = 'AI 润色完成'
    }, 1000)
  } catch (e) {
    error.value = 'AI 润色失败'
    loadingPolish.value = false
  }
}

// 格式化文件大小
function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 全选推荐标签
function selectAllRecommended() {
  recommendedTags.value.forEach(tagId => {
    if (!form.tagIds.includes(tagId)) {
      form.tagIds.push(tagId)
    }
  })
}

// 刷新推荐标签
async function refreshRecommendations() {
  if (!form.title) return
  loadingSuggest.value = true
  try {
    const ids = await recommendTagsApi({
      title: form.title || '',
      description: form.description || ''
    })
    recommendedTags.value = Array.isArray(ids) ? ids : []
  } catch (e) {
    console.error('刷新推荐标签失败:', e)
  } finally {
    loadingSuggest.value = false
  }
}

// 加载热门标签
function loadPopularTags() {
  // 模拟热门标签数据
  popularTags.value = [
    { id: 1, name: '生活', category: 'life' },
    { id: 2, name: '科技', category: 'tech' },
    { id: 3, name: '娱乐', category: 'entertainment' },
    { id: 4, name: '游戏', category: 'entertainment' },
    { id: 5, name: '美食', category: 'life' },
    { id: 6, name: '教育', category: 'tech' },
    { id: 7, name: '音乐', category: 'entertainment' },
    { id: 8, name: '旅行', category: 'life' }
  ]
}

onMounted(() => {
  loadCategories()
  loadTags()
  loadPopularTags()
})
</script>

<style scoped>
/* 全局样式 */
:root {
  --bili-pink: #fb7299;
  --bili-pink-hover: #ff85ad;
  --bili-blue: #00a1d6;
  --bili-gray: #f5f5f5;
  --bili-border: #e5e5e5;
  --bili-text: #333;
  --bili-text-light: #666;
  --bili-text-lighter: #999;
  --bili-success: #67c23a;
  --bili-error: #f56c6c;
}

/* 容器样式 */
.upload-container {
  min-height: 100vh;
  background-color: var(--bili-gray);
}

/* 头部导航 */
.upload-header {
  background-color: #fff;
  border-bottom: 1px solid var(--bili-border);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-center {
  display: flex;
  align-items: center;
}

.header-title {
  font-size: 18px;
  font-weight: 500;
  color: var(--bili-text);
}

/* 主内容区 */
.upload-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
  display: flex;
  gap: 20px;
}

/* 左侧上传区 */
.upload-left {
  width: 50%;
  min-width: 400px;
}

/* 右侧信息编辑区 */
.upload-right {
  width: 50%;
  min-width: 400px;
}

/* 左侧栏内容优化 */
.upload-left .upload-form-card {
  padding: 30px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 600px;
}

/* 上传区域优化 */
.upload-area {
  min-height: 150px;
}

.upload-area-content {
  padding: 30px 20px;
}

/* 上传状态优化 */
.upload-status {
  margin-bottom: 0;
}

/* 封面上传区域优化 */
.cover-upload-container {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.cover-upload-area {
  border: 2px dashed var(--bili-border);
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #fafafa;
  position: relative;
  min-height: 150px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 确保封面上传区域填充整个宽度 */
.upload-left .field:last-child {
  margin-bottom: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.cover-upload-area:hover {
  border-color: var(--bili-blue);
  background-color: rgba(0, 161, 214, 0.05);
}

.cover-upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 0;
  width: 100%;
}

.cover-upload-content i {
  font-size: 48px;
  color: var(--bili-blue);
  margin-bottom: 0;
  transition: all 0.3s ease;
}

.cover-upload-area:hover .cover-upload-content i {
  transform: scale(1.1);
}

.cover-upload-content p {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--bili-text);
}

.cover-hint {
  font-size: 14px;
  color: var(--bili-text-light);
  text-align: center;
  margin-top: 4px;
  font-weight: 500;
}

/* 表单卡片 */
.upload-form-card {
  background-color: #fff;
  border-radius: 8px;
  padding: 30px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.form-title {
  font-size: 20px;
  font-weight: 500;
  color: var(--bili-text);
  margin-bottom: 24px;
  border-bottom: 1px solid var(--bili-border);
  padding-bottom: 16px;
}

/* 表单字段 */
.field {
  margin-bottom: 24px;
}

.field-label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: var(--bili-text);
  margin-bottom: 8px;
}

.required {
  color: var(--bili-error);
  margin-left: 4px;
}

/* 文件上传区域 */
.file-input {
  display: none;
}

.upload-area {
  border: 2px dashed var(--bili-border);
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #fafafa;
}

.upload-area:hover {
  border-color: var(--bili-blue);
  background-color: rgba(0, 161, 214, 0.05);
}

.upload-area-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.upload-area-content i {
  font-size: 48px;
  color: var(--bili-blue);
}

.upload-main-text {
  font-size: 16px;
  color: var(--bili-text);
  margin: 0;
}

.upload-sub-text {
  font-size: 14px;
  color: var(--bili-text-lighter);
  margin: 0;
}

.upload-file-name {
  font-size: 14px;
  color: var(--bili-text-light);
  margin: 8px 0 0 0;
  word-break: break-all;
}

/* 封面上传 */
.cover-upload-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.cover-upload-area {
  width: 100%;
  min-height: 150px;
  border: 2px dashed var(--bili-border);
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background-color: #fafafa;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-upload-area:hover {
  border-color: var(--bili-blue);
  background-color: rgba(0, 161, 214, 0.05);
}

.cover-upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 0;
  width: 100%;
}

.cover-upload-content i {
  font-size: 48px;
  color: var(--bili-blue);
  margin-bottom: 0;
  transition: all 0.3s ease;
}

.cover-upload-area:hover .cover-upload-content i {
  transform: scale(1.1);
}

.cover-upload-content p {
  font-size: 16px;
  font-weight: 500;
  margin-bottom: 4px;
  color: var(--bili-text);
}

.cover-preview {
  width: 100%;
  height: 100%;
  position: relative;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remove-cover {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.6);
  border: none;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: all 0.3s ease;
}

.remove-cover:hover {
  background-color: rgba(0, 0, 0, 0.8);
}

.cover-hint {
  font-size: 14px;
  color: var(--bili-text-light);
  margin: 0;
  text-align: center;
  font-weight: 500;
  width: 100%;
}

/* 分类选择器 */
.category-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-level {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.category-label {
  font-size: 14px;
  color: var(--bili-text-light);
  white-space: nowrap;
}

.category-select {
  padding: 8px 12px;
  border: 1px solid var(--bili-border);
  border-radius: 4px;
  font-size: 14px;
  color: var(--bili-text);
  min-width: 150px;
  transition: all 0.3s ease;
}

.category-select:focus {
  outline: none;
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.1);
}

/* 标题输入 */
.title-input-wrapper {
  position: relative;
}

.title-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--bili-border);
  border-radius: 10px;
  font-size: 14px;
  color: var(--bili-text);
  transition: all 0.3s ease;
  font-weight: 500;
}

.title-input:focus {
  outline: none;
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.1);
}

.title-count {
  position: absolute;
  bottom: 12px;
  right: 16px;
  font-size: 12px;
  color: var(--bili-text-lighter);
  pointer-events: none;
}

/* 标签部分 */
.tag-section {
  margin-bottom: 8px;
}

/* 已选标签 */
.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.selected-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background-color: var(--bili-pink);
  color: #fff;
  border-radius: 18px;
  font-size: 12px;
  font-weight: 500;
  box-shadow: 0 2px 4px rgba(251, 114, 153, 0.1);
  transition: all 0.3s ease;
}

.selected-tag:hover {
  background-color: var(--bili-pink-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(251, 114, 153, 0.2);
}

.remove-tag {
  background: none;
  border: none;
  color: #fff;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  padding: 0;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.3s ease;
  margin-left: 4px;
}

.remove-tag:hover {
  background-color: rgba(255, 255, 255, 0.3);
}

/* 标签空状态 */
.tag-empty-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px;
  background-color: #fafafa;
  border-radius: 12px;
  margin-bottom: 16px;
  border: 1px dashed var(--bili-border);
}

.tag-empty-state i {
  font-size: 24px;
  color: var(--bili-blue);
}

.tag-empty-state p {
  margin: 0;
  font-size: 14px;
  color: var(--bili-text-light);
  font-weight: 500;
}

/* 双模式推荐系统 */
.tag-recommendation {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.recommendation-section {
  background-color: #fafafa;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid var(--bili-border);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--bili-text);
}

.section-actions {
  display: flex;
  gap: 8px;
}

.btn-small {
  border: 1px solid var(--bili-border);
  background: #fff;
  color: var(--bili-text-light);
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-small:hover:not(:disabled) {
  border-color: var(--bili-blue);
  color: var(--bili-blue);
}

.btn-small:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 推荐标签 */
.recommended-tags {
  min-height: 80px;
}

.loading-state,
.empty-recommendations {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: var(--bili-text-light);
  font-size: 14px;
}

.tag-pairs {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tag-pair {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.recommended-tag {
  position: relative;
  padding: 8px 14px;
  border: 1px solid var(--bili-blue);
  border-radius: 18px;
  font-size: 12px;
  color: var(--bili-blue);
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.recommended-tag:hover {
  background-color: rgba(0, 161, 214, 0.1);
  transform: translateY(-1px);
}

.recommended-tag.active {
  background-color: var(--bili-blue);
  color: #fff;
}

.add-icon {
  font-size: 10px;
  font-weight: bold;
  background-color: var(--bili-blue);
  color: #fff;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.recommended-tag.active .add-icon {
  background-color: #fff;
  color: var(--bili-blue);
}

/* 热门标签 */
.popular-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.popular-tag {
  padding: 8px 14px;
  border-radius: 18px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-weight: 500;
}

.popular-tag.category-tech {
  border: 1px solid var(--bili-pink);
  color: var(--bili-pink);
}

.popular-tag.category-tech:hover {
  background-color: rgba(251, 114, 153, 0.1);
}

.popular-tag.category-tech.active {
  background-color: var(--bili-pink);
  color: #fff;
}

.popular-tag.category-entertainment {
  border: 1px solid var(--bili-pink);
  color: var(--bili-pink);
}

.popular-tag.category-entertainment:hover {
  background-color: rgba(251, 114, 153, 0.1);
}

.popular-tag.category-entertainment.active {
  background-color: var(--bili-pink);
  color: #fff;
}

.popular-tag.category-life {
  border: 1px solid var(--bili-pink);
  color: var(--bili-pink);
}

.popular-tag.category-life:hover {
  background-color: rgba(251, 114, 153, 0.1);
}

.popular-tag.category-life.active {
  background-color: var(--bili-pink);
  color: #fff;
}

.tag-checkbox {
  display: none;
}

/* 简介部分 */
.description-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.description-input-wrapper {
  position: relative;
}

.description-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--bili-border);
  border-radius: 10px;
  font-size: 14px;
  color: var(--bili-text);
  resize: vertical;
  min-height: 120px;
  transition: all 0.3s ease;
}

.description-input:focus {
  outline: none;
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.1);
}

.description-count {
  position: absolute;
  bottom: 12px;
  right: 16px;
  font-size: 12px;
  color: var(--bili-text-lighter);
  pointer-events: none;
}

/* 错误和提示信息 */
.error-text {
  font-size: 12px;
  color: var(--bili-error);
  margin-top: 4px;
  margin-bottom: 0;
}

.success-text {
  font-size: 12px;
  color: var(--bili-success);
  margin-top: 4px;
  margin-bottom: 0;
}

.error-message {
  font-size: 14px;
  color: var(--bili-error);
  margin: 16px 0;
  padding: 12px;
  background-color: rgba(245, 108, 108, 0.05);
  border: 1px solid rgba(245, 108, 108, 0.2);
  border-radius: 8px;
}

/* 提交按钮 */
.btn-submit {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(135deg, var(--bili-pink) 0%, #e85c8a 100%);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 16px;
  position: relative;
  overflow: hidden;
}

.btn-submit::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.6s ease;
}

.btn-submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(251, 114, 153, 0.4), 0 0 20px rgba(251, 114, 153, 0.2);
}

.btn-submit:hover::before {
  left: 100%;
}

.btn-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* 上传状态 */
.upload-status {
  margin: 16px 0;
  padding: 16px;
  background-color: #fafafa;
  border-radius: 10px;
  border: 1px solid var(--bili-border);
}

.status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.status-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--bili-text);
}

.status-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.success {
  background-color: rgba(103, 194, 58, 0.1);
  color: var(--bili-success);
}

.status-badge.uploading {
  background-color: rgba(0, 161, 214, 0.1);
  color: var(--bili-blue);
}

.status-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.info-item {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.info-label {
  color: var(--bili-text-light);
}

.info-value {
  color: var(--bili-text);
  font-weight: 500;
}

/* 上传进度 */
.upload-progress {
  margin-top: 12px;
}

.progress-bar {
  width: 100%;
  height: 8px;
  background-color: var(--bili-gray);
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 8px;
}

.progress-fill {
  height: 100%;
  background-color: var(--bili-blue);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 12px;
  color: var(--bili-text-light);
  text-align: center;
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 992px) {
  .upload-content {
    flex-direction: column;
  }
  
  .upload-left,
  .upload-right {
    width: 100%;
    min-width: unset;
  }
  
  .cover-upload-container {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .cover-upload-area {
    width: 100%;
  }
}

/* 图标样式 */
.icon-cloud-upload::before {
  content: "☁️";
  font-size: 48px;
}

.icon-upload::before {
  content: "📷";
  font-size: 24px;
}

.icon-ai::before {
  content: "🤖";
  font-size: 14px;
}

.icon-topic::before {
  content: "💬";
  font-size: 20px;
}

.icon-polish::before {
  content: "✨";
  font-size: 14px;
}

/* AI思考动画 */
.icon-ai.thinking {
  animation: thinking 1.5s infinite ease-in-out;
}

@keyframes thinking {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1) rotate(5deg);
  }
}

/* 表单卡片优化 */
.upload-form-card {
  background-color: #fff;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.3s ease;
}

.upload-form-card:hover {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

/* 分类选择器优化 */
.category-select {
  padding: 8px 12px;
  border: 1px solid var(--bili-border);
  border-radius: 8px;
  font-size: 14px;
  color: var(--bili-text);
  min-width: 150px;
  transition: all 0.3s ease;
}

.category-select:focus {
  outline: none;
  border-color: var(--bili-pink);
  box-shadow: 0 0 0 2px rgba(251, 114, 153, 0.1);
}

/* 左右卡片对齐优化 */
.upload-content {
  align-items: stretch;
  gap: 20px;
}

.upload-left,
.upload-right {
  display: flex;
  flex-direction: column;
}

.upload-form-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 600px;
}

.upload-form-card .field:last-child {
  margin-bottom: auto;
}
</style>
