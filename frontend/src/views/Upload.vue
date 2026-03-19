<template>
  <div class="upload-page">
    <div class="upload-card">
      <h2>投稿视频</h2>
      <form @submit.prevent="submit" class="form">
        <div class="field">
          <label>视频文件 <span class="required">*</span></label>
          <input
            ref="videoInput"
            type="file"
            accept="video/*"
            @change="onVideoChange"
          />
        </div>
        <div class="field">
          <label>封面（可选）</label>
          <input
            ref="coverInput"
            type="file"
            accept="image/*"
            @change="onCoverChange"
          />
        </div>
        <div class="field">
          <label>视频分类 <span class="required">*</span></label>
          <div class="category-select">
            <select v-model.number="form.parentCategoryId" @change="onParentChange">
              <option value="">请选择一级分类</option>
              <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
            <select v-model.number="form.categoryId" :disabled="!childCategories.length">
              <option value="">请选择二级分类</option>
              <option v-for="c in childCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
            </select>
          </div>
          <p v-if="categoryError" class="hint">{{ categoryError }}</p>
        </div>
        <div class="field">
          <label>标题 <span class="required">*</span></label>
          <input v-model="form.title" type="text" placeholder="请输入视频标题" required />
        </div>
        <div class="field">
          <label>标签 <span class="required">*</span></label>
          <div class="tag-actions">
            <button type="button" class="btn-recommend" @click="recommendTags" :disabled="loadingSuggest">{{ loadingSuggest ? '推荐中...' : '智能推荐标签' }}</button>
          </div>
          <div class="tag-list">
            <label v-for="t in tags" :key="t.id" class="tag-item">
              <input type="checkbox" :value="t.id" v-model="form.tagIds" />
              <span>{{ t.name }}</span>
            </label>
          </div>
          <p v-if="tagError" class="hint">{{ tagError }}</p>
        </div>
        <div class="field">
          <label>简介（可选）</label>
          <textarea v-model="form.description" placeholder="介绍一下你的视频吧~" rows="4"></textarea>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" class="btn-submit" :disabled="loading">
          {{ loading ? '上传中...' : '提交投稿' }}
        </button>
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

const router = useRouter()
const videoInput = ref(null)
const coverInput = ref(null)
const loading = ref(false)
const error = ref('')
const categoryError = ref('')
const tagError = ref('')
const loadingSuggest = ref(false)
const videoFile = ref(null)
const coverFile = ref(null)
const categories = ref([])
const tags = ref([])
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

async function submit() {
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

onMounted(() => {
  loadCategories()
  loadTags()
})
</script>

<style scoped>
.upload-page {
  max-width: 600px;
  margin: 0 auto;
}

.upload-card {
  padding: 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: var(--card-shadow);
}

.upload-card h2 {
  font-size: 22px;
  margin-bottom: 24px;
}

.field {
  margin-bottom: 20px;
}

.field label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.required {
  color: #f56c6c;
}

.field input[type="text"],
.field input[type="file"],
.field textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  font-size: 14px;
}

.field input:focus,
.field textarea:focus {
  outline: none;
  border-color: var(--bili-pink);
}

.field input[type="file"] {
  padding: 8px;
}

.error {
  color: #f56c6c;
  font-size: 14px;
  margin-bottom: 12px;
}

.btn-submit {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  background: var(--bili-pink);
  border-radius: 8px;
  margin-top: 8px;
}

.btn-submit:hover:not(:disabled) {
  background: var(--bili-pink-hover);
}

.btn-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
</style>
