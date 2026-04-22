import { categoryTagMap, defaultTagNames, uploadTagGroups } from './uploadTagConfig.js'

function normalizeArray(value) {
  return Array.isArray(value) ? value : []
}

function findParentAndChild(categories, categoryId) {
  for (const parent of normalizeArray(categories)) {
    if (parent.id === categoryId) {
      return { parent, child: null }
    }

    const child = normalizeArray(parent.children).find((item) => item.id === categoryId)
    if (child) {
      return { parent, child }
    }
  }

  return { parent: null, child: null }
}

function resolveCategoryTagNames(categoryLabel) {
  if (!categoryLabel) {
    return []
  }

  const directMatch = categoryTagMap[categoryLabel]
  if (directMatch) {
    return directMatch
  }

  const [parentLabel] = categoryLabel.split(' / ')
  return categoryTagMap[parentLabel] || []
}

function buildMoreTagGroups(remainingTags) {
  const groupedTagNames = new Set(uploadTagGroups.flatMap((group) => group.tagNames))

  const groups = uploadTagGroups
    .map((group) => ({
      key: group.key,
      label: group.label,
      tags: remainingTags.filter((tag) => group.tagNames.includes(tag.name))
    }))
    .filter((group) => group.tags.length > 0)

  const ungroupedTags = remainingTags.filter((tag) => !groupedTagNames.has(tag.name))
  if (ungroupedTags.length > 0) {
    groups.push({
      key: 'other',
      label: '其他标签',
      tags: ungroupedTags
    })
  }

  return groups
}

export function resolveCategorySelectionState({ categories, selectedParentCategoryId, categoryId }) {
  const categoryList = normalizeArray(categories)
  const { parent, child } = findParentAndChild(categoryList, categoryId)
  const fallbackParent = categoryList.find((item) => item.id === selectedParentCategoryId) || null
  const activeParentCategory = parent || fallbackParent
  const activeChildCategories = normalizeArray(activeParentCategory?.children)

  let selectedCategoryLabel = ''
  if (parent && child) {
    selectedCategoryLabel = `${parent.name} / ${child.name}`
  } else if (parent) {
    selectedCategoryLabel = parent.name
  }

  const categoryHintText = activeParentCategory && activeChildCategories.length > 0 && activeParentCategory.id === categoryId
    ? '可继续细分到二级分类，也可直接使用一级分类'
    : ''

  return {
    selectedParentCategoryId: activeParentCategory?.id ?? null,
    activeParentCategory,
    activeChildCategories,
    selectedCategoryLabel,
    categoryHintText
  }
}

export function buildUploadTagSections({
  tags,
  selectedTagIds,
  suggestedTagIds,
  categoryLabel,
  keyword,
  showAllTags,
  defaultVisibleCount = 18
}) {
  const tagList = normalizeArray(tags)
  const selectedIdSet = new Set(normalizeArray(selectedTagIds))
  const suggestedIdSet = new Set(normalizeArray(suggestedTagIds))
  const normalizedKeyword = String(keyword || '').trim().toLowerCase()
  const categoryTagNameSet = new Set(resolveCategoryTagNames(categoryLabel))

  const selectedTags = tagList.filter((tag) => selectedIdSet.has(tag.id))
  const recommendedTags = tagList.filter((tag) => suggestedIdSet.has(tag.id) && !selectedIdSet.has(tag.id))
  const categoryTags = categoryLabel
    ? tagList.filter((tag) => categoryTagNameSet.has(tag.name) && !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id))
    : []

  let remainingTags = tagList.filter((tag) => {
    return !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id) && !categoryTagNameSet.has(tag.name)
  })

  if (normalizedKeyword) {
    remainingTags = remainingTags.filter((tag) => tag.name.toLowerCase().includes(normalizedKeyword))
  }

  const visibleRemainingTags = showAllTags ? remainingTags : remainingTags.slice(0, defaultVisibleCount)
  const moreTagGroups = buildMoreTagGroups(visibleRemainingTags)
  const defaultTagNameSet = new Set(defaultTagNames)
  const defaultTags = tagList.filter((tag) => {
    return defaultTagNameSet.has(tag.name) && !selectedIdSet.has(tag.id) && !suggestedIdSet.has(tag.id) && !categoryTagNameSet.has(tag.name)
  })

  return {
    selectedTags,
    recommendedTags,
    categoryTags,
    defaultTags,
    moreTagGroups,
    hasMoreTags: remainingTags.length > defaultVisibleCount,
    remainingTagCount: remainingTags.length
  }
}
