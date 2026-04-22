export function applyImageFallbackOnce(event, fallbackSrc) {
  const target = event?.target
  if (!target || !fallbackSrc) {
    return
  }
  if (target.dataset.fallbackApplied === '1') {
    return
  }
  target.dataset.fallbackApplied = '1'
  target.onerror = null
  target.src = fallbackSrc
}