export function mergeSearchPage(existingItems, incomingItems, page, totalPages) {
  const safeExisting = Array.isArray(existingItems) ? existingItems : []
  const safeIncoming = Array.isArray(incomingItems) ? incomingItems : []
  const merged = page <= 1 ? [...safeIncoming] : [...safeExisting, ...safeIncoming]
  const seenIds = new Set()
  const deduplicated = merged.filter((item) => {
    const id = item?.id
    if (id == null) return true
    if (seenIds.has(id)) return false
    seenIds.add(id)
    return true
  })

  return {
    items: deduplicated,
    hasMore: page < totalPages,
    nextPage: page <= 1 ? 2 : page + 1
  }
}
