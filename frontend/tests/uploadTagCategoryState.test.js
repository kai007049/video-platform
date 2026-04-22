import test from 'node:test'
import assert from 'node:assert/strict'

import {
  resolveCategorySelectionState,
  buildUploadTagSections
} from '../src/views/uploadTagCategoryState.js'
import { uploadTagGroups, categoryTagMap } from '../src/views/uploadTagConfig.js'

const categories = [
  {
    id: 3,
    name: '科技',
    children: [
      { id: 18, name: '编程开发' },
      { id: 19, name: '人工智能' },
      { id: 20, name: '数码评测' }
    ]
  },
  {
    id: 4,
    name: '知识',
    children: [
      { id: 21, name: '科普' }
    ]
  },
  {
    id: 2,
    name: '游戏',
    children: [
      { id: 17, name: '电竞' }
    ]
  },
  {
    id: 6,
    name: '影视',
    children: [
      { id: 28, name: '电影' }
    ]
  },
  {
    id: 10,
    name: 'Vlog',
    children: [
      { id: 40, name: '出行' },
      { id: 41, name: '城市记录' },
      { id: 42, name: '个人日常' }
    ]
  }
]

const tags = [
  { id: 1, name: '编程开发' },
  { id: 2, name: 'Java' },
  { id: 3, name: 'SpringBoot' },
  { id: 4, name: 'Redis' },
  { id: 5, name: '教程' },
  { id: 6, name: '实战' },
  { id: 7, name: '人工智能' },
  { id: 8, name: '机器学习' },
  { id: 9, name: '算法' },
  { id: 10, name: '干货' },
  { id: 11, name: 'Vlog' },
  { id: 12, name: '探店' },
  { id: 13, name: '剪辑' },
  { id: 14, name: '科普' },
  { id: 15, name: '复盘' },
  { id: 16, name: '解说' },
  { id: 17, name: '电竞' },
  { id: 18, name: '攻略' },
  { id: 19, name: '集锦' },
  { id: 21, name: '剧情' },
  { id: 22, name: '影视解说' },
  { id: 23, name: 'Vue' },
  { id: 24, name: '系统设计' },
  { id: 25, name: '未分组标签A' },
  { id: 26, name: 'Python' },
  { id: 27, name: 'TypeScript' },
  { id: 28, name: 'Node.js' },
  { id: 29, name: 'PostgreSQL' },
  { id: 30, name: 'MongoDB' },
  { id: 31, name: 'Kubernetes' },
  { id: 32, name: 'Maven' },
  { id: 33, name: 'Gradle' },
  { id: 34, name: 'RabbitMQ' },
  { id: 35, name: 'DevOps' },
  { id: 36, name: 'CI/CD' },
  { id: 37, name: '原理' },
  { id: 38, name: '源码' },
  { id: 39, name: '调优' },
  { id: 40, name: '避坑' },
  { id: 41, name: '盘点' },
  { id: 42, name: '高光' },
  { id: 43, name: '阅读' },
  { id: 44, name: '书评' },
  { id: 45, name: '职场' },
  { id: 46, name: '成长' },
  { id: 47, name: '效率' },
  { id: 48, name: '摄影' },
  { id: 49, name: '旅行' },
  { id: 50, name: '影评' },
  { id: 51, name: '吐槽' },
  { id: 52, name: '配音' },
  { id: 53, name: '混剪' },
  { id: 54, name: '名场面' },
  { id: 55, name: '高能' },
  { id: 56, name: '催泪' },
  { id: 57, name: '上手' },
  { id: 58, name: '版本解析' },
  { id: 59, name: '开荒' }
]

function flattenGroupTagNames(groups) {
  return groups.flatMap((group) => group.tags.map((tag) => tag.name))
}

function resetCategoryDerivedState(state) {
  state.aiSuggestedTagIds = []
  state.tagKeyword = ''
  state.showAllTags = false
}

const collapsedRemainingTags = [
  { id: 101, name: 'Vue' },
  { id: 102, name: 'Vlog' },
  { id: 103, name: '系统设计' },
  { id: 104, name: '剧情' },
  { id: 105, name: '未分组标签A' }
]

test('upload tag config keeps film taxonomy aligned with backend tag seeds', () => {
  const styleGroup = uploadTagGroups.find((group) => group.key === 'style')
  const techGroup = uploadTagGroups.find((group) => group.key === 'tech')
  const topicGroup = uploadTagGroups.find((group) => group.key === 'topic')
  const formatGroup = uploadTagGroups.find((group) => group.key === 'format')

  assert.ok(styleGroup)
  assert.ok(techGroup)
  assert.ok(topicGroup)
  assert.ok(formatGroup)
  assert.ok(!styleGroup.tagNames.includes('电影'))
  assert.ok(!styleGroup.tagNames.includes('电视剧'))
  assert.ok(!styleGroup.tagNames.includes('纪录片'))
  assert.ok(styleGroup.tagNames.includes('影评'))
  assert.ok(styleGroup.tagNames.includes('吐槽'))
  assert.ok(styleGroup.tagNames.includes('配音'))
  assert.ok(styleGroup.tagNames.includes('混剪'))
  assert.ok(styleGroup.tagNames.includes('名场面'))
  assert.ok(styleGroup.tagNames.includes('高能'))
  assert.ok(styleGroup.tagNames.includes('催泪'))
  assert.ok(techGroup.tagNames.includes('Python'))
  assert.ok(techGroup.tagNames.includes('TypeScript'))
  assert.ok(techGroup.tagNames.includes('Node.js'))
  assert.ok(techGroup.tagNames.includes('PostgreSQL'))
  assert.ok(techGroup.tagNames.includes('MongoDB'))
  assert.ok(techGroup.tagNames.includes('Kubernetes'))
  assert.ok(techGroup.tagNames.includes('RabbitMQ'))
  assert.ok(techGroup.tagNames.includes('DevOps'))
  assert.ok(techGroup.tagNames.includes('CI/CD'))
  assert.ok(topicGroup.tagNames.includes('阅读'))
  assert.ok(topicGroup.tagNames.includes('书评'))
  assert.ok(topicGroup.tagNames.includes('职场'))
  assert.ok(topicGroup.tagNames.includes('成长'))
  assert.ok(topicGroup.tagNames.includes('效率'))
  assert.ok(topicGroup.tagNames.includes('摄影'))
  assert.ok(topicGroup.tagNames.includes('旅行'))
  assert.ok(formatGroup.tagNames.includes('原理'))
  assert.ok(formatGroup.tagNames.includes('源码'))
  assert.ok(formatGroup.tagNames.includes('调优'))
  assert.ok(formatGroup.tagNames.includes('避坑'))
  assert.ok(formatGroup.tagNames.includes('盘点'))
  assert.ok(formatGroup.tagNames.includes('高光'))
  assert.ok(formatGroup.tagNames.includes('上手'))
  assert.ok(formatGroup.tagNames.includes('版本解析'))
  assert.ok(formatGroup.tagNames.includes('开荒'))
  assert.deepEqual(categoryTagMap['影视 / 电影'], ['剧情', '解说', '影视解说', '剪辑', '混剪', '影评', '吐槽', '配音', '名场面', '高能', '催泪'])
})

test('category state prefers parent fallback when child not chosen', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 3,
    categoryId: 3
  })

  assert.equal(state.selectedParentCategoryId, 3)
  assert.equal(state.selectedCategoryLabel, '科技')
  assert.equal(state.categoryHintText, '可继续细分到二级分类，也可直接使用一级分类')
})

test('category state builds full label for programming development', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 3,
    categoryId: 18
  })

  assert.equal(state.selectedCategoryLabel, '科技 / 编程开发')
  assert.equal(state.categoryHintText, '')
})

test('category state resolves ai category when parent selection conflicts', () => {
  const state = resolveCategorySelectionState({
    categories,
    selectedParentCategoryId: 10,
    categoryId: 19
  })

  assert.equal(state.selectedParentCategoryId, 3)
  assert.equal(state.activeParentCategory?.name, '科技')
  assert.deepEqual(state.activeChildCategories.map((item) => item.name), ['编程开发', '人工智能', '数码评测'])
  assert.equal(state.selectedCategoryLabel, '科技 / 人工智能')
  assert.equal(state.categoryHintText, '')
})

test('reset category derived state clears ai suggestions and ui-only filters without touching manual tag ids', () => {
  const state = {
    aiSuggestedTagIds: [2, 4],
    tagKeyword: '后端',
    showAllTags: true,
    formTagIds: [1, 7]
  }

  resetCategoryDerivedState(state)

  assert.deepEqual(state.aiSuggestedTagIds, [])
  assert.equal(state.tagKeyword, '')
  assert.equal(state.showAllTags, false)
  assert.deepEqual(state.formTagIds, [1, 7])
})

test('tag sections prioritize selected recommended and category-related tags for programming development', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [1],
    suggestedTagIds: [2],
    categoryLabel: '科技 / 编程开发',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(sections.selectedTags.map((item) => item.name), ['编程开发'])
  assert.deepEqual(sections.recommendedTags.map((item) => item.name), ['Java'])
  assert.deepEqual(sections.categoryTags.map((item) => item.name), [
    'SpringBoot',
    'Redis',
    '教程',
    '实战',
    'Vue',
    '系统设计',
    'Python',
    'TypeScript',
    'Node.js',
    'PostgreSQL',
    'MongoDB',
    'Kubernetes',
    'Maven',
    'Gradle',
    'RabbitMQ',
    'DevOps',
    'CI/CD',
    '原理',
    '源码',
    '调优',
    '避坑'
  ])
  assert.ok(sections.moreTagGroups.length > 0)
  assert.ok(sections.moreTagGroups.every((group) => group.tags.every((tag) => !['编程开发', 'Java', 'SpringBoot', 'Redis', '教程', '实战'].includes(tag.name))))
})

test('tag sections use ai mapping for new taxonomy names', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '科技 / 人工智能',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(sections.categoryTags.map((item) => item.name), ['教程', '实战', '人工智能', '机器学习', '算法', '干货', '复盘', 'Python', '原理'])
})

test('tag sections use backend-seeded tags for film taxonomy', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '影视 / 电影',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(sections.categoryTags.map((item) => item.name), ['剪辑', '解说', '剧情', '影视解说', '影评', '吐槽', '配音', '混剪', '名场面', '高能', '催泪'])
})

test('tag sections use vlog travel mapping for new taxonomy names', () => {
  const sections = buildUploadTagSections({
    tags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: 'Vlog / 出行',
    keyword: '',
    showAllTags: false
  })

  assert.deepEqual(sections.categoryTags.map((item) => item.name), ['Vlog', '探店', '剪辑', '摄影', '旅行'])
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

test('tag sections collapse remaining tags and report more-tag metadata', () => {
  const sections = buildUploadTagSections({
    tags: collapsedRemainingTags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '',
    keyword: '',
    showAllTags: false,
    defaultVisibleCount: 3
  })

  assert.equal(sections.hasMoreTags, true)
  assert.equal(sections.remainingTagCount, 5)
  assert.deepEqual(flattenGroupTagNames(sections.moreTagGroups), ['Vlog', 'Vue', '系统设计'])
})

test('tag sections show all remaining tags with complete grouping when expanded', () => {
  const sections = buildUploadTagSections({
    tags: collapsedRemainingTags,
    selectedTagIds: [],
    suggestedTagIds: [],
    categoryLabel: '',
    keyword: '',
    showAllTags: true,
    defaultVisibleCount: 3
  })

  assert.equal(sections.hasMoreTags, true)
  assert.equal(sections.remainingTagCount, 5)
  assert.deepEqual(
    sections.moreTagGroups.map((group) => ({
      key: group.key,
      tagNames: group.tags.map((tag) => tag.name)
    })),
    [
      { key: 'format', tagNames: ['Vlog'] },
      { key: 'tech', tagNames: ['Vue'] },
      { key: 'direction', tagNames: ['系统设计'] },
      { key: 'style', tagNames: ['剧情'] },
      { key: 'other', tagNames: ['未分组标签A'] }
    ]
  )
})
