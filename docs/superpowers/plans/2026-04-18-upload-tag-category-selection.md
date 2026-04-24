# Upload Tag & Category Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变投稿页整体视觉风格的前提下，优化分类与标签选择体验：分类支持“二级优先、一级可兜底”，标签改为“已选 / 智能推荐 / 当前分类相关 / 更多标签”的分层结构。

**Architecture:** 保持 [frontend/src/views/Upload.vue](frontend/src/views/Upload.vue) 作为页面入口，不改后端接口协议。新增一个前端配置文件维护标签主题分组与分类标签映射，再新增一个纯函数 helper 文件负责分类状态和标签分层计算，避免继续把所有选择逻辑堆进 Upload.vue。测试采用现有 `node:test` 风格，对 helper 逻辑做纯函数测试，再通过本地运行页面做视觉与交互验收。

**Tech Stack:** Vue 3 `<script setup>`、Vite、Node `node:test`、现有 Category/Tag API、本地规则标签推荐接口 `/tag/recommend`

---

## File Structure

### Files to Create

- `frontend/src/views/uploadTagConfig.js`
  - 投稿页标签主题分组、默认常用标签、分类到标签的前端配置映射
- `frontend/src/views/uploadTagCategoryState.js`
  - 纯函数：分类状态计算、分类提示文案、标签分层/分组计算
- `frontend/tests/uploadTagCategoryState.test.js`
  - helper 的单元测试，覆盖分类兜底、二级优先、标签分层和“更多标签”折叠逻辑

### Files to Modify

- `frontend/src/views/Upload.vue`
  - 调整分类区交互与状态显示
  - 调整标签区层次结构与文案
  - 接入新 helper / config
  - 保持现有视觉风格不变，仅新增少量状态提示样式
- `frontend/tests/uploadValidation.test.js`
  - 补一个回归测试，确保“一级分类作为 categoryId 也允许通过校验”

### Files to Reference

- `frontend/src/views/uploadValidation.js`
- `frontend/src/api/category.js`
- `frontend/src/api/tag.js`
- `backend/src/main/java/com/bilibili/video/controller/TagController.java`
- `docs/superpowers/specs/2026-04-18-upload-tag-category-selection-design.md`

---

### Task 1: 提取分类与标签配置及纯函数状态层

**Files:**
- Create: `frontend/src/views/uploadTagConfig.js`
- Create: `frontend/src/views/uploadTagCategoryState.js`
- Test: `frontend/tests/uploadTagCategoryState.test.js`

- [ ] **Step 1: 写失败测试，锁定分类与标签分层规则**

```js
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveCategorySelectionState,
  buildUploadTagSections
} from '../src/views/uploadTagCategoryState.js'

const categories = [
  {
    id: 5,
    name: '科技',
    children: [
      { id: 33, name: '前端' },
      { id: 34, name: '后端' }
    ]
  },
  {
    id: 14,
    name: 'Vlog',
    children: []
  }
]

const tags = [
  { id: 1, name: 'Java' },
  { id: 2, name: 'SpringBoot' },
  { id: 3, name: 'Vue' },
  { id: 4, name: 'Redis' },
  { id: 5, name: '教程' },
  { id: 6, name: 'Vlog' }
]

test('category state prefers parent fallback when child not chosen', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 5,
    categoryId: 5
  })

  assert.equal(state.selectedParentCategoryId, 5)
  assert.equal(state.selectedCategoryLabel, '科技')
  assert.equal(state.categoryHintText, '可继续细分到二级分类，也可直接使用一级分类')
})

test('category state builds full label when child chosen', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 5,
    categoryId: 34
  })

  assert.equal(state.selectedCategoryLabel, '科技 / 后端')
  assert.equal(state.categoryHintText, '')
})

test('tag sections prioritize selected recommended and category-related tags', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [1],
    suggestedTagIds: [2],
    categoryLabel: '科技 / 后端',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(sections.selectedTags.map(item => item.name), ['Java'])
  assert.deepEqual(sections.recommendedTags.map(item => item.name), ['SpringBoot'])
  assert.ok(sections.categoryTags.some(item => item.name === 'Redis'))
  assert.ok(sections.moreTagGroups.length > 0)
})

test('tag sections hide category group when category missing', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '',
    keyword: '',
    showAllTags: false
  })

  assert.equal(sections.categoryTags.length, 0)
})
```

- [ ] **Step 2: 运行测试并确认当前失败**

Run:
```bash
cd frontend && node --test tests/uploadTagCategoryState.test.js
```

Expected:
- FAIL
- 报错类似 `Cannot find module '../src/views/uploadTagCategoryState.js'`

- [ ] **Step 3: 创建投稿页标签配置文件**

```js
// frontend/src/views/uploadTagConfig.js
export const uploadTagGroups = [
  {
    key: 'language',
    label: '编程语言',
    tagNames: ['Java', 'Vue', 'React']
  },
  {
    key: 'stack',
    label: '框架 / 中间件',
    tagNames: ['SpringBoot', 'MySQL', 'Redis', 'Docker']
  },
  {
    key: 'content',
    label: '内容形式',
    tagNames: ['教程', '评测', '开箱', '攻略', 'Vlog']
  },
  {
    key: 'direction',
    label: '方向 / 场景',
    tagNames: ['前端', '后端', '人工智能', '机器学习', '数据库']
  }
]

export const defaultTagNames = ['教程', '评测', '开箱', 'Vlog']

export const categoryTagMap = {
  '科技': ['Java', 'SpringBoot', 'Vue', 'React', 'MySQL', 'Redis', 'Docker', '前端', '后端', '人工智能', '机器学习', '数据库', '教程'],
  '科技 / 前端': ['Vue', 'React', '前端', '教程'],
  '科技 / 后端': ['Java', 'SpringBoot', 'MySQL', 'Redis', 'Docker', '后端', '教程'],
  'Vlog': ['Vlog', '日常', '校园', '旅行']
}
```

- [ ] **Step 4: 创建纯函数 helper，封装分类状态与标签分层逻辑**

```js
// frontend/src/views/uploadTagCategoryState.js
import { categoryTagMap, defaultTagNames, uploadTagGroups } from './uploadTagConfig'

function findParentAndChild(categories, categoryId) {
  for (const parent of categories || []) {
    if (parent.id === categoryId) {
      return { parent, child: null }
    }
    const child = (parent.children || []).find(item => item.id === categoryId)
    if (child) {
      return { parent, child }
    }
  }
  return { parent: null, child: null }
}

export function resolveCategorySelectionState({ categories, selectedParentCategoryId, categoryId }) {
  const { parent, child } = findParentAndChild(categories, categoryId)
  const activeParent = categories?.find(item => item.id === selectedParentCategoryId) || parent || null
  const hasChildren = Array.isArray(activeParent?.children) && activeParent.children.length > 0

  let selectedCategoryLabel = ''
  if (child) selectedCategoryLabel = `${parent.name} / ${child.name}`
  else if (parent) selectedCategoryLabel = parent.name

  const categoryHintText = hasChildren && activeParent?.id === categoryId
    ? '可继续细分到二级分类，也可直接使用一级分类'
    : ''

  return {
    selectedParentCategoryId: activeParent?.id ?? null,
    activeParentCategory: activeParent,
    activeChildCategories: hasChildren ? activeParent.children : [],
    selectedCategoryLabel,
    categoryHintText
  }
}

export function buildUploadTagSections({ tags, selectedTagIds, suggestedTagIds, categoryLabel, keyword, showAllTags, defaultVisibleCount = 18 }) {
  const selectedIdSet = new Set(selectedTagIds || [])
  const suggestedIdSet = new Set(suggestedTagIds || [])
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()

  const selectedTags = (tags || []).filter(tag => selectedIdSet.has(tag.id))
  const recommendedTags = (tags || []).filter(tag => suggestedIdSet.has(tag.id) && !selectedIdSet.has(tag.id))

  const categoryNames = categoryTagMap[categoryLabel] || categoryTagMap[categoryLabel.split(' / ')[0]] || []
  const categoryNameSet = new Set(categoryNames)
  const categoryTags = (tags || []).filter(tag => categoryNameSet.has(tag.name) && !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id))

  let remaining = (tags || []).filter(tag => !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id) && !categoryNameSet.has(tag.name))
  if (normalizedKeyword) {
    remaining = remaining.filter(tag => tag.name.toLowerCase().includes(normalizedKeyword))
  }

  const visibleRemaining = showAllTags ? remaining : remaining.slice(0, defaultVisibleCount)

  const moreTagGroups = uploadTagGroups
    .map(group => ({
      ...group,
      tags: visibleRemaining.filter(tag => group.tagNames.includes(tag.name))
    }))
    .filter(group => group.tags.length > 0)

  const ungroupedTags = visibleRemaining.filter(tag => {
    return !uploadTagGroups.some(group => group.tagNames.includes(tag.name))
  })

  if (ungroupedTags.length > 0) {
    moreTagGroups.push({ key: 'other', label: '其他标签', tags: ungroupedTags })
  }

  const defaultTags = (tags || []).filter(tag => defaultTagNames.includes(tag.name) && !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id))

  return {
    selectedTags,
    recommendedTags,
    categoryTags,
    defaultTags,
    moreTagGroups,
    hasMoreTags: remaining.length > defaultVisibleCount,
    remainingTagCount: remaining.length
  }
}
```

- [ ] **Step 5: 运行测试，确认 helper 逻辑通过**

Run:
```bash
cd frontend && node --test tests/uploadTagCategoryState.test.js
```

Expected:
- PASS
- 4 tests passing

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/uploadTagConfig.js frontend/src/views/uploadTagCategoryState.js frontend/tests/uploadTagCategoryState.test.js
git commit -m "feat: add upload tag and category state helpers"
```

---

### Task 2: 重构 Upload.vue 的分类与标签交互

**Files:**
- Modify: `frontend/src/views/Upload.vue`
- Reference: `frontend/src/api/category.js`
- Reference: `frontend/src/api/tag.js`

- [ ] **Step 1: 先写失败测试，补“一级分类可兜底”的校验回归**

```js
// frontend/tests/uploadValidation.test.js

test('accepts first-level category id when category is selected', () => {
  const form = validForm()
  form.categoryId = 5

  assert.equal(validateUploadForm?.(form), '')
})
```

- [ ] **Step 2: 运行测试，确认当前行为已被覆盖**

Run:
```bash
cd frontend && node --test tests/uploadValidation.test.js
```

Expected:
- PASS
- 新增测试通过，确保后续改 Upload.vue 时不破坏基础校验

- [ ] **Step 3: 在 Upload.vue 中接入 helper 和配置，替换旧的分类/标签计算**

```js
// Upload.vue <script setup>
import {
  buildUploadTagSections,
  resolveCategorySelectionState
} from './uploadTagCategoryState'

const categoryState = computed(() => resolveCategorySelectionState({
  categories: parentCategories.value,
  selectedParentCategoryId: selectedParentCategoryId.value,
  categoryId: form.categoryId
}))

const selectedCategoryLabel = computed(() => categoryState.value.selectedCategoryLabel)
const activeParentCategory = computed(() => categoryState.value.activeParentCategory)
const activeChildCategories = computed(() => categoryState.value.activeChildCategories)
const categoryHintText = computed(() => categoryState.value.categoryHintText)

const tagSections = computed(() => buildUploadTagSections({
  tags: tags.value,
  selectedTagIds: form.tagIds,
  suggestedTagIds: aiSuggestedTagIds.value,
  categoryLabel: selectedCategoryLabel.value,
  keyword: tagKeyword.value,
  showAllTags: showAllTags.value,
  defaultVisibleCount
}))

const selectedTags = computed(() => tagSections.value.selectedTags)
const recommendedTags = computed(() => tagSections.value.recommendedTags)
const categoryRelatedTags = computed(() => tagSections.value.categoryTags)
const groupedMoreTags = computed(() => tagSections.value.moreTagGroups)
const hasMoreTags = computed(() => tagSections.value.hasMoreTags)
```

- [ ] **Step 4: 更新分类区模板，保留现有风格但增强状态表达**

```vue
<div class="card-header card-header--stacked">
  <div class="card-header-main">
    <span class="card-icon">📂</span>
    <span class="card-title">分类选择</span>
    <span v-if="selectedCategoryLabel" class="card-selected-label">{{ selectedCategoryLabel }}</span>
  </div>
  <p v-if="categoryHintText" class="card-helper-text">{{ categoryHintText }}</p>
</div>

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
      :class="{ active: selectedParentCategoryId === parent.id }"
      @click="selectParentCategory(parent)"
    >
      {{ parent.name }}
    </button>
  </div>
</div>

<div v-if="activeChildCategories.length > 0" class="tag-group">
  <div class="tag-group-header">
    <span class="tag-group-label">二级分区（推荐）</span>
    <button type="button" class="link-btn" @click="selectCurrentParentAsCategory">直接使用一级分类</button>
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
```

- [ ] **Step 5: 更新标签区模板，改为“已选 / 智能推荐 / 当前分类相关 / 更多标签”四层**

```vue
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

<div v-if="recommendedTags.length > 0" class="tag-group">
  <div class="tag-group-header">
    <span class="tag-group-label ai-label">✨ 智能推荐</span>
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

<div v-if="categoryRelatedTags.length > 0" class="tag-group">
  <div class="tag-group-header">
    <span class="tag-group-label">当前分类相关标签</span>
  </div>
  <div class="tag-list">
    <button
      v-for="tag in categoryRelatedTags"
      :key="`category-${tag.id}`"
      type="button"
      class="tag-chip"
      @click="toggleTag(tag.id)"
    >
      {{ tag.name }}
    </button>
  </div>
</div>

<div class="tag-group">
  <div class="tag-group-header">
    <span class="tag-group-label">更多标签</span>
    <button v-if="hasMoreTags" type="button" class="expand-btn" @click="showAllTags = !showAllTags">
      {{ showAllTags ? '▲ 收起' : '▼ 展开更多' }}
    </button>
  </div>
  <input v-model.trim="tagKeyword" class="form-input tag-search" type="text" maxlength="20" placeholder="🔍 搜索标签..." />

  <div v-for="group in groupedMoreTags" :key="group.key" class="nested-tag-group">
    <p class="nested-tag-group-label">{{ group.label }}</p>
    <div class="tag-list">
      <button
        v-for="tag in group.tags"
        :key="`${group.key}-${tag.id}`"
        type="button"
        class="tag-chip"
        @click="toggleTag(tag.id)"
      >
        {{ tag.name }}
      </button>
    </div>
  </div>
</div>
```

- [ ] **Step 6: 微调方法实现，确保一级兜底和推荐标签不再自动强塞入已选**

```js
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

async function recommendTags() {
  if (!form.title.trim() && !form.description.trim()) {
    error.value = '请先填写标题或简介，再获取推荐标签'
    return
  }
  loadingSuggest.value = true
  error.value = ''
  try {
    const ids = await recommendTagsApi({
      title: form.title || '',
      description: form.description || ''
    })
    aiSuggestedTagIds.value = Array.isArray(ids) ? [...new Set(ids)] : []
  } catch (e) {
    error.value = e.message || '推荐标签失败'
  } finally {
    loadingSuggest.value = false
  }
}
```

- [ ] **Step 7: 补充最小样式，不改整体视觉语言**

```css
.card-header--stacked {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.card-header-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-helper-text {
  margin: 0;
  font-size: 12px;
  color: #64748b;
}

.link-btn {
  border: none;
  background: transparent;
  color: #4f46e5;
  font-size: 12px;
  cursor: pointer;
}

.nested-tag-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nested-tag-group-label {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}
```

- [ ] **Step 8: 运行测试并确认逻辑未回归**

Run:
```bash
cd frontend && node --test tests/uploadValidation.test.js tests/uploadTagCategoryState.test.js
```

Expected:
- PASS
- 现有校验测试 + 新 helper 测试全部通过

- [ ] **Step 9: Commit**

```bash
git add frontend/src/views/Upload.vue frontend/tests/uploadValidation.test.js
git commit -m "feat: improve upload category and tag selection"
```

---

### Task 3: 浏览器验收与收尾

**Files:**
- Modify: `frontend/src/views/Upload.vue`（仅在验收发现轻微文案/样式问题时）
- Reference: `frontend/src/router/index.js`

- [ ] **Step 1: 启动前端开发服务器**

Run:
```bash
cd frontend && npm run dev
```

Expected:
- 输出 Vite 本地地址，例如 `http://localhost:5173/`

- [ ] **Step 2: 打开投稿页并走分类主路径**

Manual path:
```text
1. 登录前端
2. 打开 /upload
3. 点击“科技”一级分类
4. 确认出现“可继续细分到二级分类，也可直接使用一级分类”提示
5. 不选二级直接提交前，确认分类状态显示为“科技”
6. 再切换为“科技 / 后端”，确认状态显示完整路径
```

Expected:
- 一级分类可直接作为最终分类
- 二级分类仍然更显眼、更容易继续细分

- [ ] **Step 3: 验证标签分层顺序和文案**

Manual path:
```text
1. 输入标题和简介
2. 点击“智能推荐”按钮
3. 确认推荐区文案不再包含“AI”
4. 选择“科技 / 后端”后，确认“当前分类相关标签”模块出现
5. 确认“更多标签”默认折叠，展开后按分组展示
```

Expected:
- 标签顺序为：已选 -> 智能推荐 -> 当前分类相关 -> 更多标签
- 不再出现“AI 推荐”字样

- [ ] **Step 4: 做一个轻量修正提交（如需要）**

```bash
git add frontend/src/views/Upload.vue
git commit -m "fix: polish upload taxonomy interaction copy"
```

说明：只有在手工验收中发现真实问题时才执行这一步；如果无需修正，跳过提交。

---

## Spec Coverage Self-Review

- 已覆盖分类“二级优先、一级可兜底”：Task 1/Task 2
- 已覆盖标签四层结构：Task 1/Task 2
- 已覆盖“移除 AI 表述，保留自动推荐能力”：Task 2
- 已覆盖“分类与标签联动”：Task 1/Task 2
- 已覆盖“不改变整体 UI 风格”：Task 2/Task 3
- 无占位符、无 TBD、无未落地的测试项

---

## Execution Notes

- 本计划优先采用**前端配置驱动**，不修改后端接口协议
- 如果后续发现分类标签映射维护成本过高，再单独立项把标签主题组/分类标签映射迁到后端元数据
- 当前 `Upload.vue` 已较大，本计划通过新增 helper/config 文件避免继续把逻辑堆入页面组件
