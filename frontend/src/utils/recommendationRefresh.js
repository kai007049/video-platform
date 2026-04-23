export function appendSeenIds(existingIds, videos) {
  const result = Array.isArray(existingIds) ? [...existingIds] : []
  const seen = new Set(result)

  for (const video of Array.isArray(videos) ? videos : []) {
    const id = video?.id
    if (typeof id === 'number' && !seen.has(id)) {
      seen.add(id)
      result.push(id)
    }
  }

  return result
}

export function replaceRecommendationBatch(_currentVideos, nextVideos) {
  return Array.isArray(nextVideos) ? [...nextVideos] : []
}