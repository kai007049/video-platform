export function mergeSearchPage(existingItems, incomingItems, page, totalPages) {
  const safeExisting = Array.isArray(existingItems) ? existingItems : []
  const safeIncoming = Array.isArray(incomingItems) ? incomingItems : []
  const merged = page <= 1 ? [...safeIncoming] : [...safeExisting, ...safeIncoming]
  return {
    items: merged,
    hasMore: page < totalPages,
    nextPage: page <= 1 ? 2 : page + 1
  }
}